package org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.function

import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.*
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Clause.toPatternSequence
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Function
import org.elixir_lang.beam.decompiler.Options

private const val TAG = "clause"

class Clause(val attributes: Attributes, val function: Function, val term: OtpErlangTuple) : Node(term) {
    val head by lazy { headMacroStringDeclaredScope.macroString.string }

    override fun toMacroString(options: Options): String {
        val (headMacroString, headDeclaredScope) = headMacroStringDeclaredScope
        val prefix = "${function.macroNameArity.macro} ${headMacroString.string}"

        return if (options.decompileBodies) {
            try {
                val indentedBody = org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Clause.bodyString(term, headDeclaredScope)

                if (indentedBody.contains("\n")) {
                    "$prefix do\n" +
                            "  $indentedBody\n" +
                            "end"
                } else {
                    "$prefix, do: ${indentedBody.trimIndent()}"
                }
            } catch (_: StackOverflowError) {
                "$prefix, do: ..."
            }
        } else {
            "$prefix, do: ..."
        }
    }

    private val headMacroStringDeclaredScope by lazy {
        val (patternSequenceMacroString, patternSequenceDeclaredScope) = patternSequenceMacroStringDeclaredScope()

        MacroStringDeclaredScope("${patternSequenceMacroString.string}${guardSequenceString()}", doBlock = false, patternSequenceDeclaredScope)
    }

    private fun guardSequenceString(): String =
            org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Clause.guardSequenceString(term)

    val parameters: List<String> by lazy {
        patternStringsDeclaredScope
                ?.let { (patternStrings, _) ->
                    function.decompiler.signatureParameters(function.macroNameArity, patternStrings).toList()
                }
                .orEmpty()
    }

    private val patternStringsDeclaredScope: Pair<Array<String>, Scope>? by lazy {
        (toPatternSequence(term) as? OtpErlangList)
                ?.let { Sequence.toMacroStringListDeclaredScope(it, Scope.EMPTY.copy(pinning = true)) }
                ?.let { (macroStringList, declaredScope) ->
                    macroStringList.map(MacroString::string).toTypedArray() to declaredScope
                }
    }

    private fun patternSequenceMacroStringDeclaredScope(): MacroStringDeclaredScope =
            when (toPatternSequence(term)) {
                null -> Sequence.unknown()
                else -> patternStringsDeclaredScope?.let { (patternStrings, declaredScope) ->
                    val signature = StringBuilder()
                    function.decompiler.appendSignature(
                            signature,
                            function.macroNameArity,
                            function.macroNameArity.name,
                            patternStrings
                    )

                    MacroStringDeclaredScope(signature.toString(), doBlock = false, declaredScope)
                } ?: Sequence.unknown(Scope.EMPTY.copy(pinning = true))
            }

    companion object {
        fun from(term: OtpErlangObject, attributes: Attributes, function: Function): Clause? =
                ifTag(term, TAG) { Clause(attributes, function, it) }
    }
}
