package org.elixir_lang.psi.scope.call_definition_clause

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.NameArityInterval
import org.elixir_lang.declaration.Form
import org.elixir_lang.beam.psi.CallDefinition as BeamCallDefinition
import org.elixir_lang.psi.*
import org.elixir_lang.psi.CallDefinitionClause.nameArityInterval
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.Named
import org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE
import org.elixir_lang.psi.impl.call.finalArguments
import org.elixir_lang.psi.impl.call.keywordArgument
import org.elixir_lang.psi.impl.maybeModularNameToModulars
import org.elixir_lang.psi.impl.headAtomValue
import org.elixir_lang.psi.impl.quotedAtomValue
import org.elixir_lang.psi.impl.stripAccessExpression
import org.elixir_lang.psi.scope.ReachedDeclaration
import org.elixir_lang.psi.scope.ResolveResultOrderedSet
import org.elixir_lang.psi.scope.VisitedElementSetResolveResult
import org.elixir_lang.psi.scope.WhileIn.whileIn
import org.elixir_lang.psi.scope.maxScope
import org.elixir_lang.psi.scope.reached
import org.elixir_lang.psi.scope.reachedThroughDelegation
import org.elixir_lang.structure_view.element.CallDefinitionHead
import org.elixir_lang.structure_view.element.Callback

class MultiResolve
private constructor(
        /**
         * Can be `null` when `Qualifier.unquote(variable)(...)` is used because although scope can be limited to
         * `Qualifier`, no `name` can be inferred, so all public call definition clauses in `Qualifier` should resolve,
         * but as invalid.
         */
        private val name: String?,
        /**
         * If `name` is `null`, then `resolvedPrimaryArity` must be valid or `incompleteCode` `true` or no match will be
         * found at all.
         */
        private val resolvedPrimaryArity: Int,
        private val incompleteCode: Boolean,
        /** [name]'s atom value, recorded as what each declaration found here was searched under. */
        private val nameAtom: String?) : org.elixir_lang.psi.scope.CallDefinitionClause() {
    override fun executeOnCallDefinitionClause(element: Call, state: ResolveState): Boolean =
            nameArityInterval(element, state)
                    ?.let { addIfNameOrArityToResolveResults(element, it, state, Form.CLAUSE) }
                    ?: true

    override fun execute(element: BeamCallDefinition, state: ResolveState): Boolean =
        addIfNameOrArityToResolveResults(element, element.nameArityInterval, state)

    override fun executeOnCallback(element: AtUnqualifiedNoParenthesesCall<*>, state: ResolveState): Boolean =
            Callback.headCall(element)
                    ?.let { CallDefinitionHead.nameArityInterval(it, state) }
                    ?.let { addIfNameOrArityToResolveResults(element, it, state, Form.CALLBACK) }
                    ?: true

    override fun executeOnDelegation(element: Call, state: ResolveState): Boolean {
        element.finalArguments()?.takeIf { it.size == 2 }?.let { arguments ->
            val head = arguments[0]

            CallDefinitionHead.nameArityInterval(head, state)?.let { headNameArityInterval ->
                val headName = headNameArityInterval.name
                val validArity = resolvedPrimaryArity in headNameArityInterval.arityInterval

                if ((this.name == null && (incompleteCode || validArity)) ||
                        (this.name != null && headName.startsWith(this.name))) {
                    val headValidResult = validArity && headName == this.name

                    // the defdelegate is valid or invalid regardless of whether the `to:` (and `:as` resolves as
                    // `defdelegate` still defines a function in the module with the head's name and arity even if it
                    // will fail at runtime to call the delegated function
                    addToResolveResults(element, headName, headValidResult, state, Form.DELEGATION)

                    // A target reached through a head that does not fit the call would be kept as invalid, and the
                    // first result for an element wins, so it would hide the same target reached through one that fits.
                    element.keywordArgument("to")?.takeIf { headValidResult || incompleteCode }?.let { definingModuleName ->
                        val modulars = definingModuleName.maybeModularNameToModulars(element.containingFile, useCall = null, incompleteCode = incompleteCode)

                        if (modulars.isNotEmpty()) {
                            val asAtom = element.keywordArgument("as")?.let { it as? ElixirAtom }
                            val nameInDefiningModule = asAtom?.node?.lastChildNode?.text ?: headName
                            val nameInDefiningModuleAtom = if (asAtom != null) quotedAtomValue(asAtom) else headAtomValue(head)
                            val headNamed = this.name == null || headName == this.name

                            for (modular in modulars) {
                                // Call recursively to get all the proper `for` and `use` handling.
                                val modularResolveResults = resolveResults(
                                    nameInDefiningModule,
                                    resolvedPrimaryArity,
                                    incompleteCode,
                                    modular,
                                    ResolveState.initial(),
                                    nameInDefiningModuleAtom
                                )

                                for (modularResultResult in modularResolveResults) {
                                    when (val modularResultResultElement = modularResultResult.element) {
                                        is Call -> addToResolveResults(
                                            modularResultResultElement,
                                            nameInDefiningModule,
                                            headValidResult && modularResultResult.isValidResult,
                                            state.reachedThroughDelegation(element, modularResultResult.reach),
                                            throughDelegation(modularResultResult.reached, element, headNamed, headValidResult, state, modularResultResultElement)
                                        )
                                        is BeamCallDefinition -> addToResolveResults(
                                            modularResultResultElement,
                                            nameInDefiningModule,
                                            headValidResult && modularResultResult.isValidResult,
                                            state.reachedThroughDelegation(element, modularResultResult.reach),
                                            throughDelegation(modularResultResult.reached, element, headNamed, headValidResult, state, modularResultResultElement)
                                        )
                                        // Anything else is not a definition a delegation can target.
                                        else -> Unit
                                    }
                                }

                                if (!keepProcessing()) {
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }

        return keepProcessing()
    }

    override fun executeOnEExFunctionFrom(element: Call, state: ResolveState): Boolean =
            element.finalArguments()?.let { arguments ->
                        arguments[1].stripAccessExpression().let { it as? ElixirAtom }?.node?.lastChildNode?.text?.let { name ->
                            if (this.name != null && name.startsWith(this.name)) {
                                val arity = if (arguments.size >= 4) {
                                    // function_from_file(kind, name, file, args)
                                    // function_from_file(kind, name, file, args, options)
                                    // function_from_string(kind, name, template, args)
                                    // function_from_string(kind, name, template, args, options)
                                    arguments[3].stripAccessExpression().let { it as? ElixirList }?.children?.size
                                } else {
                                    // function_from_file(kind, name, file) where args defaults to `[]`
                                    // function_from_string(kind, name, template) where args defaults to `[]`
                                    0
                                }

                                val validResult = (resolvedPrimaryArity == arity) && (name == this.name)

                                addToResolveResults(element, name, validResult, state, Form.EEX_FUNCTION_FROM)
                            } else {
                                true
                            }
                        } ?: true
            } ?: true

    override fun executeOnException(element: Call, state: ResolveState): Boolean =
            whileIn(Exception.NAME_ARITY_LIST) { nameArity ->
                val name = nameArity.name
                val validArity = resolvedPrimaryArity == nameArity.arity

                addIfNameOrArityToResolveResults(element, name, validArity, state, Form.EXCEPTION)
            }

    override fun executeOnMixGeneratorEmbed(element: Call, state: ResolveState): Boolean =
            element.finalArguments()?.first()?.stripAccessExpression()?.let { it as? ElixirAtom }?.node?.lastChildNode?.text?.let { prefix ->
                val suffix = element.functionName()!!.removePrefix("embed_")
                val name = "${prefix}_${suffix}"
                val arityRange = when (suffix) {
                    "template" -> 0..1
                    "text" -> 0..0
                    else -> TODO("Unknown suffix $suffix")
                }

                if (this.name != null && name.startsWith(this.name)) {
                    val validResult = (resolvedPrimaryArity in arityRange) && (name == this.name)

                    addToResolveResults(element, name, validResult, state, Form.GENERATOR_EMBED)
                } else {
                    true
                }
            } ?: true

    private fun addIfNameOrArityToResolveResults(call: Call,
                                                 nameArityInterval: NameArityInterval,
                                                 state: ResolveState,
                                                 form: Form): Boolean {
        val name = nameArityInterval.name
        val validArity = resolvedPrimaryArity in nameArityInterval.arityInterval

        return addIfNameOrArityToResolveResults(call, name, validArity, state, form)
    }

    private fun addIfNameOrArityToResolveResults(callDefinition: BeamCallDefinition,
                                                 nameArityInterval: NameArityInterval,
                                                 state: ResolveState): Boolean {
        val name = nameArityInterval.name
        val validArity = resolvedPrimaryArity in nameArityInterval.arityInterval

        return addIfNameOrArityToResolveResults(callDefinition, name, validArity, state)
    }

    private fun addIfNameOrArityToResolveResults(call: Call, name: String, validArity: Boolean, state: ResolveState, form: Form): Boolean =
            if ((this.name == null && (incompleteCode || validArity)) ||
                    (this.name != null && name.startsWith(this.name))) {
                val validResult = validArity && name == this.name

                addToResolveResults(call, name, validResult, state, form)
            } else {
                true
            }

    private fun addIfNameOrArityToResolveResults(callDefinition: BeamCallDefinition,
                                                 name: String,
                                                 validArity: Boolean,
                                                 state: ResolveState) : Boolean =
        if ((this.name == null && (incompleteCode || validArity)) ||
            (this.name != null && name.startsWith(this.name))) {
            val validResult = validArity && name == this.name

            addToResolveResults(callDefinition, name, validResult, state)
        } else {
            true
        }

    override fun keepProcessing(): Boolean = resolveResultOrderedSet.keepProcessing(incompleteCode)
    fun resolveResults(): List<VisitedElementSetResolveResult> = resolveResultOrderedSet.toList()

    private val resolveResultOrderedSet = ResolveResultOrderedSet()

    private fun addToResolveResults(call: Call, name: String, validResult: Boolean, state: ResolveState, form: Form): Boolean =
            addToResolveResults(call, name, validResult, state, declared(form, call, name, validResult, state))

    private fun addToResolveResults(
        call: Call,
        name: String,
        validResult: Boolean,
        state: ResolveState,
        reached: List<ReachedDeclaration>
    ): Boolean =
            (call as? Named)?.nameIdentifier?.let { nameIdentifier ->
                if (PsiTreeUtil.isAncestor(state.get(ENTRANCE), nameIdentifier, false)) {
                    resolveResultOrderedSet.add(call, name, validResult, emptySet(), state.reached(call), reached)
                } else {
                    resolveResultOrderedSet.add(call, name, validResult, state.visitedElementSet(), state.reached(call), reached)
                }

                keepProcessing()
            } ?: true

    private fun addToResolveResults(callDefinition: BeamCallDefinition,
                                    name: String,
                                    validResult: Boolean,
                                    state: ResolveState): Boolean =
        addToResolveResults(
            callDefinition,
            name,
            validResult,
            state,
            listOf(ReachedDeclaration(callDefinition.declaration(), nameAtom, true, validResult))
        )

    private fun addToResolveResults(callDefinition: BeamCallDefinition,
                                    name: String,
                                    validResult: Boolean,
                                    state: ResolveState,
                                    reached: List<ReachedDeclaration>): Boolean {
        resolveResultOrderedSet.add(
            callDefinition,
            name,
            validResult,
            state.visitedElementSet(),
            state.reached(callDefinition),
            reached
        )

        return keepProcessing()
    }

    /** The declaration [call] makes that the walk spells [name], when that name has an atom value. */
    private fun declared(form: Form, call: Call, name: String, valid: Boolean, state: ResolveState): List<ReachedDeclaration> =
        Declarations.of(form, call, state)
            .firstOrNull { it.text == name }
            ?.declaration
            ?.let { listOf(ReachedDeclaration(it, nameAtom, true, valid)) }
            .orEmpty()

    /**
     * What a delegation [target] recorded where it was found, as reached through [delegation] with [state]: each route
     * in the target's module continues through it.
     */
    private fun throughDelegation(
        reached: List<ReachedDeclaration>,
        delegation: Call,
        headNamed: Boolean,
        headValidResult: Boolean,
        state: ResolveState,
        target: PsiElement
    ): List<ReachedDeclaration> =
        reached.map {
            it.copy(
                headNamed = headNamed && it.headNamed,
                valid = headValidResult && it.valid,
                via = listOf(delegation) + it.via,
                reach = state.reachedThroughDelegation(delegation, it.reach).reached(target),
                visitedElementSet = state.visitedElementSet() + it.visitedElementSet
            )
        }

    companion object {
        @JvmOverloads
        @JvmStatic
        fun resolveResults(name: String?,
                           resolvedFinalArity: Int,
                           incompleteCode: Boolean,
                           entrance: PsiElement,
                           resolveState: ResolveState = ResolveState.initial(),
                           nameAtom: String? = null): List<VisitedElementSetResolveResult> {
            val multiResolve = MultiResolve(name, resolvedFinalArity, incompleteCode, nameAtom)
            val maxScope = maxScope(entrance)

            val entranceResolveState = resolveState
                    .put(ENTRANCE, entrance)
                    .putInitialVisitedElement(entrance)
                    .putAncestorUnquote(entrance)

            PsiTreeUtil.treeWalkUp(
                    multiResolve,
                    entrance,
                    maxScope,
                    entranceResolveState
            )

            return multiResolve.resolveResults()
        }
    }
}
