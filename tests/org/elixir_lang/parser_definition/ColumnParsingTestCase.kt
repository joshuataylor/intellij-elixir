package org.elixir_lang.parser_definition

import com.intellij.openapi.application.ReadAction
import org.elixir_lang.language_level.ElixirLanguageLevelResolver
import org.elixir_lang.lowering.Lowering
import org.elixir_lang.lowering.hasUnlowered
import org.elixir_lang.psi.ElixirFile

/**
 * Tabs, non-ASCII letters and astral characters, whose columns Elixir counts per code point outside quoted text and
 * per grapheme cluster inside it from 1.13, lowered whole so the snippet differential compares them on every leg.
 */
class ColumnParsingTestCase : ParsingTestCase() {
    fun testTab() = assertLoweredAndQuotedCorrectly()
    fun testNonAscii() = assertLoweredAndQuotedCorrectly()
    fun testAstral() = assertLoweredAndQuotedCorrectly()

    private fun assertLoweredAndQuotedCorrectly() {
        assertParsedAndQuotedCorrectly(false)

        val file = myFile as ElixirFile
        val lowered = ReadAction.computeBlocking<_, Throwable> {
            Lowering.lower(file, ElixirLanguageLevelResolver.languageLevelFor(file))
        }

        assertFalse("the snippet differential skips a snippet with anything unlowered", lowered.hasUnlowered())
    }

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/column"
}
