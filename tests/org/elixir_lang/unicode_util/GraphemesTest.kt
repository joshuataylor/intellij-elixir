package org.elixir_lang.unicode_util

import junit.framework.TestCase
import org.elixir_lang.language_level.ElixirLanguageLevel

class GraphemesTest : TestCase() {
    fun testALongHangulSyllableIsOneCluster() {
        val text = "ᄀ".repeat(100_000) + "ᅡ".repeat(100_000) + "ᆨ".repeat(100_000)

        assertEquals(text.length, graphemes.clusterEnd(text, 0))
    }

    fun testALongRunOfPrependsTakesTheClusterAfterIt() {
        val text = "؀".repeat(100_000) + "ab"

        assertEquals(text.length - 1, graphemes.clusterEnd(text, 0))
    }

    private val graphemes = Graphemes.of(ElixirLanguageLevel.of("1.20.4", "29.0.6"))
}
