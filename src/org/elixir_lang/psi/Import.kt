package org.elixir_lang.psi

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangString
import com.ericsson.otp.erlang.OtpErlangTuple
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.util.isAncestor
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.Arity
import org.elixir_lang.Name
import org.elixir_lang.NameArity
import org.elixir_lang.beam.psi.CallDefinition as BeamCallDefinition
import org.elixir_lang.beam.psi.Module as BeamModule
import org.elixir_lang.declaration.Capabilities
import org.elixir_lang.declaration.Reach
import org.elixir_lang.language_level.ElixirLanguageFeature.DIGITS_IN_SIGIL_NAMES
import org.elixir_lang.language_level.ElixirLanguageFeature.IMPORT_ONLY_SIGILS
import org.elixir_lang.language_level.ElixirLanguageFeature.IMPORT_ONLY_SIGILS_READS_SIGIL_NAMES
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.language_level.ElixirLanguageLevelResolver
import org.elixir_lang.model.psi.FunctionArityKeywordPair
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.name.Function.IMPORT
import org.elixir_lang.psi.call.name.Module.KERNEL
import org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE
import org.elixir_lang.psi.impl.call.finalArguments
import org.elixir_lang.psi.impl.call.keywordArguments
import org.elixir_lang.psi.impl.hasKeywordKey
import org.elixir_lang.psi.impl.maybeModularNameToModulars
import org.elixir_lang.psi.impl.stripAccessExpression
import org.elixir_lang.psi.scope.Recording
import org.elixir_lang.psi.scope.reachedThrough
import org.elixir_lang.structure_view.element.CallDefinitionHead
import org.elixir_lang.structure_view.element.Delegation

/**
 * An `import` call
 */
object Import {
    /**
     * The options of the `import` a definition was reached through. The walk admits a definition when any arity it
     * covers is admitted; a use of one arity checks that arity against this.
     */
    val FILTER = Key<Filter>("Import.FILTER")

    /** What a module exports, or what an `import` of it brings in: its functions and its macros. */
    data class Imports(val functions: Set<NameArity>, val macros: Set<NameArity>) {
        fun of(macro: Boolean): Set<NameArity> = if (macro) macros else functions
    }

    /**
     * What an `import`'s options bring in, as the compiler of a language level reads them: the first `only:` and the
     * first `except:`, an `only:` list or its `:functions`, `:macros` or `:sigils`, and no name starting with `_`
     * unless an `only:` list names it.
     */
    sealed class Filter {
        abstract fun admits(name: Name, arity: Arity, macro: Boolean): Boolean

        protected abstract fun namedArities(name: Name): Collection<Arity>

        open fun admits(name: Name, arityInterval: ArityInterval, macro: Boolean): Boolean {
            // Every arity above those the options name and the sigil arity is admitted alike, so the first stands for all.
            val maximum = arityInterval.maximum
                ?: ((namedArities(name) + arityInterval.minimum + SIGIL_ARITY).max() + 1)

            return (arityInterval.minimum..maximum).any { admits(name, it, macro) }
        }

        fun imports(exports: Imports): Imports =
            Imports(
                exports.functions.filterTo(mutableSetOf()) { admits(it.name, it.arity, false) },
                exports.macros.filterTo(mutableSetOf()) { admits(it.name, it.arity, true) },
            )

        /** An unsupported option, an `only:` list with `except:`, or a value `only:` or `except:` does not take. */
        class Invalid(val error: Error) : Filter() {
            enum class Error(val kind: String) {
                UNSUPPORTED_OPTION("unsupported_option"),
                ONLY_AND_EXCEPT_GIVEN("only_and_except_given"),
                INVALID_ONLY("invalid_option only"),
                INVALID_EXCEPT("invalid_option except"),
            }

            override fun admits(name: Name, arity: Arity, macro: Boolean): Boolean = false

            override fun namedArities(name: Name): Collection<Arity> = emptyList()
        }

        /** An `only:` list: exactly what it names, `_` names included. */
        private class Only(private val nameArities: Set<NameArity>) : Filter() {
            override fun admits(name: Name, arity: Arity, macro: Boolean): Boolean =
                NameArity(name, arity) in nameArities

            override fun namedArities(name: Name): Collection<Arity> =
                nameArities.filter { it.name == name }.map { it.arity }
        }

        /**
         * No options, `except:` alone, or a selector with or without `except:`. With `except:`, a kind [prior] brings
         * in keeps what it brought in less `except:`, and the selector's own rule is not applied to it.
         */
        private class Selecting(
            private val selector: Selector?,
            private val except: Set<NameArity>?,
            private val prior: Imports?,
            private val languageLevel: ElixirLanguageLevel,
        ) : Filter() {
            override fun admits(name: Name, arity: Arity, macro: Boolean): Boolean {
                if (selector?.selects(macro) == false) return false

                val nameArity = NameArity(name, arity)
                val priorOfKind = prior?.of(macro)?.takeIf { except != null && it.isNotEmpty() }
                val selected = priorOfKind?.let { nameArity in it }
                    ?: (!name.startsWith("_") && (selector != Selector.SIGILS || isSigil(name, arity)))

                return selected && nameArity !in except.orEmpty()
            }

            override fun namedArities(name: Name): Collection<Arity> =
                (except.orEmpty() + prior?.functions.orEmpty() + prior?.macros.orEmpty())
                    .filter { it.name == name }
                    .map { it.arity }

            private fun isSigil(name: Name, arity: Arity): Boolean {
                if (!name.startsWith(SIGIL_PREFIX)) return false

                val letters = name.removePrefix(SIGIL_PREFIX)

                return if (IMPORT_ONLY_SIGILS_READS_SIGIL_NAMES.isSufficient(languageLevel)) {
                    val digits = DIGITS_IN_SIGIL_NAMES.isSufficient(languageLevel)

                    arity == SIGIL_ARITY &&
                        ((letters.length == 1 && letters[0] in 'a'..'z') ||
                            (letters.isNotEmpty() && letters[0] in 'A'..'Z' &&
                                letters.drop(1).all { it in 'A'..'Z' || (digits && it in '0'..'9') }))
                } else {
                    letters.length == 1 && (letters[0] in 'a'..'z' || letters[0] in 'A'..'Z')
                }
            }
        }

        private enum class Selector {
            FUNCTIONS,
            MACROS,
            SIGILS;

            fun selects(macro: Boolean): Boolean =
                when (this) {
                    FUNCTIONS -> !macro
                    MACROS -> macro
                    SIGILS -> true
                }
        }

        companion object {
            private const val SIGIL_ARITY = 2
            private const val SIGIL_PREFIX = "sigil_"

            /**
             * The filter [importCall]'s options make at [languageLevel]. [prior] is what an earlier `import` of the
             * same module in scope brings in, which `except:` subtracts from, or `null` when there is none. A call
             * as a value, such as `unquote(x)`, `@attr` or a macro, is not followed.
             */
            @RequiresReadLock
            fun of(importCall: Call, languageLevel: ElixirLanguageLevel, prior: Imports?): Filter {
                ThreadingAssertions.assertReadAccess()

                val options = importCall
                    .takeIf { it.finalArguments()?.size == 2 }
                    ?.keywordArguments()
                    ?.quotableKeywordPairList()
                    .orEmpty()

                if (options.any { pair -> OPTIONS.none { pair.hasKeywordKey(it) } }) {
                    return Invalid(Invalid.Error.UNSUPPORTED_OPTION)
                }

                val only = options.firstOrNull { it.hasKeywordKey("only") }?.keywordValue
                val except = options.firstOrNull { it.hasKeywordKey("except") }?.keywordValue
                val exceptNameArities = except?.let { value ->
                    val quoted = lazy(LazyThreadSafetyMode.NONE) { value.quote() }

                    nameArities(value, quoted)
                        ?: if (isCall(quoted.value)) null else return Invalid(Invalid.Error.INVALID_EXCEPT)
                }

                if (only == null) return Selecting(null, exceptNameArities, prior, languageLevel)

                val quotedOnly = lazy(LazyThreadSafetyMode.NONE) { only.quote() }

                nameArities(only, quotedOnly)?.let { onlyNameArities ->
                    return if (except == null) Only(onlyNameArities) else Invalid(Invalid.Error.ONLY_AND_EXCEPT_GIVEN)
                }

                if (isCall(quotedOnly.value)) return Only(emptySet())

                val selector = when ((quotedOnly.value as? OtpErlangAtom)?.atomValue()) {
                    "functions" -> Selector.FUNCTIONS
                    "macros" -> Selector.MACROS
                    "sigils" -> Selector.SIGILS.takeIf { IMPORT_ONLY_SIGILS.isSufficient(languageLevel) }
                    else -> null
                } ?: return Invalid(Invalid.Error.INVALID_ONLY)

                return Selecting(selector, exceptNameArities, prior, languageLevel)
            }

            private val OPTIONS = listOf("only", "except", "warn")

            /** A variable quotes to the same `{name, meta, context}` shape, with an atom where a call has arguments. */
            private fun isCall(quoted: OtpErlangObject): Boolean =
                (quoted as? OtpErlangTuple)
                    ?.takeIf { it.arity() == 3 }
                    ?.elementAt(2)
                    .let { it != null && it !is OtpErlangAtom }

            /** The `name: arity` pairs of a literal list, skipping what is not one, or `null` when [value] is no list. */
            private fun nameArities(value: Quotable, quoted: Lazy<OtpErlangObject>): Set<NameArity>? =
                (value.stripAccessExpression() as? ElixirList)?.let { list ->
                    (list.children.lastOrNull() as? QuotableKeywordList)
                        ?.quotableKeywordPairList()
                        ?.mapNotNull { pair ->
                            FunctionArityKeywordPair.nameFromKey(pair.keywordKey)?.let { name ->
                                FunctionArityKeywordPair.arityFromValue(pair.keywordValue)?.let { NameArity(name, it) }
                            }
                        }
                        ?.toSet()
                        .orEmpty()
                } ?: emptySet<NameArity>().takeIf { isEmptyList(quoted.value) }

            /** `''` is the empty list. */
            private fun isEmptyList(quoted: OtpErlangObject): Boolean =
                (quoted is OtpErlangList && quoted.arity() == 0) || (quoted is OtpErlangString && quoted.stringValue().isEmpty())
        }
    }

    /**
     * Whether `call` is an `import Module` or `import Module, opts` call
     */
    @JvmStatic
    fun `is`(call: Call): Boolean = call.isCalling(KERNEL, IMPORT) && call.resolvedFinalArity() in 1..2

    @JvmStatic
    @RequiresReadLock
    fun treeWalkUp(
        importCall: Call,
        resolveState: ResolveState,
        keepProcessing: (PsiElement, ResolveState) -> Boolean
    ): Boolean {
        ThreadingAssertions.assertReadAccess()

        var accumulatedKeepProcessing = true

        if (walks(importCall, resolveState)) {
            val modulars = modulars(importCall)

            if (modulars.isNotEmpty()) {
                val importCallResolveState = Recording
                    .enter(
                        resolveState, "IMPORT", importCall, stops = false, absorbs = true,
                        gate = { walks(importCall, it) }, reach = ::reached
                    )
                    .putVisitedElement(importCall)
                    .let(::reached)
                // An earlier `import` of the same module is not looked for, so `except:` subtracts from everything the
                // module exports even where that `import` brought in less.
                val filter =
                    Filter.of(importCall, ElixirLanguageLevelResolver.languageLevelFor(importCall), prior = null)

                val filtered = { state: ResolveState -> state.put(FILTER, filter) }

                for (modular in modulars) {
                    ProgressManager.checkCanceled()
                    // One imported module stops at a `false` and answers `true` (`takeWhile { it }.lastOrNull() ?: true`).
                    val childResolveState = Recording
                        .enter(
                            importCallResolveState.putVisitedElement(modular), "IMPORTED", modular, stops = true,
                            absorbs = true, childGate = if (modular is Call) ::walksChild else null, reach = filtered
                        )
                        .let(filtered)

                    accumulatedKeepProcessing =
                        treeWalkUpImportedModular(modular, filter, childResolveState, keepProcessing)

                    if (!accumulatedKeepProcessing) {
                        break
                    }
                }
            }
        }

        return accumulatedKeepProcessing
    }

    /** Don't descend back into `import` when the entrance is the alias to the `import` like `MyAlias` in `import MyAlias`. */
    private fun walks(importCall: Call, resolveState: ResolveState): Boolean =
        !importCall.isAncestor(resolveState.get(ENTRANCE))

    private fun reached(resolveState: ResolveState): ResolveState = resolveState.reachedThrough(Reach.IMPORT)

    private fun walksChild(resolveState: ResolveState, child: PsiElement): Boolean = !resolveState.hasBeenVisited(child)

    private fun treeWalkUpImportedModular(
        importedModular: PsiElement,
        filter: Filter,
        resolveState: ResolveState,
        keepProcessing: (PsiElement, ResolveState) -> Boolean
    ): Boolean =
        when (importedModular) {
            is Call -> treeWalkUpImportedModular(importedModular, filter, resolveState, keepProcessing)
            is BeamModule -> treeWalkUpImportedModular(importedModular, filter, resolveState, keepProcessing)
            else -> true
        }

    private fun treeWalkUpImportedModular(
        importedModular: Call,
        filter: Filter,
        resolveState: ResolveState,
        keepProcessing: (PsiElement, ResolveState) -> Boolean
    ): Boolean =
        CallDefinitionClause.modularChildCalls(importedModular)
            .asSequence()
            .filter { walksChild(resolveState, it) }
            .map {
                ProgressManager.checkCanceled()
                treeWalkUpImportedModularChildExpression(filter, it, resolveState, keepProcessing)
            }
            .takeWhile { it }
            .lastOrNull()
            ?: true

    private fun treeWalkUpImportedModular(
        importedModular: BeamModule,
        filter: Filter,
        resolveState: ResolveState,
        keepProcessing: (PsiElement, ResolveState) -> Boolean
    ): Boolean =
        importedModular
            .callDefinitions()
            .map {
                ProgressManager.checkCanceled()
                treeWalkUpImportedModularChildExpression(filter, it, resolveState, keepProcessing)
            }
            .takeWhile { it }
            .lastOrNull()
            ?: true

    private fun treeWalkUpImportedModularChildExpression(
        filter: Filter,
        importedCall: Call,
        resolveState: ResolveState,
        keepProcessing: (Call, ResolveState) -> Boolean
    ): Boolean =
        when {
            CallDefinitionClause.`is`(importedCall) -> {
                CallDefinitionClause.nameArityInterval(importedCall, resolveState)?.let { nameArityInterval ->
                    val capabilities = importedCapabilities(importedCall)

                    if (capabilities != null &&
                        filter.admits(nameArityInterval.name, nameArityInterval.arityInterval, capabilities.compileTime)
                    ) {
                        keepProcessing(importedCall, resolveState)
                    } else {
                        true
                    }
                }
            }
            Delegation.`is`(importedCall) -> {
                importedCall.finalArguments()?.takeIf { it.size == 2 }?.let { arguments ->
                    val head = arguments[0]

                    CallDefinitionHead.nameArityInterval(head, resolveState)?.let { headNameArityInterval ->
                        if (filter.admits(headNameArityInterval.name, headNameArityInterval.arityInterval, false)) {
                            keepProcessing(importedCall, resolveState)
                        } else {
                            true
                        }
                    }
                }
            }
            else -> null
        }
            ?: true

    private fun treeWalkUpImportedModularChildExpression(
        filter: Filter,
        importedCall: BeamCallDefinition,
        resolveState: ResolveState,
        keepProcessing: (PsiElement, ResolveState) -> Boolean
    ): Boolean {
        val nameArityInterval = importedCall.nameArityInterval
        val capabilities = importedCapabilities(importedCall)

        return if (capabilities != null &&
            filter.admits(nameArityInterval.name, nameArityInterval.arityInterval, capabilities.compileTime)
        ) {
            keepProcessing(importedCall, resolveState)
        } else {
            true
        }
    }

    /** [clause]'s capabilities when an `import` of its module brings it in, else `null`. */
    @RequiresReadLock
    internal fun importedCapabilities(clause: Call): Capabilities? =
        CallDefinitionClause.capabilities(clause)?.takeIf { it.public }

    /** [definition]'s capabilities when an `import` of its module brings it in, else `null`. */
    @RequiresReadLock
    internal fun importedCapabilities(definition: BeamCallDefinition): Capabilities? =
        definition.capabilities.takeIf { it.public }

    fun elementDescription(call: Call, location: ElementDescriptionLocation): String? =
        when {
            location === UsageViewTypeLocation.INSTANCE -> "import"
            location === UsageViewNodeTextLocation.INSTANCE -> call.text
            else -> null
        }

    /**
     * The modular that is imported by `importCall`.
     * @param importCall a [Call] where [is] is `true`.
     * @return `defmodule`, `defimpl`, or `defprotocol` imported by `importCall`.  It can be
     * `null` if Alias passed to `importCall` cannot be resolved.
     */
    private fun modulars(importCall: Call): Set<PsiNamedElement> =
        importCall
            .finalArguments()
            ?.firstOrNull()
            ?.maybeModularNameToModulars(maxScope = importCall.parent, useCall = null, incompleteCode = false)
            ?: emptySet()
}
