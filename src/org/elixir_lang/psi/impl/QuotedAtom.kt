package org.elixir_lang.psi.impl

import com.ericsson.otp.erlang.OtpErlangAtom
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.ElixirAtom
import org.elixir_lang.psi.ElixirAtomKeyword
import org.elixir_lang.psi.Quotable
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.name.Function.UNQUOTE
import org.elixir_lang.structure_view.element.CallDefinitionHead

/**
 * The value of the atom [quotable] quotes to; `null` when it quotes to something else, as an interpolated atom does,
 * or to an atom longer than the 255 characters an atom can hold.
 */
@RequiresReadLock
fun quotedAtomValue(quotable: Quotable): String? {
    ThreadingAssertions.assertReadAccess()

    return try {
        quotable.quote() as? OtpErlangAtom
    } catch (_: IllegalArgumentException) {
        null
    }?.atomValue()
}

/** The atom value of the name [call] uses. */
@RequiresReadLock
fun functionNameAtomValue(call: Call): String? {
    ThreadingAssertions.assertReadAccess()

    return (call.functionNameElement() as? Quotable)?.let(::quotedAtomValue)
}

/** The atom value a definition [head] names: its name, or for an `unquote(atom)` head, that atom. */
@RequiresReadLock
fun headAtomValue(head: PsiElement): String? {
    ThreadingAssertions.assertReadAccess()

    return (CallDefinitionHead.strip(head) as? Call)?.let { stripped ->
        if (stripped.functionName() == UNQUOTE) {
            // Quoting a malformed operand can throw `NotImplementedError`.
            stripped.primaryArguments()?.singleOrNull()?.stripAccessExpression()
                ?.takeIf { it is ElixirAtom || it is ElixirAtomKeyword }
        } else {
            stripped.functionNameElement()
        }
    }
        ?.let { it as? Quotable }
        ?.let(::quotedAtomValue)
}
