package org.elixir_lang.psi.scope

import com.intellij.ide.impl.HeadlessDataManager
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.code_insight.gotoDeclarationTargetsAtCaret

/**
 * In `pattern when guard`, only the pattern binds: the guard and the body read the pattern's variables and any bound
 * before it, whether the `when` is written inside a call's parentheses or not.
 */
class WhenPatternVariableTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Go To Declaration needs a real DataContext.
        HeadlessDataManager.fallbackToProductionDataManager(myFixture.testRootDisposable)
    }

    fun testForKeywordControl() = assertEveryVariableFindsItsBinding("for x when x > 0 <- [1], do: x")

    fun testForKeyword() = assertEveryVariableFindsItsBinding("for(x when x > 0 <- [1], do: x)")

    fun testForDoBlockControl() = assertEveryVariableFindsItsBinding("for x when x > 0 <- [1] do\n      x\n    end")

    fun testForDoBlock() = assertEveryVariableFindsItsBinding("for(x when x > 0 <- [1]) do\n      x\n    end")

    fun testWithControl() = assertEveryVariableFindsItsBinding("with {:ok, x} when x > 0 <- f(), do: x")

    fun testWith() = assertEveryVariableFindsItsBinding("with({:ok, x} when x > 0 <- f(), do: x)")

    fun testFnControl() = assertEveryVariableFindsItsBinding("fn x when x > 0 -> x end")

    fun testFn() = assertEveryVariableFindsItsBinding("fn(x when x > 0) -> x end")

    fun testCase() = assertEveryVariableFindsItsBinding("case 1 do\n      x when x > 0 -> x\n    end")

    fun testOuterVariableInFnGuardControl() = assertEveryVariableFindsItsBinding("y = 1\n    fn x when x > y -> y end")

    fun testOuterVariableInFnGuard() = assertEveryVariableFindsItsBinding("y = 1\n    fn(x when x > y) -> y end")

    fun testOuterVariableInForKeywordGuardControl() =
        assertEveryVariableFindsItsBinding("y = 1\n    for x when x > y <- [1], do: y")

    fun testOuterVariableInForKeywordGuard() =
        assertEveryVariableFindsItsBinding("y = 1\n    for(x when x > y <- [1], do: y)")

    fun testOuterVariableInForDoBlockGuardControl() =
        assertEveryVariableFindsItsBinding("y = 1\n    for x when x > y <- [1] do\n      y\n    end")

    fun testOuterVariableInForDoBlockGuard() =
        assertEveryVariableFindsItsBinding("y = 1\n    for(x when x > y <- [1]) do\n      y\n    end")

    fun testOuterVariableInWithGuardControl() =
        assertEveryVariableFindsItsBinding("y = 1\n    with {:ok, x} when x > y <- f(), do: y")

    fun testOuterVariableInWithGuard() =
        assertEveryVariableFindsItsBinding("y = 1\n    with({:ok, x} when x > y <- f(), do: y)")

    /** Every `x` finds the first `x`, and every `y` the first `y`. */
    private fun assertEveryVariableFindsItsBinding(expression: String) {
        val text = "defmodule M do\n  def f do\n    $expression\n  end\nend\n"
        myFixture.configureByText("x.ex", text)
        val variables = Regex("\\b[xy]\\b").findAll(text).map { it.value to it.range.first }.toList()
        val bindings = variables.groupBy({ it.first }, { it.second }).mapValues { (_, offsets) -> offsets.first() }

        assertEquals(
            variables.map { (name, offset) -> "$name@$offset -> ${listOf(bindings[name])}" },
            variables.map { (name, offset) ->
                myFixture.editor.caretModel.moveToOffset(offset)
                "$name@$offset -> ${myFixture.gotoDeclarationTargetsAtCaret()?.map { it.destination?.textOffset }}"
            }
        )
    }
}
