package org.elixir_lang.expander

import org.elixir_lang.lowering.ElixirAst
import org.elixir_lang.psi.ElixirFile
import java.io.File

/**
 * After each statement of a case module body, and at each identity probe in its patterns, the expander's variables
 * fall into the same classes as Elixir's, and its env's other fields equal Elixir's, on the leg's Elixir. A case the
 * expander reports an error for is compared as an error. One that reaches an unported clause at any statement is
 * counted as uncovered.
 */
class VariableClassProbeTest : ProbeTestCase() {
    private val probes = ExpansionProbes(harness) { createPsiFile(getTestName(false), it) as ElixirFile }

    fun testTheVariableOraclesTopLevelCases() {
        val cases = File(ORACLE_CASES)
            .listFiles { file -> file.name.startsWith("top__") && file.name.endsWith(".exs") }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.name to it.readLines().drop(HEADER_LINES).joinToString("\n") }
        val expansions = cases.mapValues { (_, body) -> probes.expand(body) }
        val covered = expansions.filterValues { it.outcome is Expansion.Expanded }
        val uncoveredAt = expansions.filterKeys { it !in covered }.values
            .map { expansion ->
                when (val outcome = expansion.outcome) {
                    is Expansion.Unported -> describe(outcome.at)
                    is Expansion.Error -> "error ${outcome.kind}"
                    is Expansion.Expanded -> error("unreachable")
                }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        println("covered ${covered.size} of ${cases.size}")
        println("uncovered, by the first unported node: " + uncoveredAt.joinToString { "${it.key} ${it.value}" })
        println("covered cases: " + covered.keys.joinToString(" "))

        probes.assertMatchesElixir(expansions.filterValues { it.outcome !is Expansion.Unported })
        assertTrue("covered ${covered.size} of ${cases.size}, below $FLOOR", covered.size >= FLOOR)
    }

    fun testOwnCases() {
        val expansions = OWN_CASES.associateWith { probes.expand(it) }

        assertEquals(
            "",
            expansions.filterValues { it.outcome !is Expansion.Expanded }.keys.joinToString("\n")
        )

        probes.assertMatchesElixir(expansions)
    }

    /** A call as its name and arity, anything else as its kind. */
    private fun describe(node: ElixirAst): String =
        when {
            node is ElixirAst.Call && node.callee is ElixirAst.Literal.Atom ->
                node.callee.name + (node.arguments?.let { "/${it.size}" } ?: " (variable)")
            node is ElixirAst.Call -> "remote or anonymous call"
            else -> node.javaClass.simpleName
        }

    private companion object {
        const val ORACLE_CASES = "testData/org/elixir_lang/model/psi/variable/oracle/cases"

        /** The comment lines `generate.exs` writes at the top of each case. */
        const val HEADER_LINES = 2

        /** The covered count measured when these clauses were ported; it may only grow. */
        const val FLOOR = 91

        val OWN_CASES = listOf(
            "()",
            "(a = 1; b = a)\na",
            "{a, b, c} = {1, 2, 3}",
            "[a | b] = [1, 2]",
            "x = y = 1",
            "{x, x} = {1, 1}",
            "_ = 1",
            "key = :k\n%{^key => v} = %{key => 1}",
            "x = 1\n{x, y} = {x, 2}",
            "y = 1\n(x = y) = 1",
            "{a = b, c} = {1, 2}",
            "t = {x = 1, 2}",
            "l = [x = 1]",
            "t = [{x, x} = {1, 1}]",
            "t = {{x, x} = {1, 1}, 2}\ny = 1",
            "t = [{x, x, _} = {1, 1, 2}]",
        )
    }
}
