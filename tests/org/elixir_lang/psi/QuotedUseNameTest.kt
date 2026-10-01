package org.elixir_lang.psi

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.code_insight.ParameterInfo
import org.elixir_lang.code_insight.Signature
import org.elixir_lang.code_insight.completionStringsAtCaret
import org.elixir_lang.mix.DepGatherer
import org.elixir_lang.model.psi.callback.BehaviourMembership
import org.elixir_lang.psi.call.Call

/** A use names what it refers to by the atom it quotes to, as the definitions it is matched against are named. */
class QuotedUseNameTest : PlatformTestCase() {
    fun testUseWithAQuotedAtomInjectsOnlyThatDefinersDefinitions() {
        myFixture.configureByText(
            "use.ex",
            "defmodule Web do\n" +
                "  def controller do\n    quote do\n      def injected_by_controller(), do: :ok\n    end\n  end\n\n" +
                "  def view do\n    quote do\n      def injected_by_view(), do: :ok\n    end\n  end\n\n" +
                "  defmacro __using__(which) do\n    apply(__MODULE__, which, [])\n  end\nend\n\n" +
                "defmodule Ctl do\n  use Web, :\"controller\"\n\n  def usage do\n    injected_by_view<caret>\n  end\nend\n"
        )
        val call = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset - 1), Call::class.java)!!

        assertEquals(
            emptyList<String>(),
            (call.reference as PsiPolyVariantReference)
                .multiResolve(false)
                .filter { it.isValidResult }
                .map { it.element!!.text }
                .filter { it.startsWith("def ") },
        )
    }

    fun testDepsFunctionSpelledDecomposed() {
        val file = myFixture.configureByText(
            "mix.exs",
            "defmodule Sample.MixProject do\n  use Mix.Project\n\n" +
                "  def project do\n    [app: :s, deps: $DECOMPOSED()]\n  end\n\n" +
                "  defp $DECOMPOSED do\n    [{:jason, \"~> 1.0\"}]\n  end\nend\n"
        )
        val gatherer = DepGatherer()

        file.accept(gatherer)

        assertEquals(listOf("jason"), gatherer.depSet.map { it.application })
    }

    fun testUnresolvedBehaviourWrittenWithTheElixirPrefix() {
        myFixture.configureByText("behaviour.ex", "defmodule Impl do\n  @behaviour Elixir.Missing\nend\n")
        val module = PsiTreeUtil.findChildrenOfType(myFixture.file, Call::class.java).single { Module.`is`(it) }

        assertEquals(setOf("Missing"), BehaviourMembership.namesImplementedBy(module))
    }

    fun testAliasesUnderANamespaceWrittenWithTheElixirPrefix() {
        myFixture.configureByText(
            "namespace.ex",
            "defmodule Space.Bar do\nend\n\ndefmodule Space.Baz do\nend\n\n" +
                "defmodule User do\n  alias Elixir.Space.{Ba<caret>}\nend\n"
        )

        assertContainsElements(myFixture.completionStringsAtCaret().orEmpty(), "Bar", "Baz")
    }

    fun testParameterInfoForACallSpelledDecomposed() {
        myFixture.configureByText(
            "parameter_info.ex",
            "defmodule M do\n  def $DECOMPOSED(a, b), do: a\n  def f, do: $DECOMPOSED(<caret>1, 2)\nend\n"
        )
        val handler = ParameterInfo()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val arguments = handler.findElementForParameterInfo(context)!!
        handler.showParameterInfo(arguments, context)

        assertEquals(
            listOf("a, b"),
            context.itemsToShow.orEmpty().map { item ->
                MockParameterInfoUIContext(arguments).also { handler.updateUI(item as Signature, it) }.text
            },
        )
    }

    private companion object {
        const val DECOMPOSED = "snoć"
    }
}
