package org.elixir_lang.lowering

/** Offsets into a file's text as [Meta.Position]s. */
internal class Lines(private val text: CharSequence) {
    private val starts: IntArray = run {
        val starts = mutableListOf(0)

        for (offset in text.indices) {
            if (text[offset] == '\n') starts.add(offset + 1)
        }

        starts.toIntArray()
    }

    fun position(offset: Int): Meta.Position {
        val line = starts.binarySearch(offset).let { if (it >= 0) it else -it - 2 }

        return Meta.Position(line + 1, Character.codePointCount(text, starts[line], offset) + 1)
    }
}
