package org.elixir_lang.lowering

import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangString
import com.ericsson.otp.erlang.OtpErlangTuple

/**
 * `fn`, `->` and its signatures. Each expected term is `Code.string_to_quoted(code, columns: true, token_metadata: true)`
 * on that version's Elixir.
 */
class ClausesTest : LoweringTestCase() {
    fun testAClauseWithoutASignatureHasNoArguments() = assertLowers(
        "fn -> 1 end",
        "{:fn, [closing: [line: 1, column: 9], line: 1, column: 1], [{:->, [line: 1, column: 4], [[], 1]}]}"
    )

    fun testANoParenthesesSignatureIsItsArguments() = assertLowers(
        "fn 1, 2 -> 3 end",
        "{:fn, [closing: [line: 1, column: 14], line: 1, column: 1], [{:->, [line: 1, column: 9], [[1, 2], 3]}]}"
    )

    fun testASignatureIsACharListWhenEveryArgumentIsAByte() {
        val signature = clause(lower("fn (1) -> 2 end").toOtp()).elementAt(0)

        assertEquals(OtpErlangString("\u0001"), signature)
    }

    fun testOneParenthesizedArgumentIsAParenthesizedExpression() = assertLowers(
        "fn ({1, 2, 3}) -> 1 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 21], line: 1, column: 1], [{:->, [line: 1, column: 16], [[{:{}, [closing: [line: 1, column: 13], line: 1, column: 5], [1, 2, 3]}], 1]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 21], line: 1, column: 1], [{:->, [line: 1, column: 16], [[{:{}, [parens: [line: 1, column: 4, closing: [line: 1, column: 14]], closing: [line: 1, column: 13], line: 1, column: 5], [1, 2, 3]}], 1]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 21], line: 1, column: 1], [{:->, [line: 1, column: 16], [[{:{}, [parens: [closing: [line: 1, column: 14], line: 1, column: 4], closing: [line: 1, column: 13], line: 1, column: 5], [1, 2, 3]}], 1]}]}",
    )

    fun testOneParenthesizedLiteralArgumentHasNoParens() = assertLowers(
        "fn (1) -> 2 end",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 13], line: 1, column: 1], [{:->, [line: 1, column: 8], [[1], 2]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 13], line: 1, column: 1], [{:->, [line: 1, column: 8], [[1], 2]}]}",
    )

    fun testParenthesizedArgumentsAddParensToTheArrowFrom118() = assertLowers(
        "fn (1, 2) -> 3 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 16], line: 1, column: 1], [{:->, [line: 1, column: 11], [[1, 2], 3]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 16], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 9]], line: 1, column: 11], [[1, 2], 3]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 16], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 9], line: 1, column: 4], line: 1, column: 11], [[1, 2], 3]}]}",
    )

    fun testEmptyParenthesesAddParensToTheArrowFrom118() = assertLowers(
        "fn () -> 1 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 12], line: 1, column: 1], [{:->, [line: 1, column: 7], [[], 1]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 12], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 5]], line: 1, column: 7], [[], 1]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 12], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 5], line: 1, column: 4], line: 1, column: 7], [[], 1]}]}",
    )

    fun testParenthesizedKeywordsAddParensToTheArrowFrom118() = assertLowers(
        "fn (1, a: 1) -> 2 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [line: 1, column: 14], [[1, [a: 1]], 2]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 12]], line: 1, column: 14], [[1, [a: 1]], 2]}]}",
    )

    fun testOnlyParenthesizedKeywordsAddParensToTheArrowFrom118() = assertLowers(
        "fn (a: 1) -> 2 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 16], line: 1, column: 1], [{:->, [line: 1, column: 11], [[[a: 1]], 2]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 16], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 9]], line: 1, column: 11], [[[a: 1]], 2]}]}",
    )

    fun testParenthesizedArgumentsBeforeAGuardAreTheGuardsLeadingArguments() = assertLowers(
        "fn (1, 2) when 3 -> 4 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 23], line: 1, column: 1], [{:->, [line: 1, column: 18], [[{:when, [line: 1, column: 11], [1, 2, 3]}], 4]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 23], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 9]], line: 1, column: 18], [[{:when, [line: 1, column: 11], [1, 2, 3]}], 4]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 23], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 9], line: 1, column: 4], line: 1, column: 18], [[{:when, [line: 1, column: 11], [1, 2, 3]}], 4]}]}",
    )

    fun testEmptyParenthesesBeforeAGuardLeaveTheGuardAlone() = assertLowers(
        "fn () when 1 -> 2 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [line: 1, column: 14], [[{:when, [line: 1, column: 7], [1]}], 2]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 5], line: 1, column: 4], line: 1, column: 14], [[{:when, [line: 1, column: 7], [1]}], 2]}]}",
    )

    fun testOneParenthesizedArgumentBeforeAGuardIsAParenthesizedExpression() = assertLowers(
        "fn ({1, 2, 3}) when 1 -> 2 end",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 28], line: 1, column: 1], [{:->, [line: 1, column: 23], [[{:when, [line: 1, column: 16], [{:{}, [closing: [line: 1, column: 13], line: 1, column: 5], [1, 2, 3]}, 1]}], 2]}]}",
        "1.20.4" to "{:fn, [closing: [line: 1, column: 28], line: 1, column: 1], [{:->, [line: 1, column: 23], [[{:when, [line: 1, column: 16], [{:{}, [parens: [closing: [line: 1, column: 14], line: 1, column: 4], closing: [line: 1, column: 13], line: 1, column: 5], [1, 2, 3]}, 1]}], 2]}]}",
    )

    fun testANoParenthesesGuardTakesEveryArgument() = assertLowers(
        "fn 1, 2 when 3 -> 4 end",
        "{:fn, [closing: [line: 1, column: 21], line: 1, column: 1], [{:->, [line: 1, column: 16], [[{:when, [line: 1, column: 9], [1, 2, 3]}], 4]}]}"
    )

    fun testANoParenthesesGuardOnOneArgument() = assertLowers(
        "fn 1 when 2 -> 3 end",
        "{:fn, [closing: [line: 1, column: 18], line: 1, column: 1], [{:->, [line: 1, column: 13], [[{:when, [line: 1, column: 6], [1, 2]}], 3]}]}"
    )

    fun testNoParenthesesKeywordsAreTheLastArgument() = assertLowers(
        "fn 1, a: 1 -> 2 end",
        "{:fn, [closing: [line: 1, column: 17], line: 1, column: 1], [{:->, [line: 1, column: 12], [[1, [a: 1]], 2]}]}"
    )

    fun testAnEmptyBodyIsNil() = assertLowers(
        "fn 1 -> end",
        "{:fn, [closing: [line: 1, column: 9], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], nil]}]}"
    )

    fun testAClauseWithNeitherSignatureNorBody() = assertLowers(
        "fn -> end",
        "{:fn, [closing: [line: 1, column: 7], line: 1, column: 1], [{:->, [line: 1, column: 4], [[], nil]}]}"
    )

    fun testNewlinesAfterFnAreItsNewlines() = assertLowers(
        "fn\n\n1 -> 2 end",
        "{:fn, [newlines: 2, closing: [line: 3, column: 8], line: 1, column: 1], [{:->, [line: 3, column: 3], [[1], 2]}]}"
    )

    fun testACommentAfterFnLeavesOneNewline() = assertLowers(
        "fn # c\n 1 -> 2 end",
        "{:fn, [newlines: 1, closing: [line: 2, column: 9], line: 1, column: 1], [{:->, [line: 2, column: 4], [[1], 2]}]}"
    )

    fun testASemicolonAfterFnIsNoNewline() = assertLowers(
        "fn; 1 -> 2 end",
        "{:fn, [closing: [line: 1, column: 12], line: 1, column: 1], [{:->, [line: 1, column: 7], [[1], 2]}]}"
    )

    fun testNewlinesAfterASemicolonAfterFnAreItsNewlines() = assertLowers(
        "fn;\n1 -> 2 end",
        "{:fn, [newlines: 1, closing: [line: 2, column: 8], line: 1, column: 1], [{:->, [line: 2, column: 3], [[1], 2]}]}"
    )

    fun testNewlinesBeforeAnArrowWithoutASignatureAreTheArrowsNotFns() = assertLowers(
        "fn\n -> end",
        "{:fn, [closing: [line: 2, column: 5], line: 1, column: 1], [{:->, [newlines: 1, line: 2, column: 2], [[], nil]}]}"
    )

    fun testNewlinesAfterASemicolonBeforeAnArrowAreBothFnsAndTheArrows() = assertLowers(
        "fn; # c\n -> end",
        "{:fn, [newlines: 1, closing: [line: 2, column: 5], line: 1, column: 1], [{:->, [newlines: 1, line: 2, column: 2], [[], nil]}]}"
    )

    fun testASemicolonBeforeAnArrowIsNoNewline() = assertLowers(
        "fn; -> end",
        "{:fn, [closing: [line: 1, column: 8], line: 1, column: 1], [{:->, [line: 1, column: 5], [[], nil]}]}"
    )

    fun testNewlinesAfterTheGuardOfOneParenthesizedArgumentAreTheGuards() = assertLowers(
        "fn (1) when\n 2 -> 3 end",
        "{:fn, [closing: [line: 2, column: 9], line: 1, column: 1], [{:->, [line: 2, column: 4], [[{:when, [newlines: 1, line: 1, column: 8], [1, 2]}], 3]}]}"
    )

    fun testNewlinesAfterTheArrowAreItsNewlines() = assertLowers(
        "fn 1 ->\n\n {1, 2, 3}\nend",
        "1.16.3" to "{:fn, [closing: [line: 4, column: 1], line: 1, column: 1], [{:->, [newlines: 2, line: 1, column: 6], [[1], {:{}, [closing: [line: 3, column: 10], line: 3, column: 2], [1, 2, 3]}]}]}",
        "1.17.3" to "{:fn, [closing: [line: 4, column: 1], line: 1, column: 1], [{:->, [newlines: 2, line: 1, column: 6], [[1], {:{}, [end_of_expression: [newlines: 1, line: 3, column: 11], closing: [line: 3, column: 10], line: 3, column: 2], [1, 2, 3]}]}]}",
    )

    fun testACommentAfterTheArrowLeavesOneNewline() = assertLowers(
        "fn 1 -> # c\n 2 end",
        "{:fn, [closing: [line: 2, column: 4], line: 1, column: 1], [{:->, [newlines: 1, line: 1, column: 6], [[1], 2]}]}"
    )

    fun testParenthesesAndNewlinesOnOneArrow() = assertLowers(
        "fn (1, 2) ->\n3 end",
        "1.17.3" to "{:fn, [closing: [line: 2, column: 3], line: 1, column: 1], [{:->, [newlines: 1, line: 1, column: 11], [[1, 2], 3]}]}",
        "1.20.4" to "{:fn, [closing: [line: 2, column: 3], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 9], line: 1, column: 4], newlines: 1, line: 1, column: 11], [[1, 2], 3]}]}",
    )

    fun testTheEndOfExpressionAfterALiteralBodyIsTheArrowsUntil117() = assertLowers(
        "fn 1 -> 2; 3 -> 4 end",
        "1.11.4" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 0, line: 1, column: 10], line: 1, column: 6], [[1], 2]}, {:->, [line: 1, column: 14], [[3], 4]}]}",
        "1.16.3" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 0, line: 1, column: 10], line: 1, column: 6], [[1], 2]}, {:->, [line: 1, column: 14], [[3], 4]}]}",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 19], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], 2]}, {:->, [line: 1, column: 14], [[3], 4]}]}",
    )

    fun testANewlineBetweenClausesIsAnEndOfExpression() = assertLowers(
        "fn 1 -> 2\n3 -> 4 end",
        "1.16.3" to "{:fn, [closing: [line: 2, column: 8], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 1, line: 1, column: 10], line: 1, column: 6], [[1], 2]}, {:->, [line: 2, column: 3], [[3], 4]}]}",
        "1.17.3" to "{:fn, [closing: [line: 2, column: 8], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], 2]}, {:->, [line: 2, column: 3], [[3], 4]}]}",
    )

    fun testTheEndOfExpressionAfterAParenthesizedHeadClauseIsTheArrowsUntil117() = assertLowers(
        "fn (1, 2) -> 3; (4) -> 5 end",
        "1.16.3" to "{:fn, [closing: [line: 1, column: 26], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 0, line: 1, column: 15], line: 1, column: 11], [[1, 2], 3]}, {:->, [line: 1, column: 21], [[4], 5]}]}",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 26], line: 1, column: 1], [{:->, [line: 1, column: 11], [[1, 2], 3]}, {:->, [line: 1, column: 21], [[4], 5]}]}",
        "1.18.4" to "{:fn, [closing: [line: 1, column: 26], line: 1, column: 1], [{:->, [parens: [line: 1, column: 4, closing: [line: 1, column: 9]], line: 1, column: 11], [[1, 2], 3]}, {:->, [line: 1, column: 21], [[4], 5]}]}",
    )

    fun testTheEndOfExpressionAfterABodyWithMetadataIsTheBodysOnEveryVersion() = assertLowers(
        "fn 1 -> {1, 2, 3}; 2 -> {4, 5, 6} end",
        "{:fn, [closing: [line: 1, column: 35], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], {:{}, [end_of_expression: [newlines: 0, line: 1, column: 18], closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]}, {:->, [line: 1, column: 22], [[2], {:{}, [closing: [line: 1, column: 33], line: 1, column: 25], [4, 5, 6]}]}]}"
    )

    fun testTheEndOfExpressionAfterAParenthesizedBlockBodyIsTheBlocks() = assertLowers(
        "fn 1 -> (1; 2); 2 -> 3 end",
        "{:fn, [closing: [line: 1, column: 24], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], {:__block__, [end_of_expression: [newlines: 0, line: 1, column: 15], closing: [line: 1, column: 14], line: 1, column: 9], [1, 2]}]}, {:->, [line: 1, column: 19], [[2], 3]}]}"
    )

    fun testTheLastExpressionOfABodyBeforeAnotherClauseTakesItsEndOfExpression() = assertLowers(
        "fn 1 -> {1, 2, 3}\n{4, 5, 6}; 2 -> 3 end",
        "{:fn, [closing: [line: 2, column: 19], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], {:__block__, [], [{:{}, [end_of_expression: [newlines: 1, line: 1, column: 18], closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}, {:{}, [end_of_expression: [newlines: 0, line: 2, column: 10], closing: [line: 2, column: 9], line: 2, column: 1], [4, 5, 6]}]}]}, {:->, [line: 2, column: 14], [[2], 3]}]}"
    )

    fun testAFirstBodyExpressionWithoutMetadataGivesItsEndOfExpressionToTheArrowUntil117() = assertLowers(
        "fn 1 ->\n2\n{4, 5, 6}; 2 -> {7, 8, 9}\nend",
        "1.16.3" to "{:fn, [closing: [line: 4, column: 1], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 1, line: 2, column: 2], newlines: 1, line: 1, column: 6], [[1], {:__block__, [], [2, {:{}, [end_of_expression: [newlines: 0, line: 3, column: 10], closing: [line: 3, column: 9], line: 3, column: 1], [4, 5, 6]}]}]}, {:->, [line: 3, column: 14], [[2], {:{}, [closing: [line: 3, column: 25], line: 3, column: 17], ~c\"\\a\\b\\t\"}]}]}",
        "1.17.3" to "{:fn, [closing: [line: 4, column: 1], line: 1, column: 1], [{:->, [newlines: 1, line: 1, column: 6], [[1], {:__block__, [], [2, {:{}, [end_of_expression: [newlines: 0, line: 3, column: 10], closing: [line: 3, column: 9], line: 3, column: 1], [4, 5, 6]}]}]}, {:->, [line: 3, column: 14], [[2], {:{}, [end_of_expression: [newlines: 1, line: 3, column: 26], closing: [line: 3, column: 25], line: 3, column: 17], ~c\"\\a\\b\\t\"}]}]}",
    )

    fun testTheEndOfExpressionBeforeEndIsTheLastExpressionsFrom117() = assertLowers(
        "fn 1 -> {1, 2, 3}; end",
        "1.16.3" to "{:fn, [closing: [line: 1, column: 20], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]}]}",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 20], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], {:{}, [end_of_expression: [newlines: 0, line: 1, column: 18], closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]}]}",
    )

    fun testTheEndOfExpressionBeforeEndIsNeverTheArrows() = assertLowers(
        "fn 1 -> 2; end",
        "{:fn, [closing: [line: 1, column: 12], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], 2]}]}"
    )

    fun testParenthesizedClausesAreAList() = assertLowers(
        "(1 -> 2\n3 -> 4)",
        "1.16.3" to "[{:->, [end_of_expression: [newlines: 1, line: 1, column: 8], line: 1, column: 4], [[1], 2]}, {:->, [line: 2, column: 3], [[3], 4]}]",
        "1.17.3" to "[{:->, [line: 1, column: 4], [[1], 2]}, {:->, [line: 2, column: 3], [[3], 4]}]",
    )

    fun testAParenthesizedClauseWithoutASignature() = assertLowers("(-> 1)", "[{:->, [line: 1, column: 2], [[], 1]}]")

    fun testParenthesesAroundFnAddParensFrom118() = assertLowers(
        "(fn -> 1 end)",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 10], line: 1, column: 2], [{:->, [line: 1, column: 5], [[], 1]}]}",
        "1.18.4" to "{:fn, [parens: [line: 1, column: 1, closing: [line: 1, column: 13]], closing: [line: 1, column: 10], line: 1, column: 2], [{:->, [line: 1, column: 5], [[], 1]}]}",
    )

    fun testANewlineBeforeTheArrowAfterASignatureIsTheArrows() {
        assertLowers(
            "fn 1\n-> 2 end",
            "{:fn, [closing: [line: 2, column: 6], line: 1, column: 1], [{:->, [newlines: 1, line: 2, column: 1], [[1], 2]}]}"
        )
        assertLowers(
            "fn (1, 2)\n-> 3 end",
            "1.17.3" to "{:fn, [closing: [line: 2, column: 6], line: 1, column: 1], [{:->, [newlines: 1, line: 2, column: 1], [[1, 2], 3]}]}",
            "1.20.4" to "{:fn, [closing: [line: 2, column: 6], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 9], line: 1, column: 4], newlines: 1, line: 2, column: 1], [[1, 2], 3]}]}",
        )
        assertLowers(
            "case x do\n  1\n  -> 2\nend",
            "{:case, [do: [line: 1, column: 8], end: [line: 4, column: 1], line: 1, column: 1], [{:x, [line: 1, column: 6], nil}, [do: [{:->, [newlines: 1, line: 3, column: 3], [[1], 2]}]]]}"
        )
    }

    fun testANewlineBeforeWhenAfterOneParenthesizedArgumentIsTheWhens() {
        assertLowers(
            "fn (1)\nwhen 2 -> 3 end",
            "{:fn, [closing: [line: 2, column: 13], line: 1, column: 1], [{:->, [line: 2, column: 8], [[{:when, [newlines: 1, line: 2, column: 1], [1, 2]}], 3]}]}"
        )
        assertLowers(
            "fn (1, 2)\nwhen 3 -> 4 end",
            "1.17.3" to "{:fn, [closing: [line: 2, column: 13], line: 1, column: 1], [{:->, [line: 2, column: 8], [[{:when, [line: 2, column: 1], [1, 2, 3]}], 4]}]}",
            "1.20.4" to "{:fn, [closing: [line: 2, column: 13], line: 1, column: 1], [{:->, [parens: [closing: [line: 1, column: 9], line: 1, column: 4], line: 2, column: 8], [[{:when, [line: 2, column: 1], [1, 2, 3]}], 4]}]}",
        )
    }

    fun testAnEmptyClauseBeforeAnotherTakesTheEndOfExpressionUntil117() = assertLowers(
        "fn 1 -> ; 2 -> 3 end",
        "1.16.3" to "{:fn, [closing: [line: 1, column: 18], line: 1, column: 1], [{:->, [end_of_expression: [newlines: 0, line: 1, column: 9], line: 1, column: 6], [[1], nil]}, {:->, [line: 1, column: 13], [[2], 3]}]}",
        "1.17.3" to "{:fn, [closing: [line: 1, column: 18], line: 1, column: 1], [{:->, [line: 1, column: 6], [[1], nil]}, {:->, [line: 1, column: 13], [[2], 3]}]}",
    )

    /** The `->` clause's `[signature, body]`. */
    private fun clause(fn: com.ericsson.otp.erlang.OtpErlangObject): OtpErlangList {
        val clauses = (fn as OtpErlangTuple).elementAt(2) as OtpErlangList
        val clause = clauses.elementAt(0) as OtpErlangTuple

        return clause.elementAt(2) as OtpErlangList
    }
}
