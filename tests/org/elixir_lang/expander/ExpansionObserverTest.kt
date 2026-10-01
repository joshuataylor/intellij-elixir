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

    fun testEachNodeIsLeftWithItsExpansionAfterItsChildren() {
        val code = "{a, b} = {1, 2}"

        assertEquals(
            LEVELS.joinToString("\n") { "$it: {1, 2} {} | a {a} | b {a b} | {a, b} {a b} | {a, b} = {1, 2} {a b}" },
            LEVELS.joinToString("\n") { version ->
                val left = mutableListOf<String>()
                val observer = object : ExpansionObserver {
                    override fun entering(node: org.elixir_lang.lowering.ElixirAst, state: ExState, env: Env) {}

                    override fun left(node: org.elixir_lang.lowering.ElixirAst, expansion: Expansion) {
                        val read = (expansion as Expansion.Expanded).state.read.keys.map { it.name }.sorted()

                        left.add(node.meta.origin.substring(code) + read.joinToString(" ", " {", "}"))
                    }
                }

                expand(code, version, observer)

                "$version: " + left.joinToString(" | ")
            }
        )
    }

    fun testAClauseBodysStatementIsLeftWithTheStateAfterIt() {
        val code = "x = 1\ncase x do\n1 -> y = 2\nend"

        assertEquals(
            LEVELS.joinToString("\n") { "$it: x y" },
            LEVELS.joinToString("\n") { version ->
                var read = ""
                val observer = object : ExpansionObserver {
                    override fun entering(node: org.elixir_lang.lowering.ElixirAst, state: ExState, env: Env) {}

                    override fun left(node: org.elixir_lang.lowering.ElixirAst, expansion: Expansion) {
                        if (node.meta.origin.substring(code) == "y = 2") {
                            read = (expansion as Expansion.Expanded).state.read.keys.map { it.name }.sorted().joinToString(" ")
                        }
                    }
                }

                expand(code, version, observer)

                "$version: $read"
            }
        )
    }

    fun testAClauseHeadIsEnteredInMatchContextAndItsGuardInGuardContext() {
        val code = "x = true\ncase x do\ny when y -> y\nend"

        assertEquals(
            LEVELS.joinToString("\n") { "$it: y MATCH, y GUARD, y NONE" },
            LEVELS.joinToString("\n") { version ->
                val entered = mutableListOf<String>()

                expand(code, version) { node, _, env ->
                    if (isVariable(node) && node.meta.origin.substring(code) == "y") entered.add("y ${env.context}")
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
