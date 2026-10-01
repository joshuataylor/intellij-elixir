package org.elixir_lang.psi.scope.call_definition_clause

import com.intellij.psi.ResolveState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.EEx
import org.elixir_lang.declaration.Form
import org.elixir_lang.psi.CallDefinitionClause
import org.elixir_lang.psi.Exception
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall
import org.elixir_lang.psi.mix.Generator
import org.elixir_lang.psi.scope.WalkProbe
import org.elixir_lang.structure_view.element.Callback
import org.elixir_lang.structure_view.element.Delegation

/** Which [Form] a call declares with: `syntacticForm(call) ?: resolvingForm(call, state)`. */
object DeclaringForm {
    /** The forms the call's own syntax decides. */
    @RequiresReadLock
    fun syntacticForm(call: Call): Form? {
        WalkProbe.count(WalkProbe.Counter.CLASSIFY)

        return syntacticForm(SyntacticCall.of(call))
    }

    @RequiresReadLock
    fun syntacticForm(call: SyntacticCall): Form? {
        ThreadingAssertions.assertReadAccess()

        return when {
            CallDefinitionClause.`is`(call) -> Form.CLAUSE
            Callback.`is`(call) -> Form.CALLBACK
            Delegation.`is`(call) -> Form.DELEGATION
            Exception.`is`(call) -> Form.EXCEPTION
            else -> null
        }
    }

    /** The forms whose definer is known only by resolving the call, which needs the walk's [state]. */
    @RequiresReadLock
    fun resolvingForm(call: Call, state: ResolveState): Form? {
        ThreadingAssertions.assertReadAccess()
        WalkProbe.count(WalkProbe.Counter.CLASSIFY)

        return when {
            EEx.isFunctionFrom(call, state) -> Form.EEX_FUNCTION_FROM
            Generator.isEmbed(call, state) -> Form.GENERATOR_EMBED
            else -> null
        }
    }

    /** [syntacticForm], or the shape of a [resolvingForm] without resolving whose call it is. */
    @RequiresReadLock
    fun shapedForm(call: Call): Form? =
        syntacticForm(call) ?: when {
            EEx.isFunctionFromShaped(call) -> Form.EEX_FUNCTION_FROM
            Generator.isEmbedShaped(call) -> Form.GENERATOR_EMBED
            else -> null
        }
}
