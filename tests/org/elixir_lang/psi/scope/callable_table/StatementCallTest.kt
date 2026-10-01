package org.elixir_lang.psi.scope.callable_table

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.scope.CallableTable
import org.elixir_lang.psi.scope.WalkTestSupport.location

/**
 * A module-level call that the module's walk tells apart by resolving it, such as `test`, resolves as walking the
 * module does when its own resolve is the first to need the module's table.
 */
class StatementCallTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
        RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    }

    override fun tearDown() {
        try {
            CallableTable.enabled = true
        } finally {
            super.tearDown()
        }
    }

    fun testTestInAComprehension() = assertAsWalked(TEST_IN_A_COMPREHENSION, "test \"handles", false)

    /** The module's walk resolves the `test` for complete code, while this resolve is for incomplete code. */
    fun testTestInAComprehensionForIncompleteCode() =
        assertAsWalked(TEST_IN_A_COMPREHENSION, "test \"handles", true)

    private fun assertAsWalked(text: String, use: String, incompleteCode: Boolean) {
        val file = myFixture.configureByText("statement.ex", text.trimIndent())
        val tabled = answer(file, use, incompleteCode)
        CallableTable.enabled = false
        // A file added is a PSI change, so every cached resolve is recomputed without the table.
        myFixture.addFileToProject("without_table.ex", "")
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertEquals(answer(file, use, incompleteCode), tabled)
    }

    private fun answer(file: PsiFile, use: String, incompleteCode: Boolean): List<String> {
        val offset = file.text.indexOf(use).also { check(it >= 0) { "no `$use`" } }
        val reference = file.viewProvider.findReferenceAt(offset, ElixirLanguage) as PsiPolyVariantReference

        return reference.multiResolve(incompleteCode).map { "${location(it.element)}${if (it.isValidResult) "" else " (invalid)"}" }
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"

    private companion object {
        const val TEST_IN_A_COMPREHENSION = """
            defmodule ComprehensionTestNameSites do
              use ExUnit.Case

              for renamee <- [1, 2] do
                test "handles #{renamee}" do
                  assert renamee == 1
                end
              end
            end
            """
    }
}
