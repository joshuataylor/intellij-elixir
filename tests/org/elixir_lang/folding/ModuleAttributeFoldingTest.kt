package org.elixir_lang.folding

import com.intellij.codeInsight.folding.CodeFoldingManager
import org.elixir_lang.PlatformTestCase

class ModuleAttributeFoldingTest : PlatformTestCase() {
    fun testAttributeFoldsToValue() {
        myFixture.configureByText(
            "test.ex",
            """
                defmodule Timeouts do
                  @timeout 5000

                  def timeout, do: @timeout
                end
            """.trimIndent()
        )

        assertEquals("5000", attributePlaceholder("@timeout"))
    }

    fun testForFoldsToForModule() {
        myFixture.configureByText(
            "test.ex",
            """
                defimpl Inspect, for: Tuple do
                  def inspect(_, _), do: @for
                end
            """.trimIndent()
        )

        assertEquals("Tuple", attributePlaceholder("@for"))
    }

    fun testForWithoutForFoldsToEnclosingModule() {
        myFixture.configureByText(
            "test.ex",
            """
                defmodule Outer do
                  defimpl Inspect do
                    def inspect(_, _), do: @for
                  end
                end
            """.trimIndent()
        )

        assertEquals("Outer", attributePlaceholder("@for"))
    }

    fun testForAsOperandFoldsToForModule() {
        myFixture.configureByText(
            "test.ex",
            """
                defimpl Inspect, for: Tuple do
                  def inspect(term, _) do
                    if term == @for do
                      "{}"
                    end
                  end
                end
            """.trimIndent()
        )

        assertEquals("Tuple", attributePlaceholder("@for"))
    }

    fun testForWithForListFoldsToList() {
        myFixture.configureByText(
            "test.ex",
            """
                defimpl Inspect, for: [Tuple, List] do
                  def inspect(_, _), do: @for
                end
            """.trimIndent()
        )

        assertEquals("[Tuple, List]", attributePlaceholder("@for"))
    }

    fun testProtocolFoldsToProtocol() {
        myFixture.configureByText(
            "test.ex",
            """
                defimpl Inspect, for: Tuple do
                  def inspect(_, _), do: @protocol
                end
            """.trimIndent()
        )

        assertEquals("Inspect", attributePlaceholder("@protocol"))
    }

    private fun attributePlaceholder(attribute: String): String? {
        CodeFoldingManager.getInstance(project).updateFoldRegions(myFixture.editor)
        val start = myFixture.file.text.lastIndexOf(attribute)
        val regions = myFixture.editor.foldingModel.allFoldRegions
        assertTrue("The folding pass built no regions at all", regions.isNotEmpty())

        return regions
            .singleOrNull { it.startOffset == start && it.endOffset == start + attribute.length }
            ?.placeholderText
    }
}
