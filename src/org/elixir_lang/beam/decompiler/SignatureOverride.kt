package org.elixir_lang.beam.decompiler

import org.elixir_lang.NameArity
import java.lang.StringBuilder

/**
 * For macro/name/arity that don't need be handled by [Unquoted], but won't map to Code correctly if the signature
 * in the `Docs` chunk is used.
 */
object SignatureOverride : Default() {
    /**
     * Whether the decompiler accepts the `macroNameArity`
     *
     * @return `true`
     */
    override fun accept(beamLanguage: String, nameArity: NameArity): Boolean = nameArity.name == "__struct__"

    override fun parameters(macroNameArity: org.elixir_lang.beam.MacroNameArity): Array<String> =
        signatureParameters(macroNameArity, super.parameters(macroNameArity))

    override fun signatureParameters(
        macroNameArity: org.elixir_lang.beam.MacroNameArity,
        parameters: Array<String>
    ): Array<String> =
        when (macroNameArity.arity) {
            1 -> arrayOf("kv")
            else -> parameters
        }

    override fun appendSignature(decompiled: StringBuilder,
                                 macroNameArity: org.elixir_lang.beam.MacroNameArity,
                                 name: String,
                                 parameters: Array<String>) {
        super.appendSignature(decompiled, macroNameArity, name, signatureParameters(macroNameArity, parameters))
    }
}
