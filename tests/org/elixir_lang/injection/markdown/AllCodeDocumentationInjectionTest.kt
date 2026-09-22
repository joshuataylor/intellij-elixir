package org.elixir_lang.injection.markdown

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.HeredocLiteral
import com.intellij.psi.util.PsiTreeUtil

/**
 * A documentation heredoc whose every line is a code line - no line is ever fully kept as Markdown -
 * used to still get one Markdown place under the pre-#2813 per-line injector, carrying just the
 * indent (and `iex> ` prompt, if any) each code line owed Markdown. `markdownInjection`'s run-building
 * loop only ever appends a code line's leftover text to an *existing* run's neighbouring place
 * ([computeMarkdownInjection]'s `pending`); when no run is ever opened at all, that text has nowhere to
 * go and is dropped, along with the place that would have carried it.
 */
class AllCodeDocumentationInjectionTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/injection/markdown"

    fun testADocumentationOfOnlyCodeLinesStillGetsAMarkdownPlace() {
        myFixture.configureByFile("all_code_documentation.ex")
        val heredoc = PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first()

        val places = markdownInjection(heredoc).places

        assertFalse(
            "A documentation heredoc with no prose at all still owes Markdown the indent its one code " +
                    "line contributed, but no place was registered to carry it",
            places.isEmpty()
        )
    }

    /** The zero-length place the fix above registers must survive real injection, not just list-building. */
    fun testADocumentationOfOnlyCodeLinesHighlightsWithoutError() {
        myFixture.configureByFile("all_code_documentation.ex")
        myFixture.doHighlighting()
    }
}
