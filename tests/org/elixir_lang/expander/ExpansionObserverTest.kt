package org.elixir_lang.expander

/** What an [ExpansionObserver] is told, and in what order, as the expander reaches each node. */
class ExpansionObserverTest : ExpanderTestCase() {
    fun testEachNodeIsEnteredBeforeItsChildrenAndALiteralArgumentIsNot() {
        val code = "{a, [b | c]} = {1, [2]}"

        assertEntered(code, LEVELS) {
            listOf("{a, [b | c]} = {1, [2]}", "{1, [2]}", "[2]", "{a, [b | c]}", "a", "[b | c]", "b", "c")
                .joinToString(" | ")
        }
    }

    fun testABitstringSizeIsEnteredInGuardContextFrom1_14() {
        val code = "n = 8; <<x::size(n)>> = <<1>>"

        assertEquals(
            LEVELS.joinToString("\n") { version ->
                "$version: n MATCH, n " + if (isBefore(version, "1.14.0-rc.0")) "NONE" else "GUARD"
            },
            LEVELS.joinToString("\n") { version ->
                val entered = mutableListOf<String>()

                expand(code, version) { node, _, env ->
                    if (isVariable(node) && node.meta.origin.substring(code) == "n") entered.add("n ${env.context}")
                }

                "$version: " + entered.joinToString()
            }
        )
    }

    private fun assertEntered(code: String, versions: List<String>, expected: (String) -> String) =
        assertEquals(
            versions.joinToString("\n") { "$it: ${expected(it)}" },
            versions.joinToString("\n") { version ->
                val entered = mutableListOf<String>()

                expand(code, version) { node, _, _ -> entered.add(node.meta.origin.substring(code)) }

                "$version: " + entered.joinToString(" | ")
            }
        )
}
