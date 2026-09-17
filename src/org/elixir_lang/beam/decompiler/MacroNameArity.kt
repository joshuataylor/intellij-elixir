package org.elixir_lang.beam.decompiler

import org.elixir_lang.NameArity
import org.elixir_lang.beam.MacroNameArity
import java.lang.StringBuilder

/**
 * Decompiles a [org.elixir_lang.beam.MacroNameArity]
 */
abstract class MacroNameArity {
    /**
     * Whether the decompiler accepts the `macroNameArity`
     *
     * @param beamLanguage BEAM language, such as `"elixir"` or `"erlang"`.  Matches format used in
     * [org.elixir_lang.beam.chunk.beam_documentation.Documentation.beamLanguage]
     * @param nameArity Name and arity of definition to decompile.
     * @return `true` if [.append] should be called with
     * `macroNameArity`.
     */
    abstract fun accept(beamLanguage: String, nameArity: NameArity): Boolean

    /**
     * Append the decompiled source for `macroNameArity` to `decompiled`.
     *
     * @param decompiled the decompiled source so far
     */
    abstract fun append(decompiled: StringBuilder,
                        macroNameArity: MacroNameArity)

    /**
     * The names this decompiler invents for a definition whose `.beam` records none.
     */
    abstract fun parameters(macroNameArity: MacroNameArity): Array<String>

    /**
     * The parameters [appendSignature] renders when given [parameters].
     */
    open fun signatureParameters(macroNameArity: MacroNameArity, parameters: Array<String>): Array<String> =
            parameters

    abstract fun appendSignature(decompiled: StringBuilder,
                                 macroNameArity: MacroNameArity,
                                 name: String,
                                 parameters: Array<String>)

    /**
     * Append the decompiled name for `macroNameArity` to `decompiled`.
     *
     * @param decompiled the decompiled source so far
     */
    abstract fun appendName(decompiled: StringBuilder,
                            name: String)

    companion object {
        @JvmStatic
        fun appendBody(decompiled: StringBuilder) {
            appendNotDecompiledBody(decompiled)
        }
    }
}
