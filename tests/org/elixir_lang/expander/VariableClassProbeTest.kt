package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpOutputStream
import com.intellij.openapi.application.ReadAction
import org.elixir_lang.lowering.ElixirAst
import org.elixir_lang.lowering.Lowering
import org.elixir_lang.lowering.expressionNodes
import org.elixir_lang.psi.ElixirFile
import java.io.File
import kotlin.time.TimeSource

/**
 * After each statement of a case module body, the expander's variables fall into the same classes as Elixir's, and
 * its env's other fields equal Elixir's, on the leg's Elixir. Only cases the expander covers are compiled: one that
 * reaches an unported clause at any statement is counted as uncovered.
 */
class VariableClassProbeTest : ProbeTestCase() {
    fun testTheVariableOraclesTopLevelCases() {
        val cases = File(ORACLE_CASES)
            .listFiles { file -> file.name.startsWith("top__") && file.name.endsWith(".exs") }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.name to it.readLines().drop(HEADER_LINES).joinToString("\n") }
        val expansions = cases.mapValues { (_, body) -> expandStatements(body) }
        val covered = expansions.filterValues { it.all { expansion -> expansion is Expansion.Expanded } }
        val uncoveredAt = expansions.filterKeys { it !in covered }.values
            .map { steps -> describe((steps.first { it is Expansion.Unported } as Expansion.Unported).at) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        println("covered ${covered.size} of ${cases.size}")
        println("uncovered, by the first unported node: " + uncoveredAt.joinToString { "${it.key} ${it.value}" })
        println("covered cases: " + covered.keys.joinToString(" "))

        assertMatchesElixir(covered.keys.associateWith { cases.getValue(it) })
        assertTrue("covered ${covered.size} of ${cases.size}, below $FLOOR", covered.size >= FLOOR)
    }

    fun testOwnCases() {
        val expansions = OWN_CASES.associateWith { expandStatements(it) }

        assertEquals(
            "",
            expansions.filterValues { steps -> steps.any { it is Expansion.Unported } }.keys.joinToString("\n")
        )

        assertMatchesElixir(OWN_CASES.associateWith { it })
    }

    /** Compiles [cases] in one batch and compares every probe of each with the expander's state and env there. */
    private fun assertMatchesElixir(cases: Map<String, String>) {
        val names = cases.keys.toList()
        val start = TimeSource.Monotonic.markNow()
        val batch = harness.compile(names.map(cases::getValue))
        val elapsed = start.elapsedNow()
        val replyBytes = batch.observations.sumOf { OtpOutputStream(it.env).size() }

        println("batch of ${names.size} cases: $elapsed, ${batch.observations.size} probes, $replyBytes bytes of env")

        val observed = batch.observations.groupBy { it.tag.case }.toSortedMap().values.map { probes ->
            probes.map { it.env }
        }
        val steps = names.map { expandedSteps(cases.getValue(it)) }

        assertEquals(
            render(names) { case -> VariableClasses.canonical(observed[case].map(VariableClasses::observed)) },
            render(names) { case -> VariableClasses.canonical(steps[case].map { it.state.read }) }
        )
        assertEquals(
            render(names) { case ->
                observed[case].map { ProbedEnvNormaliser.render(ProbedEnvNormaliser.observed(it, batch.probeModule)) }
            },
            render(names) { case ->
                steps[case].map { ProbedEnvNormaliser.render(ProbedEnvNormaliser.projected(it.env)) }
            }
        )
    }

    /** Each case's name, then one line per probe. */
    private fun render(names: List<String>, probes: (Int) -> List<String>): String =
        names.withIndex().joinToString("\n") { (case, name) -> "== $name\n" + probes(case).joinToString("\n") }

    /** The state and env before [body]'s first statement and after each, which the case must cover. */
    private fun expandedSteps(body: String): List<Expansion.Expanded> =
        listOf(Expansion.Expanded(ExState.EMPTY, Env.empty(legLevel(), legKernel))) +
            expandStatements(body).map { it as Expansion.Expanded }

    /**
     * Each statement of [body], lowered on its own and expanded from the one before it, as `expand_block` threads
     * them, up to the first that is unported.
     */
    private fun expandStatements(body: String): List<Expansion> {
        val level = legLevel()
        val file = createPsiFile(getTestName(false), body) as ElixirFile
        val statements = ReadAction.computeBlocking<List<ElixirAst>, Throwable> {
            val lowering = Lowering.of(file, level)

            expressionNodes(file).map { lowering.lower(it.psi) }
        }
        var state = ExState.EMPTY
        var env = Env.empty(level, legKernel)
        val expansions = mutableListOf<Expansion>()

        for (statement in statements) {
            val expansion = Expander.expand(statement, state, env, level)
            expansions.add(expansion)

            when (expansion) {
                is Expansion.Expanded -> {
                    state = expansion.state
                    env = expansion.env
                }
                is Expansion.Unported -> break
            }
        }

        return expansions
    }

    /** A call as its name and arity, anything else as its kind. */
    private fun describe(node: ElixirAst): String =
        when {
            node is ElixirAst.Call && node.callee is ElixirAst.Literal.Atom ->
                (node.callee as ElixirAst.Literal.Atom).name + (node.arguments?.let { "/${it.size}" } ?: " (variable)")
            node is ElixirAst.Call -> "remote or anonymous call"
            else -> node.javaClass.simpleName
        }

    private companion object {
        const val ORACLE_CASES = "testData/org/elixir_lang/model/psi/variable/oracle/cases"

        /** The comment lines `generate.exs` writes at the top of each case. */
        const val HEADER_LINES = 2

        /** The covered count measured when these clauses were ported; it may only grow. */
        const val FLOOR = 84

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
