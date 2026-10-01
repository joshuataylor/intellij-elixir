package org.elixir_lang.psi

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.call.Call

/** A definition's name is the atom its head quotes to, wherever the name is read. */
class DefinitionNameTest : PlatformTestCase() {
    fun testEscapedUnquotedAtom() = assertNamed("def unquote(:\"a\\x62\")(), do: 1", "ab")

    fun testUnquotedOperatorAtom() = assertNamed("def unquote(:in)(x, _y), do: x", "in")

    fun testUnquotedEscapedQuote() = assertNamed("def unquote(:\"a\\\"b\")(x), do: x", "a\"b")

    fun testUnquotedAtomWithASpace() = assertNamed("def unquote(:\"foo bar\")(x), do: x", "foo bar")

    fun testDecomposedIdentifier() = assertNamed("def café(), do: 1", "café")

    private fun assertNamed(definition: String, expected: String) {
        val file = myFixture.configureByText("named.ex", "defmodule M do\n  $definition\nend\n")
        val clause = PsiTreeUtil.findChildrenOfType(file, Call::class.java).single { CallDefinitionClause.`is`(it) }

        assertEquals(
            "getName, nameArityInterval",
            listOf(expected, expected),
            listOf(
                (clause as PsiNamedElement).name,
                CallDefinitionClause.nameArityInterval(clause, ResolveState.initial())?.name,
            )
        )
    }
}
