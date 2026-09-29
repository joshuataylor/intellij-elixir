package org.elixir_lang.lowering

/** The options to `Code.string_to_quoted` that change what Elixir puts in metadata. */
class ParserOptions(val columns: Boolean = false, val tokenMetadata: Boolean = false) {
    companion object {
        /** What `Code.string_to_quoted/1` uses, and what `Quotable.quote()` reproduces. */
        @JvmField
        val DEFAULT = ParserOptions()
    }
}
