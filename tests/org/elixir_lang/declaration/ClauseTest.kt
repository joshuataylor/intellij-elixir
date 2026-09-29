package org.elixir_lang.declaration

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.CallDefinitionClause
import org.elixir_lang.psi.call.Call

/** Which calls are definition clauses, which also decides which calls get stubs. */
class ClauseTest : PlatformTestCase() {
    fun testClauses() {
        myFixture.configureByText(
            "clauses.ex",
            """
            defmodule M do
              def bodyless(x)
              def one_line(x), do: x
              defmemo memoized(x), do: x
              defdelegate delegated(x), to: N
              plain(1)
            end
            """.trimIndent() + "\n"
        )

        assertEquals(
            mapOf(
                "bodyless" to true,
                "one_line" to true,
                "memoized" to true,
                "delegated" to false,
                "plain" to false
            ),
            listOf("bodyless", "one_line", "memoized", "delegated", "plain").associateWith {
                CallDefinitionClause.`is`(statement(it))
            }
        )
    }

    /** The whole line naming [name]: the outermost call inside `defmodule`. */
    private fun statement(name: String): Call =
        generateSequence(myFixture.file.findElementAt(myFixture.file.text.indexOf("$name("))) { it.parent }
            .filterIsInstance<Call>()
            .last { it.textRange.startOffset > 0 }
}
