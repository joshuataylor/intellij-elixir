package org.elixir_lang.declaration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.util.FileContentUtil
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.call.Visibility
import org.elixir_lang.psi.CallDefinitionClause
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.declaringCall

class SourceDeclarationTest : PlatformTestCase() {
    fun testDefinerOfEachClause() {
        myFixture.configureByText("definers.ex", DEFINERS)

        assertEquals(
            listOf(
                Definer.DEF,
                Definer.DEFP,
                Definer.DEFMACRO,
                Definer.DEFMACROP,
                Definer.DEFGUARD,
                Definer.DEFGUARDP
            ),
            listOf("a", "b", "c", "d", "e", "f").map { CallDefinitionClause.definer(clause(myFixture.file, "$it(")) }
        )
    }

    fun testNoDefinerForOtherCalls() {
        myFixture.configureByText("others.ex", "defmodule M do\n  defdelegate g(x), to: N\n  foo(1)\nend\n")

        assertNull(CallDefinitionClause.definer(callAt(myFixture.file, "defdelegate")))
        assertNull(CallDefinitionClause.definer(callAt(myFixture.file, "foo(1)")))
        assertNull(CallDefinitionClause.definer(callAt(myFixture.file, "defmodule")))
    }

    fun testDeclaration() {
        myFixture.configureByText("declaration.ex", "defmodule M do\n  defmacrop m(a, b \\\\ 1), do: {a, b}\nend\n")
        val clause = clause(myFixture.file, "m(")

        assertEquals(
            Declaration(
                "m",
                ArityKnowledge.Range(1, 2),
                Capabilities(quotesArguments = true, compileTime = true, usableInGuards = false, Visibility.PRIVATE),
                Declared.Source(
                    Form.CLAUSE,
                    SourceOrigin(myFixture.file.virtualFile, myFixture.file.viewProvider.modificationStamp, clause.textRange)
                )
            ),
            CallDefinitionClause.declaration(clause, ResolveState.initial())
        )
    }

    fun testOriginResolvesBackToTheClause() {
        myFixture.configureByText("resolve.ex", DEFINERS)
        val clause = clause(myFixture.file, "e(")

        assertSame(clause, origin(clause).declaringCall(project))
    }

    fun testOriginResolvesToNothingAfterAnEdit() {
        myFixture.configureByText("edited.ex", DEFINERS)
        val origin = origin(clause(myFixture.file, "e("))

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "\n")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        assertNull(origin.declaringCall(project))
    }

    fun testOriginResolvesToNothingForAnotherRange() {
        myFixture.configureByText("range.ex", DEFINERS)
        val origin = origin(clause(myFixture.file, "e("))

        assertNull(origin.copy(range = origin.range.grown(-1)).declaringCall(project))
        assertNull(
            origin.copy(range = TextRange(origin.range.startOffset + 1, origin.range.endOffset)).declaringCall(project)
        )
    }

    fun testOriginOfANonPhysicalFile() {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("non_physical.ex", ElixirLanguage, DEFINERS, false, true)
        assertFalse(file.isPhysical)
        val clause = clause(file, "e(")

        assertSame(clause, origin(clause).declaringCall(project))
    }

    fun testOriginResolvesToNothingAfterTheFileIsReloadedFromDisk() {
        val file = myFixture.addFileToProject("reloaded.ex", DEFINERS)
        val virtualFile = file.virtualFile
        val origin = origin(clause(file, "e("))

        WriteCommandAction.runWriteCommandAction(project) {
            virtualFile.setBinaryContent(DEFINERS.replace(" e(", " z(").toByteArray())
        }
        FileContentUtil.reparseFiles(project, listOf(virtualFile), false)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val reloaded = PsiManager.getInstance(project).findFile(virtualFile)!!
        assertNotSame(file, reloaded)
        assertTrue(reloaded.text.contains(" z("))

        assertNull(origin.declaringCall(project))
    }

    fun testOriginOfATemplate() {
        myFixture.configureByText("template.eex", "<% def e(x), do: x %>\n")
        val elixir = myFixture.file.viewProvider.getPsi(ElixirLanguage)!!
        val clause = clause(elixir, "e(")

        assertSame(clause, origin(clause).declaringCall(project))
    }

    fun testOriginOfACompletionCopy() {
        myFixture.configureByText("original.ex", DEFINERS)
        val copy = PsiFileFactory.getInstance(project)
            .createFileFromText("original.ex", ElixirLanguage, "# shifted\n$DEFINERS", false, true) as PsiFileImpl
        copy.originalFile = myFixture.file
        val clause = clause(copy, "e(")

        assertSame(clause, origin(clause).declaringCall(project))
    }

    fun testOriginOfADeletedFile() {
        val file = myFixture.addFileToProject("deleted.ex", DEFINERS)
        val virtualFile = file.virtualFile
        val origin = origin(clause(file, "e("))

        WriteCommandAction.runWriteCommandAction(project) { virtualFile.delete(this) }

        assertNull(origin.declaringCall(project))
    }

    private fun origin(clause: Call): SourceOrigin =
        (CallDefinitionClause.declaration(clause, ResolveState.initial())!!.declared as Declared.Source).origin

    private fun callAt(file: PsiFile, text: String): Call =
        generateSequence(file.findElementAt(file.text.indexOf(text))) { it.parent }.filterIsInstance<Call>().first()

    private fun clause(file: PsiFile, head: String): Call =
        generateSequence(file.findElementAt(file.text.indexOf(" $head") + 1)) { it.parent }
            .filterIsInstance<Call>()
            .first { it.functionName()?.startsWith("def") == true }

    companion object {
        private const val DEFINERS = """defmodule M do
  def a(x), do: x
  defp b(x), do: x
  defmacro c(x), do: x
  defmacrop d(x), do: x
  defguard e(x) when is_integer(x)
  defguardp f(x) when is_integer(x)
end
"""
    }
}
