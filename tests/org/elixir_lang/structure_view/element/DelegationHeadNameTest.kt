package org.elixir_lang.structure_view.element

import com.intellij.navigation.ChooseByNameRegistry
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.navigation.GotoSymbolContributor
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.structure_view.Model

/** A delegated function is listed under the atom its head quotes to, as its clauses and the index name it. */
class DelegationHeadNameTest : PlatformTestCase() {
    fun testStructureViewListsAnUnquotedAtomHeadByItsAtom() =
        assertStructureView("defdelegate unquote(:ab)(x), to: N", "ab/1")

    fun testStructureViewListsADecomposedHeadComposed() =
        assertStructureView("defdelegate $DECOMPOSED(x), to: N", "$PRECOMPOSED/1")

    fun testGotoSymbolListsAnUnquotedAtomHeadByItsAtom() =
        assertGotoSymbol("defdelegate unquote(:ab)(x), to: N", "ab", "ab/1")

    fun testGotoSymbolListsADecomposedHeadComposed() =
        assertGotoSymbol("defdelegate $DECOMPOSED(x), to: N", PRECOMPOSED, "$PRECOMPOSED/1")

    private fun assertStructureView(delegation: String, expected: String) {
        myFixture.configureByText("delegation.ex", "defmodule M do\n  $delegation\nend\n")
        val module = Model(myFixture.file as ElixirFile, null).root.children.single()

        assertEquals(
            listOf(expected),
            module.children.filterIsInstance<CallDefinition>().map { it.presentation.presentableText },
        )
    }

    private fun assertGotoSymbol(delegation: String, name: String, expected: String) {
        myFixture.configureByText("delegation.ex", "defmodule M do\n  $delegation\nend\n")
        val contributor = ChooseByNameRegistry.getInstance().symbolModelContributors
            .filterIsInstance<GotoSymbolContributor>()
            .single()

        assertEquals(
            listOf(expected),
            contributor.getItemsByName(name, name, project, false).filterIsInstance<CallDefinition>().map { it.name },
        )
    }

    private companion object {
        const val DECOMPOSED = "cafe\u0301"
        const val PRECOMPOSED = "caf\u00e9"
    }
}
