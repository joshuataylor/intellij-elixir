package org.elixir_lang.model.psi.function

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.code_insight.renameTargetAtCaret
import org.elixir_lang.code_insight.singleTargetPsiUsagesAtCaret

/**
 * Find Usages and rename from a definition reach the uses Elixir calls it by, which may be spelled differently from the
 * atom the definition is named by.
 */
class FunctionSpellingUsagesTest : PlatformTestCase() {
    fun testDecomposedDefinitionFindsADecomposedUse() =
        assertFindsUses("defmodule M do\n  def $DECOMPOSED<caret>(a), do: a\n  def f(a), do: $DECOMPOSED(a)\nend\n")

    fun testComposedDefinitionFindsADecomposedUse() =
        assertFindsUses("defmodule M do\n  def $COMPOSED<caret>(a), do: a\n  def f(a), do: $DECOMPOSED(a)\nend\n")

    fun testMicroSignDefinitionFindsAMicroSignUse() =
        assertFindsUses("defmodule M do\n  def $MICRO<caret>(a), do: a\n  def f(a), do: $MICRO(a)\nend\n")

    fun testDecomposedDefinitionRenamesADecomposedUse() =
        assertRenames(
            "defmodule M do\n  def $DECOMPOSED<caret>(a), do: a\n  def f(a), do: $DECOMPOSED(a)\nend\n",
            "defmodule M do\n  def renamed(a), do: a\n  def f(a), do: renamed(a)\nend\n",
        )

    fun testComposedDefinitionRenamesADecomposedUse() =
        assertRenames(
            "defmodule M do\n  def $COMPOSED<caret>(a), do: a\n  def f(a), do: $DECOMPOSED(a)\nend\n",
            "defmodule M do\n  def renamed(a), do: a\n  def f(a), do: renamed(a)\nend\n",
        )

    fun testMicroSignDefinitionRenamesAMicroSignUse() =
        assertRenames(
            "defmodule M do\n  def $MICRO<caret>(a), do: a\n  def f(a), do: $MICRO(a)\nend\n",
            "defmodule M do\n  def renamed(a), do: a\n  def f(a), do: renamed(a)\nend\n",
        )

    private fun assertFindsUses(source: String) {
        myFixture.configureByText("spelling.ex", source)
        val use = myFixture.file.text.lastIndexOf("(a)")
        val name = myFixture.file.text.lastIndexOf(' ', use) + 1

        assertEquals(
            listOf(name),
            myFixture.singleTargetPsiUsagesAtCaret(project).filterNot { it.declaration }.map { it.range.startOffset },
        )
    }

    private fun assertRenames(source: String, expected: String) {
        myFixture.configureByText("spelling.ex", source)
        myFixture.renameTargetAtCaret("renamed")

        assertEquals(expected, myFixture.file.text)
    }

    private companion object {
        const val DECOMPOSED = "snoć"
        const val COMPOSED = "snoć"
        const val MICRO = "src_µ"
    }
}
