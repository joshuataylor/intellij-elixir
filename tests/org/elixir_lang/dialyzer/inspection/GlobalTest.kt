package org.elixir_lang.dialyzer.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.ClassFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.dialyzer.service.DialyzerWarn
import org.junit.Assert
import java.util.concurrent.Callable

class GlobalTest : PlatformTestCase() {
    fun testAWarningOnAnElixirFileIsReported() {
        val file = myFixture.configureByText("warned.ex", "defmodule Warned do\nend\n")

        assertReportsOneWarning(file, DialyzerWarn("warned.ex", 1, "no return"))
    }

    // Mix names a template in a compile error, and a template's PSI root is EEx, not Elixir.
    fun testAWarningOnAnEExTemplateIsReported() {
        val file = myFixture.configureByText("page.html.eex", "<h1><%= @greeting %></h1>\n")

        assertReportsOneWarning(file, DialyzerWarn("page.html.eex", 1, "no return"))
    }

    // `AnalysisScope` hands a global tool the `.class` files in content, which get `ClsFileImpl` outside source roots.
    fun testACompiledClassFileIsNotWalked() {
        val file = compiledClassFile("GlobalTest.class")

        val (problems, errors) = captureLoggedErrors { checkFile(file, DialyzerWarn("GlobalTest.class", 1, "no return")) }

        Assert.assertEquals("problems: $problems", emptyList<Any>(), errors)
        Assert.assertEquals("errors: $errors", emptyList<ProblemDescriptor>(), problems)
    }

    private fun assertReportsOneWarning(file: PsiFile, warn: DialyzerWarn) {
        val (problems, errors) = captureLoggedErrors { checkFile(file, warn) }

        Assert.assertEquals("problems: $problems", emptyList<Any>(), errors)
        Assert.assertEquals("problems: $problems", 1, problems.size)
        Assert.assertTrue(
            "the problem must carry the warning, got ${problems.single().descriptionTemplate}",
            problems.single().descriptionTemplate.contains(warn.message),
        )
    }

    private fun checkFile(file: PsiFile, warn: DialyzerWarn): List<ProblemDescriptor> =
        ReadAction.nonBlocking(Callable {
            Global().checkFile(file, InspectionManager.getInstance(project), mapOf(module to mutableListOf(warn)))
        }).executeSynchronously()

    // `PsiManager.findFile` gives a `.class` under a source root `PsiBinaryFileImpl`, and the light fixture's only
    // content root is a source root, so the `ClsFileImpl` a non-source content root would get is built directly.
    private fun compiledClassFile(name: String): PsiFile {
        val bytes = GlobalTest::class.java.getResourceAsStream("GlobalTest.class")!!.use { it.readAllBytes() }
        val virtualFile = myFixture.tempDirFixture.createFile(name)
        WriteAction.run<Throwable> { virtualFile.setBinaryContent(bytes) }

        return ClsFileImpl(ClassFileViewProvider(PsiManager.getInstance(project), virtualFile))
    }
}
