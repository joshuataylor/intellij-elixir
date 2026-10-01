package org.elixir_lang.expander

import org.elixir_lang.language_level.ElixirLanguageLevel

/**
 * Each ported clause of `elixir_expand`, `elixir_clauses` and `elixir_map`, over lowered snippets at every supported
 * minor, and at the first tag of each version difference.
 */
class ExpanderTest : ExpanderTestCase() {
    fun testLiteralsLeaveTheStateAlone() {
        for (literal in listOf("1", "1.5", ":a", "\"s\"", "nil", "true")) {
            assertEvery(literal, "expanded {} next 0")
        }
    }

    fun testAMatchBindsANewVariable() = assertEvery("x = 1", "expanded {x:0} next 1")

    fun testRebindingTakesANewVersion() = assertEvery("x = 1; x = 2", "expanded {x:1} next 2")

    fun testAReadKeepsTheVersion() = assertEvery("x = 1; y = x", "expanded {x:0 y:1} next 2")

    fun testAVariableRepeatedInAPatternIsOneBinding() = assertEvery("{x, x} = {1, 1}", "expanded {x:0} next 1")

    fun testAVariableBoundBeforeTheMatchIsOverriddenOnce() =
        assertEvery("x = 1; {x, y} = {x, 2}", "expanded {x:1 y:2} next 3")

    fun testATuplePattern() = assertEvery("{a, b, c} = {1, 2, 3}", "expanded {a:0 b:1 c:2} next 3")

    fun testAConsPattern() = assertEvery("[a | b] = [1, 2]", "expanded {a:0 b:1} next 2")

    fun testAListPattern() = assertEvery("[a, b] = [1, 2]", "expanded {a:0 b:1} next 2")

    fun testEmptyParentheses() = assertEvery("()", "expanded {} next 0")

    fun testANestedBlockThreadsItsVariablesOut() =
        assertEvery("(a = 1; b = a); a", "expanded {a:0 b:1} next 2")

    fun testANestedMatchExpandsTheRightSideFirst() = assertEvery("x = y = 1", "expanded {x:1 y:0} next 2")

    fun testUnderscoreTakesAVersionInAPatternFrom1_20() =
        assertSplit("_ = 1", "1.20.0-rc.5", "expanded {} next 0", "expanded {} next 1")

    fun testUnderscoreOutsideAPatternIsUnported() = assertEvery("_", "unported `_`")

    fun testAPinReadsTheVariableFromBeforeTheMatch() =
        assertEvery("x = 1; {x, ^x} = {2, 1}", "expanded {x:1} next 2")

    fun testAPinOfAVariableBoundInTheSameMatchIsUnported() =
        assertEvery("{x, ^x} = {1, 1}", "unported `x`")

    fun testAPinReadsTheVariablesFromBeforeTheRightSide() = assertEvery("^y = (y = 1)", "unported `y`")

    fun testAPinOfAnUndefinedVariableIsUnported() = assertEvery("^x = 1", "unported `x`")

    fun testAPinOfANonVariableIsUnported() = assertEvery("^1 = 1", "unported `^1`")

    fun testAPinOutsideAMatchIsUnported() = assertEvery("x = 1; ^x", "unported `^x`")

    fun testAPinnedVariable() = assertEvery("x = 1; ^x = 1", "expanded {x:0} next 1")

    fun testAPinnedMapKey() =
        assertEvery("key = :k; %{^key => v} = %{key => 1}", "expanded {key:0 v:1} next 2")

    fun testAMatchInsideAPatternTakesTheLeftSideFirst() =
        assertEvery("y = 1; (x = y) = 1", "expanded {x:1 y:2} next 3")

    fun testAMatchInsideATuplePattern() = assertEvery("{a = b, c} = {1, 2}", "expanded {a:0 b:1 c:2} next 3")

    fun testABindingInATupleElementShowsAfterTheTuple() =
        assertEvery("t = {x = 1, 2}", "expanded {t:1 x:0} next 2")

    fun testABindingInAListElementShowsAfterTheList() = assertEvery("l = [x = 1]", "expanded {l:1 x:0} next 2")

    fun testBindingsInSiblingElementsAllShowAfterTheTuple() =
        assertEvery("{x = 1, y = 2}", "expanded {x:0 y:1} next 2")

    fun testABindingIsNotVisibleToItsSiblingElement() = assertEvery("{x = 1, x}", "unported `x`")

    fun testABindingInAMapValueShowsAfterTheMap() = assertEvery("%{k: x = 1}", "expanded {x:0} next 1")

    fun testABindingInAMapUpdateShowsAfterTheMap() =
        assertEvery("m = %{k: 1}; %{m | k: v = 2}", "expanded {m:0 v:1} next 2")

    fun testAMapUpdate() = assertEvery("m = %{k: 1}; y = %{m | k: 2}", "expanded {m:0 y:1} next 2")

    fun testAMapUpdateInAPatternIsUnported() =
        assertEvery("m = %{k: 1}; %{m | k: v} = m", "unported `%{m | k: v}`")

    fun testAMapPattern() = assertEvery("%{k: x} = %{k: 1}", "expanded {x:0} next 1")

    fun testARepeatedKeyInAMapPatternIsUnported() =
        assertEvery("%{k: a, k: b} = %{k: 1}", "unported `%{k: a, k: b}`")

    fun testARepeatedKeyInAMapPatternIsComparedAsExpanded() =
        assertEvery("%{{(), 1} => a, {nil, 1} => b} = %{}", "unported `%{{(), 1} => a, {nil, 1} => b}`")

    fun testARepeatedKeyInAMapPatternIsComparedAsExpandedInsideAList() =
        assertEvery("%{[()] => a, [nil] => b} = %{}", "unported `%{[()] => a, [nil] => b}`")

    fun testARepeatedKeyOutsideAPatternOnlyWarns() = assertEvery("%{k: 1, k: 2}", "expanded {} next 0")

    fun testAVariableAsAMapKeyInAPatternIsUnported() =
        assertEvery("x = 1; %{x => 1} = %{1 => 1}", "unported `%{x => 1}`")

    fun testAPinnedMapKeyOfABoundVariable() =
        assertEvery("x = 1; %{^x => v} = %{1 => 2}", "expanded {v:1 x:0} next 2")

    fun testAPinNestedInAMapKeyInAPatternIsPortedFrom1_14() =
        assertSplit(
            "a = 1; %{{^a, 1} => v} = %{{1, 1} => 2}",
            "1.14.0-rc.0",
            "unported `%{{^a, 1} => v}`",
            "expanded {a:0 v:1} next 2"
        )

    fun testAZeroFloatInAPatternIsUnportedFrom1_16() =
        assertSplit("0.0 = 0.0", "1.16.0-rc.0", "expanded {} next 0", "unported `0.0`")

    fun testRecursiveVariablesInAPatternAreUnportedFrom1_18() {
        val code = "{x = y, x = {:ok, y}} = {{:ok, 1}, {:ok, 1}}"

        assertSplit(code, "1.18.0-rc.0", "expanded {x:0 y:1} next 2", "unported `$code`")
    }

    fun testAMatchOfAVariableWithItselfInAPatternIsRecursiveFrom1_18() =
        assertSplit("(x = x) = 1", "1.18.0-rc.0", "expanded {x:0} next 1", "unported `(x = x) = 1`")

    fun testAVariableRepeatedInAPatternInAnElementIsWrittenAtTheNextVersionOn1_18And1_19() =
        assertWindow(
            "t = [{x, x} = {1, 1}]",
            "1.18.0-rc.0",
            "1.20.0-rc.5",
            "expanded {t:1 x:0} next 2",
            "expanded {t:1 x:1} next 2"
        )

    fun testVariablesDefinedTogetherAreNotACycle() =
        assertEvery("(foo = {bar = {baz, bat}}) = {{1, 2}}", "expanded {bar:1 bat:3 baz:2 foo:0} next 4")

    fun testAnUndefinedVariableIsUnported() = assertEvery("x", "unported `x`")

    fun testTheEnvironmentNamesAreNotVariables() {
        for (name in listOf("__MODULE__", "__DIR__", "__CALLER__", "__STACKTRACE__", "__ENV__")) {
            assertEvery("$name = :a", "unported `$name`")
            assertEvery("l = [1]; [$name | t] = l", "unported `$name`")
            assertEvery(name, "unported `$name`")
        }
    }

    fun testAStructIsUnported() = assertEvery("%Struct{}", "unported `%Struct{}`")

    fun testALocalCallIsUnported() = assertEvery("foo(1)", "unported `foo(1)`")

    fun testUnderscoreInAConsPattern() =
        assertSplit("[_ | t] = [1, 2]", "1.20.0-rc.5", "expanded {t:0} next 1", "expanded {t:1} next 2")

    fun testUnderscoreTakesNoVersionWhileARepeatedVariableIsWrittenAtTheNextVersion() {
        val written = ElixirLanguageLevel.of("1.18.0-rc.0").elixir
        val taken = ElixirLanguageLevel.of("1.20.0-rc.5").elixir

        assertLevels(
            "t = [{x, x, _} = {1, 1, 2}]",
            (LEVELS + listOf("1.18.0-rc.0", "1.20.0-rc.4", "1.20.0-rc.5"))
                .sortedBy { ElixirLanguageLevel.of(it).elixir }
        ) { version ->
            val elixir = ElixirLanguageLevel.of(version).elixir

            when {
                elixir < written -> "expanded {t:1 x:0} next 2"
                elixir < taken -> "expanded {t:1 x:1} next 2"
                else -> "expanded {t:2 x:0} next 3"
            }
        }
    }
}
