package org.elixir_lang.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.NameArityInterval
import org.elixir_lang.declaration.Capabilities
import org.elixir_lang.declaration.Declaration
import org.elixir_lang.declaration.Declared
import org.elixir_lang.declaration.Definer
import org.elixir_lang.declaration.Form
import org.elixir_lang.declaration.Presentation
import org.elixir_lang.declaration.SourceOrigin
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall
import org.elixir_lang.psi.call.name.Function.*
import org.elixir_lang.psi.call.name.Module.KERNEL
import org.elixir_lang.psi.impl.enclosingMacroCall
import org.elixir_lang.structure_view.element.CallDefinitionHead

object CallDefinitionClause {
    /**
     * The enclosing macro call that acts as the modular scope of `call`.  Ignores enclosing `for` calls that
     * [enclosingMacroCall] doesn't.
     *
     * @param call a def(macro)?p?
     */
    @RequiresReadLock
    @JvmStatic
    fun enclosingModularMacroCall(call: Call): Call? =
        enclosingModularMacroCall(SyntacticCall.of(call))?.let(SyntacticCall::call)

    @RequiresReadLock
    @JvmStatic
    fun enclosingModularMacroCall(call: SyntacticCall): SyntacticCall? {
        var enclosedCall = call
        var enclosingMacroCall: SyntacticCall?

        while (true) {
            ProgressManager.checkCanceled()
            enclosingMacroCall = enclosedCall.enclosingMacroCall()

            if (enclosingMacroCall != null &&
                    (enclosingMacroCall.isCalling(KERNEL, ALIAS) ||
                            enclosingMacroCall.isCalling(KERNEL, REQUIRE) ||
                            For.`is`(enclosingMacroCall))) {
                enclosedCall = enclosingMacroCall
            } else {
                break
            }
        }

        return enclosingMacroCall
    }

    /**
     * Description of element used in find-usages and element-description presentation.
     *
     * @param call a [Call] that has already been checked with [.is]
     * @param location where the description will be used
     * @return
     */
    @RequiresReadLock
    fun elementDescription(call: Call, location: ElementDescriptionLocation): String? =
            when (capabilities(call)?.presentation) {
                Presentation.FUNCTION -> functionElementDescription(call, location)
                Presentation.MACRO -> macroElementDescription(location)
                Presentation.GUARD, null -> null
            }

    /**
     * The head of the call definition.
     *
     * @param call a call that [.is].
     * @return element for `name(arg, ...) when ...` in `def* name(arg, ...) when ...`
     */
    @RequiresReadLock
    @JvmStatic
    fun head(call: Call): PsiElement? = call.primaryArguments()?.firstOrNull()

    @RequiresReadLock
    @JvmStatic
    fun `is`(call: Call): Boolean = `is`(SyntacticCall.of(call))

    @RequiresReadLock
    @JvmStatic
    fun `is`(call: SyntacticCall): Boolean = definer(call) != null

    /** The `def*` [call] is written with, `null` when it is no clause. */
    @RequiresReadLock
    @JvmStatic
    fun definer(call: Call): Definer? = definer(SyntacticCall.of(call))

    @RequiresReadLock
    @JvmStatic
    fun definer(call: SyntacticCall): Definer? =
        call.functionName()?.let { Definer.of(it) }?.takeIf { isCallingKernelMacroOrHead(call, it.keyword) }

    @RequiresReadLock
    @JvmStatic
    fun capabilities(call: Call): Capabilities? = capabilities(SyntacticCall.of(call))

    @RequiresReadLock
    @JvmStatic
    fun capabilities(call: SyntacticCall): Capabilities? = definer(call)?.capabilities

    @RequiresReadLock
    fun declaration(call: Call, state: ResolveState): Declaration? {
        val capabilities = capabilities(call) ?: return null
        val (name, arityInterval) = nameArityInterval(call, state) ?: return null
        val file = call.containingFile
        val viewProvider =
            (file.originalFile as? PsiCompiledFile)?.takeIf { it.decompiledPsiFile == file }?.viewProvider
                ?: file.viewProvider

        return Declaration(
            name,
            arityInterval.arityKnowledge(),
            capabilities,
            Declared.Source(
                Form.CLAUSE,
                SourceOrigin(viewProvider.virtualFile, viewProvider.modificationStamp, call.textRange)
            )
        )
    }

    /**
     * Returns `true` if [element] is at or within the name/head of any call-definition clause -
     * i.e. inside the `foo(args)` head in `def foo(args)`. Used to suppress reference providers
     * that must not fire on declaration names.
     *
     * Mirrors [org.elixir_lang.psi.Protocol.isHead] but applies to all `CallDefinitionClause`
     * contexts, not just `defprotocol` bodies.
     */
    @RequiresReadLock
    fun isHead(element: PsiElement): Boolean {
        val defClause = generateSequence(element) { it.parent }
            .filterIsInstance<Call>()
            .firstOrNull { `is`(it) }
            ?: return false
        val nameIdentifier = nameIdentifier(defClause) ?: return false
        return PsiTreeUtil.isAncestor(nameIdentifier, element, false) ||
               PsiTreeUtil.isAncestor(element, nameIdentifier, false)
    }

    /**
     * The name and arity range of the call definition this clause belongs to.
     *
     * @param call
     * @return The name and arities of the [org.elixir_lang.structure_view.element.CallDefinition] this clause belongs.  Multiple arities occur when
     * default arguments are used, which produces an arity for each default argument that is turned on and off.
     * @see Call.resolvedFinalArityInterval
     */
    @RequiresReadLock
    @JvmStatic
    fun nameArityInterval(call: Call, state: ResolveState): NameArityInterval? =
            head(call)?.let { CallDefinitionHead.nameArityInterval(it, state) }

    /** A [putNameArityInterval] `write` policy that keeps the first clause seen for a repeated `(name, arity)`. */
    val firstWins: (byArity: MutableMap<Int, Call>, arity: Int, call: Call) -> Unit =
            { byArity, arity, call -> byArity.putIfAbsent(arity, call) }

    /**
     * Adds [call] to [byArityByName] under its name, once per arity in its arity interval, via [write] - so a
     * caller building a name/arity lookup across many clauses picks once whether a repeated (name, arity) keeps
     * the first clause seen or the last, instead of every call site reimplementing this walk.
     */
    @RequiresReadLock
    fun putNameArityInterval(
            call: Call,
            state: ResolveState,
            byArityByName: MutableMap<String, MutableMap<Int, Call>>,
            write: (byArity: MutableMap<Int, Call>, arity: Int, call: Call) -> Unit
    ) {
        nameArityInterval(call, state)?.let { nameArityInterval ->
            val byArity = byArityByName.getOrPut(nameArityInterval.name) { mutableMapOf() }
            nameArityInterval.arityInterval.closed().forEach { arity ->
                ProgressManager.checkCanceled()
                write(byArity, arity, call)
            }
        }
    }

    @RequiresReadLock
    fun nameIdentifier(call: Call): PsiElement? = head(call)?.let { CallDefinitionHead.nameIdentifier(it) }

    private fun functionElementDescription(
            @Suppress("UNUSED_PARAMETER") call: Call,
            location: ElementDescriptionLocation
    ): String? =
            if (location === UsageViewTypeLocation.INSTANCE) {
                "function"
            } else {
                null
            }

    private fun macroElementDescription(location: ElementDescriptionLocation): String? =
            if (location === UsageViewTypeLocation.INSTANCE) {
                "macro"
            } else {
                null
            }

    private fun isCallingKernelMacroOrHead(call: SyntacticCall, resolvedName: String): Boolean =
            call.isCallingMacro(KERNEL, resolvedName, 2) ||
                    call.isCalling(KERNEL, resolvedName, 1)
}
