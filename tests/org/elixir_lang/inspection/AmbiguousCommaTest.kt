package org.elixir_lang.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import org.elixir_lang.ElixirFileType
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.junit.LightTestCase

/**
 * Which commas Elixir rejects, and with which message, was taken from `Code.string_to_quoted/1` on 1.11.4, 1.12.3,
 * 1.14.5, 1.15.8, 1.19.5 and 1.20.4, which all agree. `^C^` and `^N^` mark a comma reported as inside a container and
 * in a nested call.
 */
class AmbiguousCommaTest : LightTestCase() {
    fun testInsideContainers() {
        for (source in listOf(
            "[foo 1^C^, 2]",
            "[foo bar 1^C^, 2]",
            "{foo 1^C^, 2}",
            "{foo bar 1^C^, 2}",
            "<<foo 1^C^, 2>>",
            "[foo 1^C^, 2 | t]",
            "[h | foo 1^C^, 2]",
            "[x, foo 1^C^, 2]",
            "Foo.{foo 1^C^, 2}",
            "x[foo 1^C^, 2]",
            "[a: foo 1^C^, 2]",
            "%{a: foo 1^C^, 2}",
            "%Foo{a: foo 1^C^, 2}",
            "foo(a: bar 1^C^, 2)",
            "foo(1, a: bar 2^C^, 3)",
            "[foo 1^C^, a: 2]",
            "{foo 1^C^, a: 2}",
            "[foo.bar 1^C^, 2]",
            "[@foo 1^C^, 2]",
            "[!foo 1^C^, 2]",
            "[x = foo 1^C^, 2]",
            "[1 + foo 2^C^, 3]",
            "{a, foo 1^C^, 2}",
            "%{a: 1, b: foo 2^C^, 3}",
            "<<a, foo 1^C^, 2>>",
            "Foo.{Bar, foo 1^C^, 2}",
            "[foo 1^C^, 2, 3]",
        )) {
            assertCommas(source)
        }
    }

    fun testInNestedCalls() {
        for (source in listOf(
            "foo 1, 2 + bar 3^N^, 4",
            "foo 1, bar 2^N^, 3",
            "foo a, bar b^N^, c",
            "foo(1, bar 2^N^, 3)",
            "foo 1, 2 |> bar 3^N^, 4",
            "foo 1, 2 when bar 3^N^, 4",
            "foo 1, !bar 2^N^, 3",
            "foo 1, @bar 2^N^, 3",
            "foo [1, 2], bar 3^N^, 4",
            "foo 1, bar baz 2^N^, 3",
            "foo(1, bar 2^N^, 3, 4)",
            "foo 1, x = bar 2^N^, 3",
        )) {
            assertCommas(source)
        }
    }

    /** Elixir's parser stops at the innermost ambiguity, so the ones around it are not reported. */
    fun testOnlyTheInnermostAmbiguityIsReported() {
        for (source in listOf(
            "[foo 1, bar 2^N^, 3]",
            "[foo 1, [x, bar 2^C^, 3]]",
            "[foo 1, {bar 2^C^, 3}]",
            "foo 1, bar 2, [baz 3^C^, 4]",
            "foo 1, bar 2, baz 3^N^, 4",
            "foo(1, bar 2, baz 3^N^, 4)",
        )) {
            assertCommas(source)
        }
    }

    fun testKeywordValuesInMapUpdatesAndBrackets() {
        for (source in listOf("%{map | a: foo 1^C^, 2}", "%Foo{map | a: foo 1^C^, 2}", "x[a: foo 1^C^, 2]")) {
            assertCommas(source)
        }
    }

    fun testElixirInDocumentationIsNotChecked() {
        val source = "defmodule Sample do\n  @moduledoc \"\"\"\n      [function a, b, c]\n  \"\"\"\nend\n"

        myFixture.enableInspections(NoParenthesesManyStrict::class.java)
        myFixture.configureByText(ElixirFileType.INSTANCE, source)

        assertEquals(
            "no Elixir is injected into $source, so this test checks nothing",
            ElixirLanguage,
            InjectedLanguageManager.getInstance(project).findInjectedElementAt(myFixture.file, source.indexOf("function"))?.language
        )
        assertEquals(emptyList<String?>(), myFixture.doHighlighting(HighlightSeverity.ERROR).map { it.description })
    }

    fun testCallWithADoBlockIsNotAmbiguous() {
        for (source in listOf(
            "foo(1, bar 2, 3 do\n:ok\nend)",
            "[foo 1, 2 do\n:ok\nend]",
            "[a: foo 1, 2 do\n:ok\nend]",
            "[foo bar 1, 2 do\n:ok\nend]",
            "foo(1, for x <- y, reduce: :ok do\n:ok -> 1\nend)",
        )) {
            assertCommas(source)
        }
    }

    /** The `do` block belongs to the outer call, so the nested call still takes the comma. */
    fun testDoBlockOfTheOuterCall() {
        assertCommas("foo 1, bar 2^N^, 3 do\n:ok\nend")
    }

    fun testCommasThatAreValid() {
        for (source in listOf(
            "foo(bar 1, 2)",
            "foo 1, (bar 3, 4)",
            "x = foo 1, 2",
            "foo bar 1, 2",
            "foo a: bar 1, 2",
            "foo 1, a: bar 2, 3",
            "[1, foo 2]",
            "{foo bar: 1, baz: 2}",
            "[foo a: 1, b: 2]",
            "foo(bar 1, 2, 3)",
            "[(foo 1, 2)]",
            "foo 1, bar(2, 3)",
            "[foo(1), 2]",
            "x[foo 1]",
            "[foo bar 1]",
            "@foo bar 1, 2",
            "foo(bar 1, 2, baz 3)",
            "quote do: foo 1, 2",
            "def foo(x), do: bar 1, 2",
            "[bar a: baz 1, 2]",
            "foo(bar a: baz 1, 2)",
            "fn x when foo 1, 2 -> x end",
            "!foo 1, 2",
            "&foo 1, 2",
            "foo 1, 2 + bar 3",
            "foo 1 + bar 2, 3",
            "[foo(1, 2)]",
        )) {
            assertCommas(source)
        }
    }

    private fun assertCommas(marked: String) {
        val expected = mutableListOf<Triple<Int, String, String>>()
        val source = StringBuilder()
        val kinds = MARKER.findAll(marked).map { it.groupValues[1] }.toList()

        marked.split(MARKER).forEachIndexed { index, part ->
            source.append(part)
            kinds.getOrNull(index)?.let { kind ->
                expected.add(Triple(source.length, ",", if (kind == "C") INSIDE_CONTAINERS else IN_NESTED_CALLS))
            }
        }

        val text = source.toString()

        myFixture.enableInspections(NoParenthesesManyStrict::class.java)
        myFixture.configureByText(ElixirFileType.INSTANCE, text)

        val actual = myFixture
            .doHighlighting(HighlightSeverity.ERROR)
            .sortedBy { it.startOffset }
            .map { Triple(it.startOffset, text.substring(it.startOffset, it.endOffset), it.description) }

        assertEquals("errors in $text", expected, actual)
    }

    private companion object {
        const val INSIDE_CONTAINERS = "unexpected comma. Parentheses are required to solve ambiguity inside containers."
        const val IN_NESTED_CALLS = "unexpected comma. Parentheses are required to solve ambiguity in nested calls."
        val MARKER = Regex("\\^([CN])\\^")
    }
}
