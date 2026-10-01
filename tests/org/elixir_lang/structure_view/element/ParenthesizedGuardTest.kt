package org.elixir_lang.structure_view.element

import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.breadcrumbs.Provider
import org.elixir_lang.code_insight.assertGotoDeclarationLandsIn
import org.elixir_lang.code_insight.gotoDeclarationDestinationAtCaret
import org.elixir_lang.code_insight.searchTargetCountAtCaret
import org.elixir_lang.code_insight.singleTargetPsiUsagesAtCaret
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.structure_view.Model

/**
 * A definition whose guard is written inside the definer's parentheses, `def(h(q, x) when is_list(q), do: q)`, is
 * the same definition as `def h(q, x) when is_list(q), do: q`. Each `...Control` test is the unparenthesised form.
 */
class ParenthesizedGuardTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Go To Declaration needs a real DataContext.
        HeadlessDataManager.fallbackToProductionDataManager(myFixture.testRootDisposable)
    }

    fun testGuardCallFindsDefguardControl() =
        assertGuardCallFindsDefguard("defguard g(q, x) when is_list(q)")

    fun testGuardCallFindsDefguard() =
        assertGuardCallFindsDefguard("defguard(g(q, x) when is_list(q))")

    fun testGuardCallFindsDefguardpControl() =
        assertGuardCallFindsDefguard("defguardp g(q, x) when is_list(q)")

    fun testGuardCallFindsDefguardp() =
        assertGuardCallFindsDefguard("defguardp(g(q, x) when is_list(q))")

    fun testGuardCallFindsDefguardInKeywordElseListControl() =
        assertGuardCallFindsDefguard(
            "defguard g(q, x) when is_list(q)",
            wrap = { "if Code.ensure_loaded?(Absent), do: nil, else: [$it]" }
        )

    fun testGuardCallFindsDefguardInKeywordElseList() =
        assertGuardCallFindsDefguard(
            "defguard(g(q, x) when is_list(q))",
            wrap = { "if Code.ensure_loaded?(Absent), do: nil, else: [$it]" }
        )

    private fun assertGuardCallFindsDefguard(definition: String, wrap: (String) -> String = { it }) {
        myFixture.configureByText(
            "x.ex",
            "defmodule M do\n  ${wrap(definition)}\n\n  def f(a, b) when g<caret>(a, b), do: :ok\nend\n"
        )

        myFixture.assertGotoDeclarationLandsIn("g", definition) { it.text == definition }
    }

    fun testCallFindsDefControl() = assertCallFindsDefinition("def h(q, x) when is_list(q), do: q", "h")

    fun testCallFindsDef() = assertCallFindsDefinition("def(h(q, x) when is_list(q), do: q)", "h")

    fun testCallFindsDefWithDoBlockControl() =
        assertCallFindsDefinition("def h(q, x) when is_list(q) do\n    q\n  end", "h")

    fun testCallFindsDefWithDoBlock() =
        assertCallFindsDefinition("def(h(q, x) when is_list(q)) do\n    q\n  end", "h")

    fun testCallFindsDefpControl() = assertCallFindsDefinition("defp h(q, x) when is_list(q), do: q", "h")

    fun testCallFindsDefp() = assertCallFindsDefinition("defp(h(q, x) when is_list(q), do: q)", "h")

    fun testCallFindsDefmacroControl() = assertCallFindsDefinition("defmacro m(q, x) when is_atom(q), do: q", "m")

    fun testCallFindsDefmacro() = assertCallFindsDefinition("defmacro(m(q, x) when is_atom(q), do: q)", "m")

    fun testCallFindsDefmacropControl() = assertCallFindsDefinition("defmacrop m(q, x) when is_atom(q), do: q", "m")

    fun testCallFindsDefmacrop() = assertCallFindsDefinition("defmacrop(m(q, x) when is_atom(q), do: q)", "m")

    private fun assertCallFindsDefinition(definition: String, name: String) {
        myFixture.configureByText(
            "x.ex",
            "defmodule M do\n  $definition\n\n  def f, do: $name<caret>([1], 2)\nend\n"
        )

        myFixture.assertGotoDeclarationLandsIn(name, definition) { it.text == definition }
    }

    fun testStructureViewNamesDefControl() = assertStructureViewNamesDef("def h(q, x) when is_list(q), do: q")

    fun testStructureViewNamesDef() = assertStructureViewNamesDef("def(h(q, x) when is_list(q), do: q)")

    private fun assertStructureViewNamesDef(definition: String) {
        myFixture.configureByText("x.ex", "defmodule M do\n  $definition\nend\n")
        val module = Model(myFixture.file as ElixirFile, null).root.children.single()

        assertEquals(
            listOf("h/2"),
            module.children.map { (it as StructureViewTreeElement).presentation.presentableText }
        )
    }

    fun testFindUsagesFindsCallControl() = assertFindUsagesFindsCall("def h<caret>(q, x) when is_list(q), do: q")

    fun testFindUsagesFindsCall() = assertFindUsagesFindsCall("def(h<caret>(q, x) when is_list(q), do: q)")

    private fun assertFindUsagesFindsCall(definition: String) {
        myFixture.configureByText("x.ex", "defmodule M do\n  $definition\n\n  def f, do: h([1], 2)\nend\n")

        assertEquals("search targets at the head", 1, myFixture.searchTargetCountAtCaret())
        assertEquals(
            listOf(myFixture.file.text.indexOf("h([1], 2)")),
            myFixture.singleTargetPsiUsagesAtCaret(project).filterNot { it.declaration }.map { it.range.startOffset }
        )
    }

    fun testBreadcrumbsNameDefControl() = assertBreadcrumbsNameDef("def h(q, x) when is_list(q), do: <caret>q")

    fun testBreadcrumbsNameDef() = assertBreadcrumbsNameDef("def(h(q, x) when is_list(q), do: <caret>q)")

    private fun assertBreadcrumbsNameDef(definition: String) {
        myFixture.configureByText("x.ex", "defmodule M do\n  $definition\nend\n")
        val provider = Provider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!

        assertEquals(
            listOf("h/2", "M"),
            generateSequence<PsiElement>(leaf) { if (it is PsiFile) null else provider.getParent(it) }
                .filter { provider.acceptElement(it) }
                .map { provider.getElementInfo(it) }
                .toList()
        )
    }

    fun testGuardVariableFindsHeadParameterControl() =
        assertVariableFindsHeadParameter("def h(q, x) when is_list(q<caret>), do: q")

    fun testGuardVariableFindsHeadParameter() =
        assertVariableFindsHeadParameter("def(h(q, x) when is_list(q<caret>), do: q)")

    fun testBodyVariableFindsHeadParameterControl() =
        assertVariableFindsHeadParameter("def h(q, x) when is_list(q), do: q<caret>")

    fun testBodyVariableFindsHeadParameter() =
        assertVariableFindsHeadParameter("def(h(q, x) when is_list(q), do: q<caret>)")

    private fun assertVariableFindsHeadParameter(definition: String) {
        myFixture.configureByText("x.ex", "defmodule M do\n  $definition\nend\n")
        myFixture.editor.caretModel.moveToOffset(myFixture.caretOffset - 1)

        val destination = myFixture.gotoDeclarationDestinationAtCaret()

        assertEquals(myFixture.file.text.indexOf("h(q") + 2, destination?.textOffset)
    }

    fun testGuardOnlyVariableIsNotBoundControl() =
        assertGuardOnlyVariableIsNotBound("def h(q) when is_list(z), do: z<caret>")

    fun testGuardOnlyVariableIsNotBound() = assertGuardOnlyVariableIsNotBound("def(h(q) when is_list(z), do: z<caret>)")

    private fun assertGuardOnlyVariableIsNotBound(definition: String) {
        myFixture.configureByText("x.ex", "defmodule M do\n  $definition\nend\n")
        val guardZ = myFixture.file.text.indexOf("(z)") + 1
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset - 1) as? PsiPolyVariantReference
        assertNotNull("the body's `z` has no reference", reference)

        val resolved = reference!!.multiResolve(false).mapNotNull { it.element?.textOffset }

        assertFalse("the body's `z` resolved to the guard's `z` at $guardZ: $resolved", guardZ in resolved)
    }
}
