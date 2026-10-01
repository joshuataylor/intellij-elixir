package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangObject

/**
 * The harness sees, after each statement of a case module body, the change Elixir makes to its env, and nothing else.
 * Each step is compared with the case's own step 0, so this checks the harness against Elixir alone.
 */
class ProbeHarnessSelfTest : ProbeTestCase() {
    fun testEachStatementChangesOnlyWhatElixirChanges() {
        val bodies = listOf("alias Foo.Bar", "x = 1; x", "import List, only: [first: 1]")
        val batch = harness.compile(bodies)
        val byCase = batch.observations.groupBy { it.tag.case }

        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1),
            batch.observations.map { it.tag.case to it.tag.statement }
        )

        val steps = byCase.mapValues { (_, observations) ->
            observations.map { ProbedEnvNormaliser.observed(it.env, batch.probeModule) }
        }

        val alias = steps.getValue(0)
        assertSteps(alias, alias[0], alias[0] + ("aliases" to list(tuple(atom("Elixir.Bar"), atom("Elixir.Foo.Bar")))))

        val variable = steps.getValue(1)
        assertSteps(variable, variable[0], variable[0], variable[0])
        assertEquals(
            listOf("", "x/nil=0", "x/nil=0"),
            VariableClasses.canonical(byCase.getValue(1).map { VariableClasses.observed(it.env) })
        )

        val import = steps.getValue(2)
        val functions = (import[0].getValue("functions") as OtpErlangList).elements()
        val requires = (import[0].getValue("requires") as OtpErlangList).elements() + atom("Elixir.List")
        val listFirst = tuple(atom("Elixir.List"), list(tuple(atom("first"), OtpErlangLong(1))))
        assertSteps(
            import,
            import[0],
            import[0] +
                ("functions" to list(listFirst, *functions)) +
                ("requires" to list(*requires.sortedBy { (it as OtpErlangAtom).atomValue() }.toTypedArray()))
        )
    }

    fun testAParenthesisedBlockIsOneStatement() {
        val batch = harness.compile(listOf("(alias Foo.Bar; :ok)"))

        assertEquals(listOf(0 to 0, 0 to 1), batch.observations.map { it.tag.case to it.tag.statement })

        val steps = batch.observations.map { ProbedEnvNormaliser.observed(it.env, batch.probeModule) }
        assertSteps(steps, steps[0], steps[0] + ("aliases" to list(tuple(atom("Elixir.Bar"), atom("Elixir.Foo.Bar")))))
    }

    fun testEmptyParenthesesAreOneStatement() {
        val batch = harness.compile(listOf("()"))

        assertEquals(listOf(0 to 0, 0 to 1), batch.observations.map { it.tag.case to it.tag.statement })
    }

    private fun assertSteps(actual: List<Map<String, OtpErlangObject>>, vararg expected: Map<String, OtpErlangObject>) =
        assertEquals(
            expected.joinToString("\n---\n") { ProbedEnvNormaliser.render(it) },
            actual.joinToString("\n---\n") { ProbedEnvNormaliser.render(it) }
        )
}
