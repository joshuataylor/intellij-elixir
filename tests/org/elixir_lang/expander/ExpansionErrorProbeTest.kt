package org.elixir_lang.expander

import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.psi.ElixirFile

/**
 * Each own case, compiled as a module body on the leg's Elixir, ends as the expander says: an error at its line and
 * for its reason, or a compile, with the same variables and env at every probe on the way, identity probes in patterns
 * included. Which route a case takes on a leg is the expander's answer there.
 */
class ExpansionErrorProbeTest : ProbeTestCase() {
    private val probes = ExpansionProbes(harness) { createPsiFile(getTestName(false), it) as ElixirFile }

    fun testOwnCases() {
        val level = legLevel()
        val cases = CASES.filter { it.from == null || level.elixir >= ElixirLanguageLevel.of(it.from).elixir }
        val expansions = cases.associate { it.code to probes.expand(it.code) }

        println(
            "unported on this leg: " +
                expansions.filterValues { it.outcome is Expansion.Unported }.keys.joinToString(" | ")
        )

        probes.assertMatchesElixir(expansions)
    }

    /** [code] is compared from [from], where Elixir's answer before it isn't one the expander gives. */
    private class Case(val code: String, val from: String? = null)

    private companion object {
        val CASES = listOf(
            // Expanded, on every leg or from where the expander's answer turns to Expanded
            Case("<<x::8>> = <<1>>"),
            Case("<<a, rest::binary>> = \"ab\""),
            Case("n = 8\n<<x::size(n)>> = <<1>>"),
            Case("<<n, x::size(n)>> = <<8, 1>>"),
            Case("x = 1\n<<^x>> = <<1>>"),
            Case("<<x::float>> = <<1.0::float>>"),
            Case("0.0 = 0.0"),
            Case("1 = 2"),
            Case("y = 1\n(x = y) = 1"),
            Case("{a = b, c} = {1, 2}"),
            Case("x = \"cd\"\n<<^x::binary, rest::binary>> = \"cdef\""),
            Case("<< <<x>>::binary >> = \"a\""),
            Case("<<\"foo#{\"bar\"}\", rest::binary>> = \"foobarbaz\""),
            Case("x = 1\ny = <<x::size(8), 2>>"),
            Case("_ = <<(x = 8)>>\nx"),
            Case("<<x::size(:a)>> = <<1>>"),
            Case("<<{x}>> = <<1>>"),
            Case("<<:a>>"),
            // Each version's error, or Expanded where a version has none
            Case("<<x>> = <<y>> = <<1>>"),
            Case("{<<x>> = <<y>>} = {<<1>>}"),
            Case("k = 1\n%{^k => <<x>>} = %{^k => <<y>>} = %{1 => <<1>>}"),
            Case("%{b: <<x>>,\na: <<y>>} = %{b: <<z>>,\na: <<w>>} = %{a: <<1>>, b: <<2>>}"),
            Case("x = \"a\"\n<<(<<^x::binary>>)::binary, y>> = \"ab\""),
            Case("%{<<1::integer>> => v} = %{<<1>> => 2}"),
            Case("n = 8\n<<y::size(^n)>> = <<1>>"),
            Case("{x = y, x = {:ok, y}} = {{:ok, 1}, {:ok, 1}}"),
            Case("{\nx = y,\nx = {:ok, y}\n} = {{:ok, 1}, {:ok, 1}}"),
            Case("(x = x) = 1"),
            Case("<<n::size(n)>> = <<8>>", from = "1.19.0-rc.1"),
            Case("<<x::size(a = 8)>> = <<1>>"),
            Case("_ = <<1::size(y = 8)>>\ny"),
            Case("<<[1]>> = <<1>>"),
            Case("<<x = y>> = <<1>>"),
            Case("<<x, [1]>> = <<1, 2>>"),
            Case("<<n, x::size(^n)>> = <<8, 1>>"),
            Case("a = 1\n%{{^a, 1} => v} = %{{1, 1} => 2}"),
            Case("n = 8\n%{<<1::size(n)>> => v} = %{<<1>> => 2}"),
            Case("%{1}", from = "1.17.0"),
            Case("x"),
            Case("{x = 1, x}"),
            Case("<<(x = 1), x>>"),
            Case("(1 :: 2)"),
            Case("(1 | 2)"),
            Case("__cursor__()"),
            // Errors on every leg
            Case("^y = 1"),
            Case("{^y, w} = {1, 2}"),
            Case("^1 = 1"),
            Case("x = 1\n^x"),
            Case("_"),
            Case("(x -> y)"),
            Case("unquote(1)(2)"),
            Case("1.foo()"),
            Case("\"a\".foo()"),
            Case("%{k: a, k: b} = %{k: 1}"),
            Case("%{\nk: a,\nk: b\n} = %{k: 1}"),
            Case("m = %{k: 1}\n%{m | k: v} = m"),
            Case("x = 1\n%{x => 1} = %{1 => 1}"),
            Case("%{<<x::8>> => 1} = %{<<1>> => 1}"),
            Case("<<x::size(n)>> = <<1>>"),
            Case("<<x::size(n), n>> = <<1, 8>>"),
            Case("{n, <<x::size(n)>>} = {8, <<1>>}"),
            Case("<<x::size(_)>> = <<1>>"),
            Case("<<x::binary, y>> = \"ab\""),
            Case("<<(<<x::binary>>), y>> = \"ab\""),
            Case("<<(<<1::1>>)::binary>>"),
            Case("<<x::integer-float>> = <<1>>"),
            Case("<<x::8-size(16)>> = <<1>>"),
            Case("<<1::binary>>"),
            Case("<<x::bits-unit(8)>> = <<1>>"),
            Case("<<x::\"a\">> = <<1>>"),
            Case("<<x::size(8)-unit(:a)>> = <<1>>"),
            Case("<<(<<1>>)::size(8)>>"),
            Case("<<\"foo\"::size(3)>>"),
            Case("<<x::utf8-size(8)>> = \"a\""),
            Case("<<x::utf8-signed>> = \"a\""),
            Case("<<x::float-size(10)>> = <<1>>"),
            Case("<<x::float-size(16)>> = <<0, 0>>"),
            Case("<<x::integer-unit(8)>> = <<1>>"),
            // Clauses: the error at the construct, or from 1.18 at the `->` clause, on every leg
            Case("case 1, []"),
            Case("cond []"),
            Case("receive []"),
            Case("try do\n1\nend"),
            Case("try []"),
            Case("case 1, 2"),
            Case("cond 1"),
            Case("receive 1"),
            Case("try 1"),
            Case("case 1, do: (1 -> 1), do: (2 -> 2)"),
            Case("try do: 1, do: 2"),
            Case("case 1, do: (1 -> 1), else: (2 -> 2)"),
            Case("try do: 1, foo: (a -> a)"),
            Case("case 1 do\n:ok\nend"),
            Case("try do\n1\nrescue\n:ok\nend"),
            Case("case 1 do\na, b -> a\nend"),
            Case("case 1 do\na, b when true -> a\nend"),
            Case("case 1 do\n1 -> 1\n2, 3 -> 2\nend"),
            Case("cond do\na, b -> a\nend"),
            Case("receive do\na, b -> a\nafter\n0 -> 1\nend"),
            Case("receive do\nafter\n0, 1 -> 1\nend"),
            Case("receive do\nafter\n0 -> 1\n1 -> 2\nend"),
            Case("try do\n1\nrescue\n_ -> 1\nelse\na, b -> 1\nend"),
            Case("try do\n1\ncatch\na, b, c -> 1\nend"),
            Case("try do\n1\ncatch\na, b, c when c -> 1\nend", from = "1.18.0-rc.0"),
            Case("try do\n1\nrescue\na, b -> 1\nend"),
            Case("try do\n1\nrescue\n1 -> 1\nend"),
            Case("x = 1\ntry do\n1\nrescue\ne in x -> e\nend"),
            Case("try do\n1\nrescue\n{a} in [:b] -> a\nend"),
            Case("try do\n1\nrescue\ne in [:a | [:b]] -> e\nend"),
            Case("cond do\ntrue -> 1\n_ -> 2\nend"),
            Case("__STACKTRACE__"),
            Case("try do\n1\nrescue\n_ -> 1\nend\n__STACKTRACE__"),
            Case("try do\n1\nrescue\n_ -> 1\nelse\n_ -> __STACKTRACE__\nend"),
            Case("try do\n1\nrescue\n_ -> 1\nafter\n__STACKTRACE__\nend"),
            Case("__STACKTRACE__ = 1"),
            Case("try do\n1\nrescue\n_ -> __STACKTRACE__ = 1\nend"),
            Case("fn a \\\\ 1 -> a end"),
            Case("fn\na -> a\na, b -> b\nend"),
            Case("fn\na -> a\nb, c -> case 1, 2\nend"),
            Case("case 1 do\n_ -> 1\nend = 1"),
            Case("cond do\ntrue -> 1\nend = 1"),
            Case("receive do\nafter\n0 -> 1\nend = 1"),
            Case("try do\n1\nafter\n2\nend = 1"),
            Case("fn -> 1 end = 1"),
            Case("x = 1\ncase x do\ny when case(y, do: (_ -> true)) -> y\nend"),
            Case("x = 1\ncase x do\ny when fn -> 1 end -> y\nend"),
            Case("try do\na = 1\nrescue\n_ -> 1\nelse\nb -> {b, a}\nend"),
        )
    }
}
