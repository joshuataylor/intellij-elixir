package org.elixir_lang.injection.markdown

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.HeredocLiteral

class ClassifyLinesCacheTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/injection/markdown"

    fun testNoErrorIsLoggedAndTheResultIsActuallyCached() {
        myFixture.configureByFile("long_documentation.ex")
        val heredoc = PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first()

        val (results, errors) = captureLoggedErrors {
            val first = classifyLines(heredoc)
            val second = classifyLines(heredoc)
            Pair(first, second)
        }

        assertEmpty("classifyLines must not log any error just from being called", errors)
        assertTrue(
            "classifyLines should return the SAME cached instance on a second call for an unchanged " +
                    "heredoc, not recompute from scratch every time",
            results.first === results.second
        )
    }

    /**
     * #2813 is specifically about *typing* inside a large doc block - a cache keyed on the bare
     * `HeredocLiteral` PsiElement must invalidate when that element's own text changes, not just when
     * some unrelated part of the file does.
     */
    fun testInvalidatesWhenTheHeredocsOwnTextChanges() {
        myFixture.configureByFile("long_documentation.ex")
        val before = classifyLines(PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first())

        val insertionOffset = myFixture.file.text.indexOf("Paragraph one of the documentation.\n") +
                "Paragraph one of the documentation.\n".length

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            myFixture.editor.document.insertString(insertionOffset, "  A brand new inserted line.\n")
        }
        myFixture.editor.caretModel.moveToOffset(insertionOffset)
        com.intellij.psi.PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

        val afterHeredoc = PsiTreeUtil.findChildrenOfType(myFixture.file, HeredocLiteral::class.java).first()
        val after = classifyLines(afterHeredoc)

        assertTrue(
            "classifyLines must reflect the heredoc's new text after an in-place edit, not a stale cached " +
                    "result from before the edit:\nbefore=${before.map { it.lineMarkdownText }}\n" +
                    "after=${after.map { it.lineMarkdownText }}",
            after.any { it.lineMarkdownText.contains("A brand new inserted line.") }
        )
        assertFalse(
            "The stale pre-edit result must not equal the post-edit one",
            before.map { it.lineMarkdownText } == after.map { it.lineMarkdownText }
        )
    }
}
