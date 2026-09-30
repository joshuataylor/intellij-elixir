package org.elixir_lang.psi.scope

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.isAncestor
import org.elixir_lang.psi.AtUnqualifiedNoParenthesesCall
import org.elixir_lang.psi.CallDefinitionClause.blockChildCalls
import org.elixir_lang.psi.ElixirMatchedUnqualifiedNoArgumentsCall
import org.elixir_lang.psi.ElixirUnmatchedAtUnqualifiedNoParenthesesCall
import org.elixir_lang.psi.ModuleAttribute.isNonReferencing
import org.elixir_lang.psi.Use
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE
import org.elixir_lang.psi.scope.WhileIn.whileIn

abstract class ModuleAttribute : PsiScopeProcessor {
    override fun execute(element: PsiElement, state: ResolveState): Boolean =
            when (element) {
                is ElixirMatchedUnqualifiedNoArgumentsCall -> true
                is ElixirUnmatchedAtUnqualifiedNoParenthesesCall -> execute(element, state) && executeBlock(element, state)
                is Call -> executeUse(element, state) && executeBlock(element, state)
                else -> true
            }

    private fun execute(maybeDeclaration: AtUnqualifiedNoParenthesesCall<*>, state: ResolveState): Boolean =
        isNonReferencing(maybeDeclaration.atIdentifier) || executeOnDeclaration(maybeDeclaration, state)

    private fun executeUse(call: Call, state: ResolveState): Boolean =
            if (Use.`is`(call)) {
                Use.treeWalkUp(call, state, ::execute)
            } else {
                true
            }

    /**
     * What a block inside [call] declares at module level counts where [call] is, nearest first. A block holding the
     * read is walked from the read instead, as a later statement in it has not run yet.
     */
    private fun executeBlock(call: Call, state: ResolveState): Boolean =
        state.get(ENTRANCE)?.let { call.isAncestor(it, strict = true) } == true ||
                whileIn(blockChildCalls(call).asReversed()) { statement ->
                    ProgressManager.checkCanceled()

                    when (statement) {
                        is AtUnqualifiedNoParenthesesCall<*> -> execute(statement, state)
                        else -> executeUse(statement, state)
                    }
                }

    /**
     * Decides whether `declaration` matches the criteria being searched for.
     *
     * @return `true` to keep processing; `false` to stop processing.
     */
    protected abstract fun executeOnDeclaration(declaration: AtUnqualifiedNoParenthesesCall<*>,
                                                state: ResolveState): Boolean
}
