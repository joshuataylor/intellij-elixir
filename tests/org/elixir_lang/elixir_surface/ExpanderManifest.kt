package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.beam.BeamReader
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.AbstractCodeCompileOptions
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Clause
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Function
import java.io.File

/**
 * The clause heads of Elixir's own expander, read from the abstract code in an SDK's `ebin`, one line per distinct
 * [ClauseKey] with its count. Functions are matched by name, not arity: `elixir_expand:expand/2` became
 * `expand/3` in 1.13.
 */
object ExpanderManifest {
    /** [argument] is 1-based. */
    data class Row(val module: String, val function: String, val argument: Int)

    val SPEC: List<Row> = listOf(
        Row("elixir_expand", "expand", 1),
        Row("elixir_expand", "expand_list", 1),
        Row("elixir_expand", "assert_generator_start", 2),
        Row("elixir_expand", "validate_for_options", 1),
        Row("elixir_clauses", "clause", 4),
        Row("elixir_clauses", "guard", 1),
        Row("elixir_clauses", "unpack_match", 1),
        Row("elixir_clauses", "expand_case", 2),
        Row("elixir_clauses", "expand_cond", 2),
        Row("elixir_clauses", "expand_receive", 2),
        Row("elixir_clauses", "expand_try", 2),
        Row("elixir_clauses", "expand_catch", 2),
        // Two arities: the smaller matches the rescue AST in argument 1, the larger `[Arg]` in argument 2.
        Row("elixir_clauses", "expand_rescue", 1),
        Row("elixir_clauses", "expand_rescue", 2),
        Row("elixir_clauses", "expand_with", 1),
        Row("elixir_fn", "capture", 2),
        Row("elixir_fn", "escape", 1),
    )

    fun expanderClauses(ebin: File): String {
        val functionsByModule = SPEC.map { it.module }.distinct().associateWith { functions(ebin, it) }

        return SPEC
            .flatMap { row -> lines(row, functionsByModule.getValue(row.module).filter { it.name?.atomValue() == row.function }) }
            .sorted()
            .joinToString("") { "$it\n" }
    }

    fun specialForms(ebin: File): String {
        val clauses = functions(ebin, "elixir_import")
            .filter { it.name?.atomValue() == "special_form" && it.arity == 2 }
            .flatMap { it.clauses }
            .also { check(it.isNotEmpty()) { "elixir_import has no special_form/2" } }

        return specialFormLines(clauses.map { it.term }).sorted().joinToString("") { "$it\n" }
    }

    /**
     * One `name/arity result` line per `special_form/2` clause, `_` for a variable. A guarded clause is keyed as the
     * `{Name, Arity}` pair with its guard instead.
     */
    internal fun specialFormLines(clauses: List<OtpErlangTuple>): List<String> =
        clauses.map { clause ->
            val patterns = patterns(clause)
            val guards = clause.elementAt(3) as OtpErlangList
            val result = (clause.elementAt(4) as OtpErlangList).elements().joinToString(", ") { ClauseKey.of(it, OtpErlangList()) }
            val head = if (guards.arity() == 0) {
                val (name, arity) = patterns.map { ClauseKey.of(it, guards) }
                "${name.removeSurrounding("'")}/$arity"
            } else {
                ClauseKey.of(OtpErlangTuple(arrayOf(OtpErlangAtom("tuple"), clause.elementAt(1), OtpErlangList(patterns.toTypedArray()))), guards)
            }

            "$head $result"
        }

    private fun lines(row: Row, functions: List<Function>): List<String> {
        val prefix = "${row.module} ${row.function} ${row.argument}"
        if (functions.isEmpty()) return listOf("$prefix (absent)")

        return functions
            .flatMap { it.clauses }
            .map { clause ->
                val patterns = patterns(clause.term)
                check(row.argument <= patterns.size) { "$prefix: ${row.function}/${patterns.size} has no argument ${row.argument}" }

                ClauseKey.of(patterns[row.argument - 1], clause.term.elementAt(3) as OtpErlangList)
            }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) -> "$prefix $key $count" }
    }

    private fun patterns(clause: OtpErlangTuple): List<OtpErlangObject> =
        (Clause.toPatternSequence(clause) as OtpErlangList).elements().toList()

    private fun functions(ebin: File, module: String): List<Function> {
        val beam = File(ebin, "$module.beam")
        val debugInfo = BeamReader.read(beam.readBytes(), beam.path) { it.debugInfo as? AbstractCodeCompileOptions }
            ?: throw AssertionError("$beam has no Erlang abstract code")

        return debugInfo.functions.functions
    }
}
