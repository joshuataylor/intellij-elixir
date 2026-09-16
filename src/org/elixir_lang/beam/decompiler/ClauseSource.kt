package org.elixir_lang.beam.decompiler

import org.elixir_lang.beam.chunk.DebugInfo
import org.elixir_lang.beam.chunk.beam_documentation.Documentation
import org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.V1
import org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.v1.definitions.Definition
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.AbstractCodeCompileOptions
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Function
import org.elixir_lang.psi.call.name.Function.DEF
import org.elixir_lang.psi.call.name.Function.DEFP

/**
 * Where a definition's decompiled clauses come from.
 */
sealed interface ClauseSource {
    class ErlangAbstractCode(val function: Function) : ClauseSource

    class ElixirDebugInfo(val definition: Definition) : ClauseSource

    class DocsSignatures(val signatures: List<String>) : ClauseSource

    class Generated(val decompiler: MacroNameArity) : ClauseSource
}

/**
 * [documentation] is only called when the debug info cannot render the definition.
 */
fun clauseSource(
    macroNameArity: org.elixir_lang.beam.MacroNameArity,
    debugInfo: DebugInfo?,
    documentation: () -> Documentation?,
    options: Options
): ClauseSource? =
    debugInfoClauseSource(macroNameArity, debugInfo, options)
        ?: documentationClauseSource(macroNameArity, documentation())

private fun debugInfoClauseSource(
    macroNameArity: org.elixir_lang.beam.MacroNameArity,
    debugInfo: DebugInfo?,
    options: Options
): ClauseSource? =
    when (debugInfo) {
        is AbstractCodeCompileOptions ->
            when (macroNameArity.macro) {
                DEF, DEFP ->
                    debugInfo.functions.byNameArity[macroNameArity.toNameArity()]
                        ?.let { ClauseSource.ErlangAbstractCode(it) }
                else -> null
            }
        is V1 ->
            debugInfo.definitions?.get(macroNameArity)
                ?.takeIf { it.renderedClauses(options) != null }
                ?.let { ClauseSource.ElixirDebugInfo(it) }
        else -> null
    }

private fun documentationClauseSource(
    macroNameArity: org.elixir_lang.beam.MacroNameArity,
    documentation: Documentation?
): ClauseSource? {
    val beamLanguage = documentation?.beamLanguage ?: "elixir"
    val decompiler = decompiler(beamLanguage, macroNameArity.toNameArity()) ?: return null

    // Docs signatures read well but are not code `unquote` can use, so only the default decompiler takes them.
    val signatures = if (decompiler === Default.INSTANCE) {
        documentation?.takeIf { it.beamLanguage == "elixir" }?.docs?.signatures(macroNameArity)
    } else {
        null
    }

    return if (!signatures.isNullOrEmpty()) {
        ClauseSource.DocsSignatures(signatures)
    } else {
        ClauseSource.Generated(decompiler)
    }
}
