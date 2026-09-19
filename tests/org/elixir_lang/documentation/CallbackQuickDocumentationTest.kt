package org.elixir_lang.documentation

import org.elixir_lang.ElixirFileType
import org.elixir_lang.PlatformTestCase

/**
 * Following a `c:Module.name/arity` link in rendered documentation shows the callback's `@doc`, for
 * `@macrocallback` as well as `@callback`.
 */
class CallbackQuickDocumentationTest : PlatformTestCase() {
    fun testCallbackLinkShowsAtDoc() {
        assertLinkShowsAtDoc("callback")
    }

    fun testMacroCallbackLinkShowsAtDoc() {
        assertLinkShowsAtDoc("macrocallback")
    }

    private fun assertLinkShowsAtDoc(attributeName: String) {
        myFixture.configureByText(
            ElixirFileType.INSTANCE,
            """
            defmodule Behaviour do
              @doc "Handles one message."
              @$attributeName handle(term()) :: :ok
            end
            """.trimIndent()
        )

        val provider = ElixirDocumentationProvider()
        val element = provider.getDocumentationElementForLink(psiManager, "c:Behaviour.handle/1", myFixture.file)

        assertNotNull("c:Behaviour.handle/1 should resolve to the @$attributeName", element)

        val documentation = provider.generateDoc(element!!, null)

        assertNotNull("Documentation should be shown for a documented @$attributeName", documentation)
        assertTrue(
            "Expected the @doc body in the documentation, got: $documentation",
            documentation!!.contains("Handles one message.")
        )
    }
}
