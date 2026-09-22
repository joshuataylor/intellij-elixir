package org.elixir_lang.injection.markdown

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.HeredocLiteral

/**
 * CommonMark allows a blank line between two paragraphs of the same indented code block - it's still
 * one code block, not two. A "blank" line here still carries the heredoc's own prefix as leading
 * whitespace (an editor commonly preserves a doc string's base indent even on an otherwise-empty line),
 * so after the prefix is stripped it is short - shorter than `CODE_BLOCK_INDENT` - but not truly empty.
 * Such a line must not end an open Elixir code block, the way real prose does.
 */
class BlankLineInCodeBlockInjectionTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/injection/markdown"

    fun testABlankLineWithResidualPrefixWhitespaceDoesNotEndTheCodeBlock() {
        myFixture.configureByFile("blank_line_in_code_block.ex")
        val heredoc = PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first()

        val elixirInjections = mutableListOf<Pair<PsiFile, List<PsiLanguageInjectionHost.Shred>>>()
        InjectedLanguageManager.getInstance(myFixture.project)
            .enumerateEx(heredoc, myFixture.file, true) { injectedPsi, shreds ->
                if (injectedPsi.language == ElixirLanguage) elixirInjections.add(Pair(injectedPsi, shreds))
            }

        assertEquals(
            "Two iex> examples separated only by a blank line (with residual heredoc-prefix whitespace) " +
                    "must stay one Elixir code block, not be split into two separate injected files:\n  " +
                    elixirInjections.joinToString("\n  ") { (file, shreds) -> "$file: $shreds" },
            1,
            elixirInjections.size
        )
        assertEquals(
            "The one Elixir injection must carry both iex> lines' code as two shreds",
            2,
            elixirInjections.single().second.size
        )
    }
}
