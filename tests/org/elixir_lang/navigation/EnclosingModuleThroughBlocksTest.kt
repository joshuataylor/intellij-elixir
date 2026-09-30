package org.elixir_lang.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ChooseByNameRegistry
import org.elixir_lang.PlatformTestCase

/**
 * A definition written in the `do` block of a macro that runs its block in place, or of a macro whose effect only
 * its expansion knows, belongs to the module around that macro.
 */
class EnclosingModuleThroughBlocksTest : PlatformTestCase() {
    fun testDefInIf() = assertDefInOuter("if true do\n    def f, do: 1\n  end")

    fun testDefInElseOfIf() = assertDefInOuter("if true do\n    :ok\n  else\n    def f, do: 1\n  end")

    fun testDefInUnless() = assertDefInOuter("unless false do\n    def f, do: 1\n  end")

    fun testDefInCase() = assertDefInOuter("case :a do\n    :a -> def f, do: 1\n  end")

    fun testDefInCond() = assertDefInOuter("cond do\n    true -> def f, do: 1\n  end")

    fun testDefInTry() = assertDefInOuter("try do\n    def f, do: 1\n  rescue\n    _ -> :ok\n  end")

    fun testDefInReceive() = assertDefInOuter("receive do\n    _ -> def f, do: 1\n  end")

    fun testDefInWith() = assertDefInOuter("with :ok <- :ok do\n    def f, do: 1\n  end")

    fun testDefInFor() = assertDefInOuter("for n <- [1] do\n    def f, do: n\n  end")

    fun testDefInIfInCase() =
        assertDefInOuter("case :a do\n    :a ->\n      if true do\n        def f, do: 1\n      end\n  end")

    fun testDefInUnknownMacro() = assertDefInOuter("describe \"x\" do\n    def f, do: 1\n  end")

    fun testDefInQualifiedUnknownMacro() = assertDefInOuter("Some.dsl do\n    def f, do: 1\n  end")

    fun testDefInUnknownMacroInUnknownMacro() =
        assertDefInOuter("describe \"x\" do\n    test \"y\" do\n      def f, do: 1\n    end\n  end")

    fun testModuleInIfIsNestedInTheEnclosingModule() =
        assertModuleNamed("Outer.Inner", "if true do\n    defmodule Inner do\n    end\n  end")

    fun testModuleInCaseIsNestedInTheEnclosingModule() =
        assertModuleNamed("Outer.Inner", "case :a do\n    :a -> defmodule Inner do\n    end\n  end")

    fun testModuleInUnknownMacroIsNestedInTheEnclosingModule() =
        assertModuleNamed("Outer.Inner", "describe \"x\" do\n    defmodule Inner do\n    end\n  end")

    fun testModuleInModuleIsNestedInIt() =
        assertModuleNamed("Outer.Inner", "defmodule Inner do\n  end")

    fun testDefInModuleInIfBelongsToTheInnerModule() =
        assertEquals(
            defLocations("defmodule Inner do\n    def f, do: 1\n  end"),
            defLocations("if true do\n    defmodule Inner do\n      def f, do: 1\n    end\n  end")
        )

    fun testDefInIfInQuoteBelongsToTheQuote() =
        assertEquals(
            defLocations("defmacro m do\n    quote do\n      def f, do: 1\n    end\n  end"),
            defLocations("defmacro m do\n    quote do\n      if true do\n        def f, do: 1\n      end\n    end\n  end")
        )

    fun testDefInIfInFunctionBodyIsNotInTheModule() {
        val inModule = defLocations("def f, do: 1")

        val inFunctionBody = defLocations("def g do\n    if true do\n      def f, do: 1\n    end\n  end")

        assertEquals(inModule.map { "$it g" }, inFunctionBody)
    }

    fun testModuleInTopLevelIfIsNotNested() {
        myFixture.configureByText("top.ex", "if true do\n  defmodule Foo do\n  end\nend\n")

        assertEquals(1, items(GotoClassContributor::class.java, "Foo").size)
    }

    fun testDefInTopLevelIfHasNoEntry() {
        myFixture.configureByText("top.ex", "if true do\n  def f, do: 1\nend\n")

        assertEmpty(items(GotoSymbolContributor::class.java, "f"))
    }

    fun testDefInTopLevelUnknownMacroIsListedUnderIt() {
        myFixture.configureByText("top.ex", "definst Proto, for: X do\n  def f, do: 1\nend\n")

        assertEquals(1, items(GotoSymbolContributor::class.java, "f").size)
    }

    private fun assertDefInOuter(body: String) = assertEquals(defLocations("def f, do: 1"), defLocations(body))

    private fun defLocations(body: String): List<String?> {
        configureInOuter(body)

        return items(GotoSymbolContributor::class.java, "f").map { it.presentation?.locationString }
    }

    private fun assertModuleNamed(name: String, body: String) {
        configureInOuter(body)

        assertEquals(1, items(GotoClassContributor::class.java, name).size)
    }

    private fun configureInOuter(body: String) {
        myFixture.configureByText("outer.ex", "defmodule Outer do\n  $body\nend\n")
    }

    private fun items(contributorClass: Class<out ChooseByNameContributor>, name: String) =
        ChooseByNameRegistry.getInstance().let { it.symbolModelContributors + it.classModelContributors }
            .single { contributorClass.isInstance(it) }
            .getItemsByName(name, name, myFixture.project, false)
            .toList()
}
