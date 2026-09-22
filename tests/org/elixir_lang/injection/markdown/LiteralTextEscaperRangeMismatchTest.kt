package org.elixir_lang.injection.markdown

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.HeredocLiteral
import org.intellij.plugins.markdown.lang.MarkdownLanguage

/**
 * `LiteralTextEscaper`'s contract does not promise `getOffsetInHost` is only ever called for the same
 * range `decode` was last called with - `PlaceInfo` constructs one escaper per place
 * (`host.createLiteralTextEscaper()`), but nothing stops the platform from calling `getOffsetInHost` for
 * a *different* range on that same instance without an intervening `decode` for it, and `decode`'s own
 * javadoc contract for `getOffsetInHost` is keyed on the range argument, not on escaper-instance state.
 */
class LiteralTextEscaperRangeMismatchTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/injection/markdown"

    fun testGetOffsetInHostIsCorrectForARangeOtherThanTheLastDecoded() {
        myFixture.configureByFile("long_documentation.ex")
        val heredoc = PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first()

        var shreds: List<PsiLanguageInjectionHost.Shred>? = null
        InjectedLanguageManager.getInstance(myFixture.project)
            .enumerateEx(heredoc, myFixture.file, true) { injectedPsi, placeShreds ->
                if (injectedPsi.language == MarkdownLanguage.INSTANCE) shreds = placeShreds
            }
        val ranges = shreds!!.map { it.rangeInsideHost }
        assertTrue("Fixture needs at least two Markdown places to exercise a range switch", ranges.size >= 2)

        val escaper = heredoc.createLiteralTextEscaper()
        val (firstRange, secondRange) = ranges[0] to ranges[1]

        // decode() for the first range only - as if the platform had already moved on to the second
        // place's range without ever asking this same escaper instance to decode() it first
        assertTrue(escaper.decode(firstRange, StringBuilder()))

        val expected = StringBuilder()
        assertTrue(escaper.decode(secondRange, expected))
        val expectedOffsets = (0..secondRange.length).map { escaper.getOffsetInHost(it, secondRange) }

        // Re-point the escaper back at the first range's cached state, then ask for an offset in the
        // *second* range without an intervening decode() for it
        assertTrue(escaper.decode(firstRange, StringBuilder()))
        val actualOffsets = (0..secondRange.length).map { escaper.getOffsetInHost(it, secondRange) }

        assertEquals(
            "getOffsetInHost(_, secondRange) must not answer from firstRange's cached offsets table",
            expectedOffsets,
            actualOffsets
        )
    }
}
