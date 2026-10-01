package org.elixir_lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall
import org.elixir_lang.psi.call.name.Function.FOR
import org.elixir_lang.psi.call.name.Module.KERNEL
import org.elixir_lang.psi.impl.call.whileInStabBodyChildExpressions
import org.elixir_lang.psi.scope.Recording

object For {
    /**
     * Whether `call` is a `for ... <- ... do` call.
     */
    fun `is`(call: Call): Boolean = `is`(SyntacticCall.of(call))

    fun `is`(call: SyntacticCall): Boolean = call.isCalling(KERNEL, FOR, 2)

    @RequiresReadLock
    fun treeWalkDown(call: Call, resolveState: ResolveState, function: (PsiElement, ResolveState) -> Boolean): Boolean {
        if (!walks(call, resolveState)) {
            return true
        }

        val forResolveState = Recording
            .enter(
                resolveState, "FOR", call, stops = true, absorbs = false,
                gate = { walks(call, it) }, childGate = { state, child -> walks(child, state) }
            )
            .putVisitedElement(call)

        return call.whileInStabBodyChildExpressions { expression ->
            !walks(expression, forResolveState) || function(expression, forResolveState)
        }
    }

    private fun walks(element: PsiElement, resolveState: ResolveState): Boolean = !resolveState.hasBeenVisited(element)
}
