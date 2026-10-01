package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangMap
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.intellij_elixir.Quoter
import org.elixir_lang.lowering.expressionNodes
import org.elixir_lang.lowering.inspect
import org.elixir_lang.psi.ElixirFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Compiles case module bodies through [Quoter.compile], one batch per call, with a probe macro after each statement
 * that sends its `__CALLER__` back. Each probe is a macro expansion, so it advances its module's counter.
 *
 * The harness's modules carry a token unique to the compile: test forks share one quoter node, and two compiles
 * defining the same module at once would make Elixir raise. So a case body may define only modules nested in its case
 * module.
 *
 * @param parse parses source text into a file, whose top-level expressions are the statements
 */
class ProbeHarness(private val parse: (String) -> ElixirFile) {
    /** `{case, block, statement}`: statement 0 is the start of the block, statement `n` follows its `n`th statement. */
    data class Tag(val case: Int, val block: Int, val statement: Int)

    /** What the probe at [tag] saw: `__CALLER__` as a map. */
    data class Observation(val tag: Tag, val env: OtpErlangMap)

    /** [probeModule] and [caseModule] are atom text, as [Env] holds modules. */
    class Batch(private val token: String, val observations: List<Observation>) {
        val probeModule = "Elixir." + probeModule(token)

        fun caseModule(case: Int) = "Elixir." + caseModule(token, case)
    }

    /** Compiles each of [bodies] as the body of a module of its own. */
    fun compile(bodies: List<String>): Batch {
        val token = "ProbeCase" + UUID.randomUUID().toString().replace("-", "")
        val probeModule = probeModule(token)
        val tags = mutableListOf<Tag>()
        val source = buildString {
            append(
                """
                defmodule $probeModule do
                  defmacro p(tag) do
                    IntellijElixir.Quoter.Probe.send(__CALLER__, {List.to_tuple(tag), Map.from_struct(__CALLER__)})
                    nil
                  end
                end

                """.trimIndent()
            )

            bodies.forEachIndexed { case, body ->
                append("\ndefmodule ${caseModule(token, case)} do\n")
                append("require $probeModule\n")
                append(probe(probeModule, Tag(case, 0, 0).also(tags::add)))
                append("\n")
                append(probed(probeModule, case, body, tags))
                append("\nend\n")
            }
        }
        val compiled = Quoter.compile(source, COMPILE_TIMEOUT)

        assertTrue(
            "compile failed: ${inspect(compiled.status)} ${compiled.diagnostics.map(::inspect)}\n$source",
            compiled.status == OtpErlangAtom("ok")
        )

        val observations = compiled.messages.map(::observation)

        assertEquals("probes that reported", tags, observations.map { it.tag })

        return Batch(token, observations)
    }

    /** [body] with a probe after each statement, on the statement's own line; each probe's tag is added to [tags]. */
    private fun probed(probeModule: String, case: Int, body: String, tags: MutableList<Tag>): String {
        val ends = statementEnds(parse(body))
        val probedBody = StringBuilder(body)

        ends.indices.forEach { tags.add(Tag(case, 0, it + 1)) }
        ends.withIndex().reversed().forEach { (index, end) ->
            probedBody.insert(end, "; " + probe(probeModule, Tag(case, 0, index + 1)))
        }

        return probedBody.toString()
    }

    /**
     * Where each statement ends, as the lowering splits a file into statements: a parenthesised block is one, though
     * it quotes as several do.
     */
    private fun statementEnds(file: ElixirFile): List<Int> =
        ReadAction.computeBlocking<List<Int>, Throwable> {
            check(!PsiTreeUtil.hasErrorElements(file)) { "a case body must parse without errors" }

            expressionNodes(file).map { it.textRange.endOffset }
        }

    private fun probe(probeModule: String, tag: Tag): String =
        "$probeModule.p([${tag.case}, ${tag.block}, ${tag.statement}])"

    private fun observation(message: OtpErlangObject): Observation {
        val (tag, env) = (message as OtpErlangTuple).elements()
        val (case, block, statement) = (tag as OtpErlangTuple).elements().map { (it as OtpErlangLong).intValue() }

        return Observation(Tag(case, block, statement), env as OtpErlangMap)
    }

    private companion object {
        val COMPILE_TIMEOUT = 30.seconds

        fun probeModule(token: String) = "$token.Probe"

        fun caseModule(token: String, case: Int) = "$token.Case$case"
    }
}
