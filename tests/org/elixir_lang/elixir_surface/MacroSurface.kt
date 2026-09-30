package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.beam.BeamBytes
import org.elixir_lang.beam.binaryToTerm
import org.elixir_lang.beam.chunk.beam_documentation.docs.Documented
import org.elixir_lang.beam.chunk.beam_documentation.docs.documented.Hidden
import java.io.File

/**
 * Every macro `Kernel` and `Kernel.SpecialForms` document in their `Docs` chunk, one `module name/arity` line each,
 * with ` hidden` for `@doc false`.
 */
object MacroSurface {
    private val MODULES = listOf("Kernel", "Kernel.SpecialForms")

    fun macros(ebin: File): String =
        MODULES
            .flatMap { module -> entries(File(ebin, "Elixir.$module.beam")).filter { it.kind == "macro" }.map { line(module, it) } }
            .sorted()
            .joinToString("") { "$it\n" }

    private fun line(module: String, macro: Documented): String =
        "$module ${macro.name}/${macro.arity}" + if (macro.doc == Hidden) " hidden" else ""

    /** `Docs` enumerates no entries, so the `docs_v1` term is walked here and each entry decoded by [Documented]. */
    private fun entries(beam: File): List<Documented> {
        val bytes = beam.readBytes()
        val span = BeamBytes.chunks(bytes).firstOrNull { it.id == "Docs" } ?: throw AssertionError("$beam has no Docs chunk")
        val (term, _) = binaryToTerm(bytes.copyOfRange(span.data, span.data + span.size), 0)

        if (term !is OtpErlangTuple || (term.elementAt(0) as? OtpErlangAtom)?.atomValue() != "docs_v1") {
            throw AssertionError("$beam's Docs chunk is not docs_v1")
        }

        return (term.elementAt(6) as OtpErlangList).map { Documented.from(it)!! }
    }
}
