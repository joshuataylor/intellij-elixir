package org.elixir_lang.lowering

/**
 * Calls, variables, bracket access, `A.{B, C}` and `do` blocks. Each expected term is
 * `Code.string_to_quoted(code, columns: true, token_metadata: true)` on that version's Elixir.
 */
class CallsTest : LoweringTestCase() {
    fun testAVariableHasNoArguments() = assertLowers("[a, _b, c?, __MODULE__]", "[{:a, [line: 1, column: 2], nil}, {:_b, [line: 1, column: 5], nil}, {:c?, [line: 1, column: 9], nil}, {:__MODULE__, [line: 1, column: 13], nil}]")

    fun testAnIdentifierIsNormalisedFrom114() = assertLowers(
        "[µ, µ(), Foo.µ]",
        "1.13.4" to "[{:µ, [line: 1, column: 2], nil}, {:µ, [closing: [line: 1, column: 7], line: 1, column: 5], []}, {{:., [line: 1, column: 13], [{:__aliases__, [last: [line: 1, column: 10], line: 1, column: 10], [:Foo]}, :µ]}, [no_parens: true, line: 1, column: 14], []}]",
        "1.14.5" to "[{:μ, [line: 1, column: 2], nil}, {:μ, [closing: [line: 1, column: 7], line: 1, column: 5], []}, {{:., [line: 1, column: 13], [{:__aliases__, [last: [line: 1, column: 10], line: 1, column: 10], [:Foo]}, :μ]}, [no_parens: true, line: 1, column: 14], []}]",
    )

    fun testEllipsisIsANullaryCallFrom117() = assertLowers(
        "...",
        "1.11.4" to "{:..., [line: 1, column: 1], nil}",
        "1.17.3" to "{:..., [line: 1, column: 1], []}",
    )

    fun testAnExpressionHoldingEllipsisBeforeAnOperandIsLeftUnloweredFrom117() {
        val on1163 = listOf(
            "...()" to "{:..., [closing: [line: 1, column: 5], line: 1, column: 1], []}",
            "...(1)" to "{:..., [closing: [line: 1, column: 6], line: 1, column: 1], [1]}",
            "... 1" to "{:..., [line: 1, column: 1], [1]}",
            "...[0]" to "{{:., [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [{:..., [line: 1, column: 1], nil}, 0]}",
            "foo ... 1, 2" to "{:foo, [line: 1, column: 1], [{:..., [line: 1, column: 5], [1, 2]}]}",
            "... - 1" to "{:-, [line: 1, column: 5], [{:..., [line: 1, column: 1], nil}, 1]}",
            "...-1" to "{:-, [line: 1, column: 4], [{:..., [line: 1, column: 1], nil}, 1]}",
            "... -[1]" to "{:-, [line: 1, column: 5], [{:..., [line: 1, column: 1], nil}, [1]]}",
            "... - 1 == 2" to "{:==, [line: 1, column: 9], [{:-, [line: 1, column: 5], [{:..., [line: 1, column: 1], nil}, 1]}, 2]}",
            "a = ... - 1" to "{:=, [line: 1, column: 3], [{:a, [line: 1, column: 1], nil}, {:-, [line: 1, column: 9], [{:..., [line: 1, column: 5], nil}, 1]}]}",
            "a + ... - 1" to "{:-, [line: 1, column: 9], [{:+, [line: 1, column: 3], [{:a, [line: 1, column: 1], nil}, {:..., [line: 1, column: 5], nil}]}, 1]}",
            "-... - 1" to "{:-, [line: 1, column: 6], [{:-, [line: 1, column: 1], [{:..., [line: 1, column: 2], nil}]}, 1]}",
            "a == ... - 1 == 2" to "{:==, [line: 1, column: 14], [{:==, [line: 1, column: 3], [{:a, [line: 1, column: 1], nil}, {:-, [line: 1, column: 10], [{:..., [line: 1, column: 6], nil}, 1]}]}, 2]}",
            "...(1) - 2" to "{:-, [line: 1, column: 8], [{:..., [closing: [line: 1, column: 6], line: 1, column: 1], [1]}, 2]}",
            "...(1).foo" to "{{:., [line: 1, column: 7], [{:..., [closing: [line: 1, column: 6], line: 1, column: 1], [1]}, :foo]}, [no_parens: true, line: 1, column: 8], []}",
            "...[1][2]" to "{{:., [from_brackets: true, closing: [line: 1, column: 9], line: 1, column: 7], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 9], line: 1, column: 7], [{{:., [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [{:..., [line: 1, column: 1], nil}, 1]}, 2]}",
            "...\\\n-1" to "{:-, [line: 2, column: 1], [{:..., [line: 1, column: 1], nil}, 1]}",
            "... do\nend" to "{:..., [do: [line: 1, column: 5], end: [line: 2, column: 1], line: 1, column: 1], [[do: {:__block__, [], []}]]}",
        )

        for ((code, term) in on1163) {
            assertLowers(code, "1.16.3" to term, "1.17.3" to "{:__cursor__, [line: 1, column: 1], []}")
        }
    }

    /** `@...` needs the attribute family, so only the hole is pinned. */
    fun testAnAttributeOfEllipsisBeforeAnOperandIsLeftUnloweredFrom117() =
        assertLowers("@... - 1", "1.17.3" to "{:__cursor__, [line: 1, column: 1], []}")

    fun testOnlyTheExpressionHoldingEllipsisBeforeAnOperandIsLeftUnlowered() = assertLowers(
        "foo do\n... - 1\nend",
        "1.16.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:-, [line: 2, column: 5], [{:..., [line: 2, column: 1], nil}, 1]}]]}",
        "1.17.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:__cursor__, [end_of_expression: [newlines: 1, line: 2, column: 8], line: 2, column: 1], []}]]}",
    )

    fun testOnlyTheInterpolatedExpressionHoldingEllipsisBeforeAnOperandIsLeftUnlowered() = assertLowers(
        "\"#{... - 1}\"",
        "1.16.3" to "{:<<>>, [delimiter: \"\\\"\", line: 1, column: 1], [{:\"::\", [line: 1, column: 2], [{{:., [line: 1, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 11], line: 1, column: 2], [{:-, [line: 1, column: 8], [{:..., [line: 1, column: 4], nil}, 1]}]}, {:binary, [line: 1, column: 2], nil}]}]}",
        "1.17.3" to "{:<<>>, [delimiter: \"\\\"\", line: 1, column: 1], [{:\"::\", [line: 1, column: 2], [{{:., [line: 1, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 11], line: 1, column: 2], [{:__cursor__, [line: 1, column: 4], []}]}, {:binary, [line: 1, column: 2], nil}]}]}",
    )

    fun testOnlyTheElseBodyHoldingEllipsisBeforeAnOperandIsLeftUnlowered() = assertLowers(
        "if x do\n1\nelse\n... - 1\nend",
        "1.16.3" to "{:if, [do: [line: 1, column: 6], end: [line: 5, column: 1], line: 1, column: 1], [{:x, [line: 1, column: 4], nil}, [do: 1, else: {:-, [line: 4, column: 5], [{:..., [line: 4, column: 1], nil}, 1]}]]}",
        "1.17.3" to "{:if, [do: [line: 1, column: 6], end: [line: 5, column: 1], line: 1, column: 1], [{:x, [line: 1, column: 4], nil}, [do: 1, else: {:__cursor__, [end_of_expression: [newlines: 1, line: 4, column: 8], line: 4, column: 1], []}]]}",
    )

    fun testEllipsisBeforeASignInAMapIsAnAmbiguousCallUntil117() = assertLowers(
        "%{... -1}",
        "1.16.3" to "{:%{}, [closing: [line: 1, column: 9], line: 1, column: 2], [{:..., [ambiguous_op: nil, line: 1, column: 3], [{:-, [line: 1, column: 7], [1]}]}]}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 9], line: 1, column: 1], [{:..., [line: 1, column: 3], [{:-, [line: 1, column: 7], [1]}]}]}",
    )

    fun testEllipsisBeforeAnUnsignedOperandInAMapIsACallUntil117() = assertLowers(
        "%{... 1}",
        "1.16.3" to "{:%{}, [closing: [line: 1, column: 8], line: 1, column: 2], [{:..., [line: 1, column: 3], [1]}]}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 8], line: 1, column: 1], [{:..., [line: 1, column: 3], [1]}]}",
    )

    fun testEllipsisAloneInAMapIsANullaryCallFrom117() = assertLowers(
        "%{...}",
        "1.16.3" to "{:%{}, [closing: [line: 1, column: 6], line: 1, column: 2], [{:..., [line: 1, column: 3], nil}]}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 6], line: 1, column: 1], [{:..., [line: 1, column: 3], []}]}",
    )

    fun testEllipsisBeforeASlashOnTheNextLineIsAVariableFrom120() = assertLowers(
        "... \\\n/0",
        "1.16.3" to "{:/, [line: 2, column: 1], [{:..., [line: 1, column: 1], nil}, 0]}",
        "1.19.5" to "{:/, [line: 2, column: 1], [{:..., [line: 1, column: 1], []}, 0]}",
        "1.20.4" to "{:/, [line: 2, column: 1], [{:..., [line: 1, column: 1], nil}, 0]}",
    )

    fun testParenthesesHaveTheirClosing() = assertLowers("foo(1, a: 1)", "{:foo, [closing: [line: 1, column: 12], line: 1, column: 1], [1, [a: 1]]}")

    fun testNewlinesStraightAfterTheOpeningParenthesisAreCounted() = assertLowers("foo(\n\n1)", "{:foo, [newlines: 2, closing: [line: 3, column: 2], line: 1, column: 1], [1]}")

    fun testACommentAfterTheOpeningParenthesisEndsItsLine() = assertLowers("foo(# c\n1)", "{:foo, [newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [1]}")

    fun testRepeatedParenthesesKeepTheFirstCallsMetadataUntil119() = assertLowers(
        "foo(\n1)(2)",
        "1.11.4" to "{{:foo, [newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [1]}, [closing: [line: 2, column: 5], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [2]}",
        "1.19.5" to "{{:foo, [newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [1]}, [closing: [line: 2, column: 5], line: 1, column: 1], [2]}",
    )

    fun testAPlainCallTakesItsArgumentsAndKeywords() = assertLowers("foo 1, a: 1, b: 2", "{:foo, [line: 1, column: 1], [1, [a: 1, b: 2]]}")

    fun testAnInterpolatedKeywordKeyInAPlainCallIsConvertedToAnAtom() = assertLowers(
        "foo \"a#{1}\": 2",
        "1.11.4" to "{:foo, [line: 1, column: 1], [[{{{:., [line: 1, column: 5], [:erlang, :binary_to_atom]}, [format: :keyword, line: 1, column: 5], [{:<<>>, [line: 1, column: 5], [\"a\", {:\"::\", [line: 1, column: 7], [{{:., [line: 1, column: 7], [Kernel, :to_string]}, [closing: [line: 1, column: 10], line: 1, column: 7], [1]}, {:binary, [line: 1, column: 7], nil}]}]}, :utf8]}, 2}]]}",
        "1.16.3" to "{:foo, [line: 1, column: 1], [[{{{:., [line: 1, column: 5], [:erlang, :binary_to_atom]}, [format: :keyword, line: 1, column: 5], [{:<<>>, [line: 1, column: 5], [\"a\", {:\"::\", [line: 1, column: 7], [{{:., [line: 1, column: 7], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 10], line: 1, column: 7], [1]}, {:binary, [line: 1, column: 7], nil}]}]}, :utf8]}, 2}]]}",
        "1.18.4" to "{:foo, [line: 1, column: 1], [[{{{:., [line: 1, column: 5], [:erlang, :binary_to_atom]}, [delimiter: \"\\\"\", format: :keyword, line: 1, column: 5], [{:<<>>, [line: 1, column: 5], [\"a\", {:\"::\", [line: 1, column: 7], [{{:., [line: 1, column: 7], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 10], line: 1, column: 7], [1]}, {:binary, [line: 1, column: 7], nil}]}]}, :utf8]}, 2}]]}",
    )

    fun testAPlainCallInsideAPlainCallTakesTheRestOfTheArguments() = assertLowers("foo bar 1, 2", "{:foo, [line: 1, column: 1], [{:bar, [line: 1, column: 5], [1, 2]}]}")

    fun testAPlainCallInsideParenthesesTakesEveryArgument() = assertLowers("foo(bar 1, 2)", "{:foo, [closing: [line: 1, column: 13], line: 1, column: 1], [{:bar, [line: 1, column: 5], [1, 2]}]}")

    fun testARemoteCallIsAtItsNameFrom113() = assertLowers(
        "Foo.\nbar(1)",
        "1.11.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 2, column: 6], line: 1, column: 4], [1]}",
        "1.13.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 2, column: 6], line: 2, column: 1], [1]}",
    )

    fun testARemoteCallWithoutArgumentsHasNoParens() = assertLowers(
        "a.b.c",
        "1.12.3" to "{{:., [line: 1, column: 4], [{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [no_parens: true, line: 1, column: 2], []}, :c]}, [no_parens: true, line: 1, column: 4], []}",
        "1.13.4" to "{{:., [line: 1, column: 4], [{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [no_parens: true, line: 1, column: 3], []}, :c]}, [no_parens: true, line: 1, column: 5], []}",
    )

    fun testARemoteCallWithArgumentsHasNoNoParens() = assertLowers(
        "Foo.bar 1, 2",
        "1.12.3" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [line: 1, column: 4], [1, 2]}",
        "1.13.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [line: 1, column: 5], [1, 2]}",
    )

    fun testAQuotedRemoteNameIsUnescapedAndCarriesItsDelimiterFrom118() = assertLowers(
        "Foo.\"a\\x62\\\"c\"()",
        "1.11.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :\"a\\\\x62\\\"c\"]}, [closing: [line: 1, column: 16], line: 1, column: 4], []}",
        "1.13.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a\\\\x62\\\"c\"]}, [closing: [line: 1, column: 16], line: 1, column: 5], []}",
        "1.18.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"ab\\\"c\"]}, [closing: [line: 1, column: 16], delimiter: \"\\\"\", line: 1, column: 5], []}",
    )

    fun testALineContinuationInAQuotedRemoteNameIsKeptFrom112Until118() = assertLowers(
        "Foo.\"a\\\nb\"()",
        "1.11.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :ab]}, [closing: [line: 2, column: 4], line: 1, column: 4], []}",
        "1.12.3" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :\"a\\\\\\nb\"]}, [closing: [line: 2, column: 4], line: 1, column: 4], []}",
        "1.18.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :ab]}, [closing: [line: 2, column: 4], delimiter: \"\\\"\", line: 1, column: 5], []}",
    )

    fun testASingleQuotedRemoteNameHasASingleQuoteDelimiterFrom118() = assertLowers(
        "Foo.'a b'",
        "1.17.3" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [no_parens: true, line: 1, column: 5], []}",
        "1.18.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [no_parens: true, delimiter: \"'\", line: 1, column: 5], []}",
    )

    fun testAReservedWordOrOperatorIsARemoteName() = assertLowers(
        "[Foo.and(1), Foo.if, Foo.+(1), Foo.nil, Foo.do]",
        "1.12.3" to "[{{:., [line: 1, column: 5], [{:__aliases__, [line: 1, column: 2], [:Foo]}, :and]}, [closing: [line: 1, column: 11], line: 1, column: 5], [1]}, {{:., [line: 1, column: 17], [{:__aliases__, [line: 1, column: 14], [:Foo]}, :if]}, [no_parens: true, line: 1, column: 17], []}, {{:., [line: 1, column: 25], [{:__aliases__, [line: 1, column: 22], [:Foo]}, :+]}, [closing: [line: 1, column: 29], line: 1, column: 25], [1]}, {{:., [line: 1, column: 35], [{:__aliases__, [line: 1, column: 32], [:Foo]}, nil]}, [no_parens: true, line: 1, column: 35], []}, {{:., [line: 1, column: 44], [{:__aliases__, [line: 1, column: 41], [:Foo]}, :do]}, [no_parens: true, line: 1, column: 44], []}]",
        "1.13.4" to "[{{:., [line: 1, column: 5], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, :and]}, [closing: [line: 1, column: 11], line: 1, column: 6], [1]}, {{:., [line: 1, column: 17], [{:__aliases__, [last: [line: 1, column: 14], line: 1, column: 14], [:Foo]}, :if]}, [no_parens: true, line: 1, column: 18], []}, {{:., [line: 1, column: 25], [{:__aliases__, [last: [line: 1, column: 22], line: 1, column: 22], [:Foo]}, :+]}, [closing: [line: 1, column: 29], line: 1, column: 26], [1]}, {{:., [line: 1, column: 35], [{:__aliases__, [last: [line: 1, column: 32], line: 1, column: 32], [:Foo]}, nil]}, [no_parens: true, line: 1, column: 36], []}, {{:., [line: 1, column: 44], [{:__aliases__, [last: [line: 1, column: 41], line: 1, column: 41], [:Foo]}, :do]}, [no_parens: true, line: 1, column: 45], []}]",
    )

    fun testAnAnonymousFunctionCallCalledAgainKeepsOnlyItsLocationFrom119() = assertLowers(
        "a.(1)(2)",
        "1.11.4" to "{{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}]}, [closing: [line: 1, column: 5], line: 1, column: 2], [1]}, [closing: [line: 1, column: 8], closing: [line: 1, column: 5], line: 1, column: 2], [2]}",
        "1.19.5" to "{{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}]}, [closing: [line: 1, column: 5], line: 1, column: 2], [1]}, [closing: [line: 1, column: 8], line: 1, column: 2], [2]}",
    )

    fun testARemoteNameCalledAsAnAnonymousFunctionTakesItsNoParensCall() = assertLowers(
        "Foo.bar.()",
        "1.12.3" to "{{:., [line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 4], []}]}, [closing: [line: 1, column: 10], line: 1, column: 8], []}",
        "1.13.4" to "{{:., [line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 5], []}]}, [closing: [line: 1, column: 10], line: 1, column: 8], []}",
    )

    fun testBracketAccessHasItsClosingFrom112() = assertLowers(
        "a[\n1]",
        "1.11.4" to "{{:., [line: 1, column: 2], [Access, :get]}, [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}",
        "1.12.3" to "{{:., [newlines: 1, closing: [line: 2, column: 2], line: 1, column: 2], [Access, :get]}, [newlines: 1, closing: [line: 2, column: 2], line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}",
        "1.16.3" to "{{:., [from_brackets: true, newlines: 1, closing: [line: 2, column: 2], line: 1, column: 2], [Access, :get]}, [from_brackets: true, newlines: 1, closing: [line: 2, column: 2], line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}",
    )

    fun testBracketAccessOnAnExpressionHasFromBracketsFrom115() = assertLowers(
        "[1][0]",
        "1.11.4" to "{{:., [line: 1, column: 4], [Access, :get]}, [line: 1, column: 4], [[1], 0]}",
        "1.12.3" to "{{:., [closing: [line: 1, column: 6], line: 1, column: 4], [Access, :get]}, [closing: [line: 1, column: 6], line: 1, column: 4], [[1], 0]}",
        "1.15.8" to "{{:., [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 6], line: 1, column: 4], [[1], 0]}",
    )

    fun testBracketAccessOnAnIdentifierHasFromBracketsLaterThanOnAnExpression() = assertLowers(
        "a[1][2]",
        "1.11.4" to "{{:., [line: 1, column: 5], [Access, :get]}, [line: 1, column: 5], [{{:., [line: 1, column: 2], [Access, :get]}, [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}, 2]}",
        "1.12.3" to "{{:., [closing: [line: 1, column: 7], line: 1, column: 5], [Access, :get]}, [closing: [line: 1, column: 7], line: 1, column: 5], [{{:., [closing: [line: 1, column: 4], line: 1, column: 2], [Access, :get]}, [closing: [line: 1, column: 4], line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}, 2]}",
        "1.15.8" to "{{:., [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [{{:., [closing: [line: 1, column: 4], line: 1, column: 2], [Access, :get]}, [closing: [line: 1, column: 4], line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}, 2]}",
        "1.16.3" to "{{:., [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [{{:., [from_brackets: true, closing: [line: 1, column: 4], line: 1, column: 2], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 4], line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, 1]}, 2]}",
    )

    fun testBracketAccessOnARemoteNameTakesItsNoParensCall() = assertLowers(
        "Foo.bar[1]",
        "1.11.4" to "{{:., [line: 1, column: 8], [Access, :get]}, [line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 4], []}, 1]}",
        "1.12.3" to "{{:., [closing: [line: 1, column: 10], line: 1, column: 8], [Access, :get]}, [closing: [line: 1, column: 10], line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 4], []}, 1]}",
        "1.13.4" to "{{:., [closing: [line: 1, column: 10], line: 1, column: 8], [Access, :get]}, [closing: [line: 1, column: 10], line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 5], []}, 1]}",
        "1.16.3" to "{{:., [from_brackets: true, closing: [line: 1, column: 10], line: 1, column: 8], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 10], line: 1, column: 8], [{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 5], []}, 1]}",
    )

    fun testMultipleAliasesAreACallOfBracesOnTheQualifier() = assertLowers(
        "A.{B, C}",
        "1.12.3" to "{{:., [line: 1, column: 2], [{:__aliases__, [line: 1, column: 1], [:A]}, :{}]}, [closing: [line: 1, column: 8], line: 1, column: 2], [{:__aliases__, [line: 1, column: 4], [:B]}, {:__aliases__, [line: 1, column: 7], [:C]}]}",
        "1.13.4" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [closing: [line: 1, column: 8], line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 4], line: 1, column: 4], [:B]}, {:__aliases__, [last: [line: 1, column: 7], line: 1, column: 7], [:C]}]}",
    )

    fun testNewlinesStraightAfterTheOpeningBraceAreCounted() = assertLowers(
        "A.{\nB\n}",
        "1.12.3" to "{{:., [line: 1, column: 2], [{:__aliases__, [line: 1, column: 1], [:A]}, :{}]}, [newlines: 1, closing: [line: 3, column: 1], line: 1, column: 2], [{:__aliases__, [line: 2, column: 1], [:B]}]}",
        "1.13.4" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [newlines: 1, closing: [line: 3, column: 1], line: 1, column: 2], [{:__aliases__, [last: [line: 2, column: 1], line: 2, column: 1], [:B]}]}",
    )

    fun testEmptyMultipleAliasesHaveTheirNewlinesFrom119() = assertLowers(
        "A.{\n}",
        "1.18.4" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [line: 1, column: 2], []}",
        "1.19.5" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [newlines: 1, closing: [line: 2, column: 1], line: 1, column: 2], []}",
    )

    fun testEmptyMultipleAliasesHaveTheirClosingFrom119() = assertLowers(
        "A.{}",
        "1.18.4" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [line: 1, column: 2], []}",
        "1.19.5" to "{{:., [line: 1, column: 2], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:A]}, :{}]}, [closing: [line: 1, column: 4], line: 1, column: 2], []}",
    )

    fun testADoBlockIsTheLastArgument() = assertLowers("foo 1 do\n2\nend", "{:foo, [do: [line: 1, column: 7], end: [line: 3, column: 1], line: 1, column: 1], [1, [do: 2]]}")

    fun testAnEmptyDoBlockIsAtItsDoFrom120() = assertLowers(
        "a do\nend",
        "1.11.4" to "{:a, [do: [line: 1, column: 3], end: [line: 2, column: 1], line: 1, column: 1], [[do: {:__block__, [], []}]]}",
        "1.20.4" to "{:a, [do: [line: 1, column: 3], end: [line: 2, column: 1], line: 1, column: 1], [[do: {:__block__, [line: 1, column: 3], []}]]}",
    )

    fun testEveryBlockOfADoBlockIsAKeyword() = assertLowers("try do\n1\nrescue\n2\ncatch\n3\nelse\n4\nafter\n5\nend", "{:try, [do: [line: 1, column: 5], end: [line: 11, column: 1], line: 1, column: 1], [[do: 1, rescue: 2, catch: 3, else: 4, after: 5]]}")

    fun testOnlyTheDoBlockIsAtItsDo() = assertLowers(
        "foo do\nelse\nend",
        "1.11.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:__block__, [], []}, else: {:__block__, [], []}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:__block__, [line: 1, column: 5], []}, else: {:__block__, [], []}]]}",
    )

    fun testDoAndEndComeBeforeTheParenthesesMetadata() = assertLowers(
        "foo(\n1) do\nend",
        "1.11.4" to "{:foo, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [1, [do: {:__block__, [], []}]]}",
        "1.20.4" to "{:foo, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 1], [1, [do: {:__block__, [line: 2, column: 4], []}]]}",
    )

    fun testADoBlockOfSeveralExpressionsIsABlockAtItsDoFrom120() = assertLowers(
        "foo do 1; 2 end",
        "1.11.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 1, column: 13], line: 1, column: 1], [[do: {:__block__, [], [1, 2]}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 1, column: 13], line: 1, column: 1], [[do: {:__block__, [line: 1, column: 5], [1, 2]}]]}",
    )

    fun testADoBlockInsideADoBlockHasItsEndOfExpressionAfterItsEndFrom117() = assertLowers(
        "foo do\nbar do\nend\nend",
        "1.16.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 4, column: 1], line: 1, column: 1], [[do: {:bar, [do: [line: 2, column: 5], end: [line: 3, column: 1], line: 2, column: 1], [[do: {:__block__, [], []}]]}]]}",
        "1.17.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 4, column: 1], line: 1, column: 1], [[do: {:bar, [end_of_expression: [newlines: 1, line: 3, column: 4], do: [line: 2, column: 5], end: [line: 3, column: 1], line: 2, column: 1], [[do: {:__block__, [], []}]]}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 4, column: 1], line: 1, column: 1], [[do: {:bar, [parens: [line: 1, column: 5], end_of_expression: [newlines: 1, line: 3, column: 4], do: [line: 2, column: 5], end: [line: 3, column: 1], line: 2, column: 1], [[do: {:__block__, [line: 2, column: 5], []}]]}]]}",
    )

    fun testDoAndEndComeBeforeARemoteCallsParenthesesMetadata() = assertLowers(
        "Foo.\"a b\"(\n1) do\nend",
        "1.12.3" to "{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :\"a b\"]}, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 4], [1, [do: {:__block__, [], []}]]}",
        "1.13.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 5], [1, [do: {:__block__, [], []}]]}",
        "1.17.3" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], line: 1, column: 5], [1, [do: {:__block__, [], []}]]}",
        "1.18.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], delimiter: \"\\\"\", line: 1, column: 5], [1, [do: {:__block__, [], []}]]}",
        "1.20.4" to "{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :\"a b\"]}, [do: [line: 2, column: 4], end: [line: 3, column: 1], newlines: 1, closing: [line: 2, column: 2], delimiter: \"\\\"\", line: 1, column: 5], [1, [do: {:__block__, [line: 2, column: 4], []}]]}",
    )

    fun testADoBlockBelongsToTheLastParentheses() = assertLowers(
        "foo(1)(2) do\nend",
        "1.11.4" to "{{:foo, [closing: [line: 1, column: 6], line: 1, column: 1], [1]}, [do: [line: 1, column: 11], end: [line: 2, column: 1], closing: [line: 1, column: 9], closing: [line: 1, column: 6], line: 1, column: 1], [2, [do: {:__block__, [], []}]]}",
        "1.19.5" to "{{:foo, [closing: [line: 1, column: 6], line: 1, column: 1], [1]}, [do: [line: 1, column: 11], end: [line: 2, column: 1], closing: [line: 1, column: 9], line: 1, column: 1], [2, [do: {:__block__, [], []}]]}",
        "1.20.4" to "{{:foo, [closing: [line: 1, column: 6], line: 1, column: 1], [1]}, [do: [line: 1, column: 11], end: [line: 2, column: 1], closing: [line: 1, column: 9], line: 1, column: 1], [2, [do: {:__block__, [line: 1, column: 11], []}]]}",
    )

    fun testParenthesesAroundARemoteCallAddParensFrom118() = assertLowers(
        "(Foo.bar)",
        "1.17.3" to "{{:., [line: 1, column: 5], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, :bar]}, [no_parens: true, line: 1, column: 6], []}",
        "1.18.4" to "{{:., [line: 1, column: 5], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, :bar]}, [parens: [line: 1, column: 1, closing: [line: 1, column: 9]], no_parens: true, line: 1, column: 6], []}",
        "1.20.4" to "{{:., [line: 1, column: 5], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, :bar]}, [parens: [closing: [line: 1, column: 9], line: 1, column: 1], no_parens: true, line: 1, column: 6], []}",
    )

    fun testARemoteCallWithParenthesesIsAQualifier() = assertLowers(
        "a.b(1).c",
        "1.12.3" to "{{:., [line: 1, column: 7], [{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [closing: [line: 1, column: 6], line: 1, column: 2], [1]}, :c]}, [no_parens: true, line: 1, column: 7], []}",
        "1.13.4" to "{{:., [line: 1, column: 7], [{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [closing: [line: 1, column: 6], line: 1, column: 3], [1]}, :c]}, [no_parens: true, line: 1, column: 8], []}",
    )

    fun testADotStartingALineContinuesTheExpressionBefore() = assertLowers(
        "foo\n.bar",
        "1.12.3" to "{{:., [line: 2, column: 1], [{:foo, [line: 1, column: 1], nil}, :bar]}, [no_parens: true, line: 2, column: 1], []}",
        "1.13.4" to "{{:., [line: 2, column: 1], [{:foo, [line: 1, column: 1], nil}, :bar]}, [no_parens: true, line: 2, column: 2], []}",
    )

    fun testADoBlockBelongsToTheOutermostCall() = assertLowers(
        "foo bar do\nend",
        "1.11.4" to "{:foo, [do: [line: 1, column: 9], end: [line: 2, column: 1], line: 1, column: 1], [{:bar, [line: 1, column: 5], nil}, [do: {:__block__, [], []}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 9], end: [line: 2, column: 1], line: 1, column: 1], [{:bar, [line: 1, column: 5], nil}, [do: {:__block__, [line: 1, column: 9], []}]]}",
    )

    fun testRepeatedParenthesesOnARemoteCallKeepOnlyItsLocationFrom119() = assertLowers(
        "Foo.bar()()",
        "1.12.3" to "{{{:., [line: 1, column: 4], [{:__aliases__, [line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 1, column: 9], line: 1, column: 4], []}, [closing: [line: 1, column: 11], closing: [line: 1, column: 9], line: 1, column: 4], []}",
        "1.13.4" to "{{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 1, column: 9], line: 1, column: 5], []}, [closing: [line: 1, column: 11], closing: [line: 1, column: 9], line: 1, column: 5], []}",
        "1.18.4" to "{{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 1, column: 9], line: 1, column: 5], []}, [closing: [line: 1, column: 11], closing: [line: 1, column: 9], line: 1, column: 5], []}",
        "1.19.5" to "{{{:., [line: 1, column: 4], [{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}, :bar]}, [closing: [line: 1, column: 9], line: 1, column: 5], []}, [closing: [line: 1, column: 11], line: 1, column: 5], []}",
    )

    fun testDoAndElseKeywordsAreAnOrdinaryKeywordList() = assertLowers("if true, do: 1, else: 2", "{:if, [line: 1, column: 1], [true, [do: 1, else: 2]]}")

    fun testANameBeforeAVariableIsACall() = assertLowers("a b", "{:a, [line: 1, column: 1], [{:b, [line: 1, column: 3], nil}]}")

    fun testADoBlockFollowsTheKeywordsAsASeparateList() = assertLowers(
        "foo bar: 1 do\nend",
        "1.11.4" to "{:foo, [do: [line: 1, column: 12], end: [line: 2, column: 1], line: 1, column: 1], [[bar: 1], [do: {:__block__, [], []}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 12], end: [line: 2, column: 1], line: 1, column: 1], [[bar: 1], [do: {:__block__, [line: 1, column: 12], []}]]}",
    )

    fun testASigilEndingADoBlockHasItsEndOfExpressionFrom117() = assertLowers(
        "foo do\n~S(a)\nend",
        "1.16.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:sigil_S, [delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}]]}",
        "1.17.3" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:sigil_S, [end_of_expression: [newlines: 1, line: 2, column: 6], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}]]}",
        "1.20.4" to "{:foo, [do: [line: 1, column: 5], end: [line: 3, column: 1], line: 1, column: 1], [[do: {:sigil_S, [parens: [line: 1, column: 5], end_of_expression: [newlines: 1, line: 2, column: 6], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}]]}",
    )

    fun testARemoteCallIsNeverAmbiguous() = assertLowers(
        "a.b -1",
        "1.11.4" to "{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [line: 1, column: 2], [{:-, [line: 1, column: 5], [1]}]}",
        "1.13.4" to "{{:., [line: 1, column: 2], [{:a, [line: 1, column: 1], nil}, :b]}, [line: 1, column: 3], [{:-, [line: 1, column: 5], [1]}]}",
    )

    fun testAUnaryPlusOrMinusOpeningTheOnlyArgumentMakesTheCallAmbiguous() = assertCallMeta(
        "foo -1" to "[ambiguous_op: nil, line: 1, column: 1]",
        "foo +1" to "[ambiguous_op: nil, line: 1, column: 1]",
        "foo -1, 2" to "[line: 1, column: 1]",
        "foo -1 do\nend" to "[do: [line: 1, column: 8], end: [line: 2, column: 1], line: 1, column: 1]",
    )

    fun testASpacedSignBeforeAContainerMakesAnAmbiguousCallFrom117() = assertLowers(
        "foo -[1]",
        "1.16.3" to "{:-, [line: 1, column: 5], [{:foo, [line: 1, column: 1], nil}, [1]]}",
        "1.17.3" to "{:foo, [ambiguous_op: nil, line: 1, column: 1], [{:-, [line: 1, column: 5], [[1]]}]}",
    )

    fun testBracketAccessOnAnAttributeHasFromBracketsFrom1162() = assertLowersMeta(
        "@Foo[1]",
        "1.11.4" to "[line: 1, column: 5]",
        "1.12.3" to "[closing: [line: 1, column: 7], line: 1, column: 5]",
        "1.16.1" to "[closing: [line: 1, column: 7], line: 1, column: 5]",
        "1.16.2" to "[from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5]",
    )

    fun testACalleeOrContainerSpansOnlyItsOwnText() {
        assertSpan("foo(1)(2)", "foo(1)") { (it as ElixirAst.Call).callee }
        assertSpan("Foo.bar(1)", "Foo.bar") { (it as ElixirAst.Call).callee }
        assertSpan("Foo.bar 1", "Foo.bar") { (it as ElixirAst.Call).callee }
        assertSpan("a.(1)", "a.") { (it as ElixirAst.Call).callee }
        assertSpan("Foo.bar[1]", "Foo.bar") { (it as ElixirAst.Call).arguments!!.first() }
    }

    private fun assertSpan(code: String, expected: String, node: (ElixirAst) -> ElixirAst) =
        assertEquals(code, expected, node(lower(code)).meta.origin.substring(code))

    /** Each code's outermost call's own metadata on the oldest and newest versions, whether or not its arguments lower. */
    private fun assertCallMeta(vararg expected: Pair<String, String>) =
        expected.forEach { (code, meta) -> assertLowersMeta(code, OLDEST to meta, NEWEST to meta) }

    private fun assertLowersMeta(code: String, vararg expected: Pair<String, String>) =
        assertEquals(
            expected.joinToString("\n") { (version, meta) -> "$code on $version: $meta" },
            expected.joinToString("\n") { (version, _) ->
                "$code on $version: " + inspect(lower(code, version).meta.toOtp(COLUMNS_AND_TOKEN_METADATA))
            }
        )
}
