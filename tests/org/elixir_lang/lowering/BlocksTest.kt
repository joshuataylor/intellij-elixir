package org.elixir_lang.lowering

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.psi.ElixirFile

/**
 * The file, parentheses and interpolation, which share one block builder. Each expected term is
 * `Code.string_to_quoted(code, columns: true, token_metadata: true)` on that version's Elixir.
 */
class BlocksTest : LoweringTestCase() {
    fun testAnEmptyFileHasLineMetadataFrom120() = assertLowers(
        "",
        "1.19.5" to "{:__block__, [], []}",
        "1.20.4" to "{:__block__, [line: 1, column: 1], []}",
    )

    fun testAFileOfOnlyAnEndOfExpressionIsAtItUntil120() = assertLowers(
        "  \n",
        "1.19.5" to "{:__block__, [line: 1, column: 3], []}",
        "1.20.4" to "{:__block__, [line: 1, column: 1], []}",
    )

    fun testAFileOfOnlyACommentIsAtTheCommentUntil120() = assertLowers(
        "  # c\n",
        "1.11.4" to "{:__block__, [line: 1, column: 3], []}",
        "1.19.5" to "{:__block__, [line: 1, column: 3], []}",
        "1.20.4" to "{:__block__, [line: 1, column: 1], []}",
    )

    fun testTheLastExpressionHasItsEndOfExpressionFrom117() = assertLowers(
        "{1, 2, 3}\n{1, 2, 3}\n",
        "1.16.3" to "{:__block__, [], [{:{}, [end_of_expression: [newlines: 1, line: 1, column: 10], closing: [line: 1, column: 9], line: 1, column: 1], [1, 2, 3]}, {:{}, [closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}]}",
        "1.17.3" to "{:__block__, [], [{:{}, [end_of_expression: [newlines: 1, line: 1, column: 10], closing: [line: 1, column: 9], line: 1, column: 1], [1, 2, 3]}, {:{}, [end_of_expression: [newlines: 1, line: 2, column: 10], closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}]}",
    )

    fun testASemicolonIsAnEndOfExpressionWithoutNewlines() = assertLowers(
        "{1, 2, 3};{1, 2, 3}",
        "{:__block__, [], [{:{}, [end_of_expression: [newlines: 0, line: 1, column: 10], closing: [line: 1, column: 9], line: 1, column: 1], [1, 2, 3]}, {:{}, [closing: [line: 1, column: 19], line: 1, column: 11], [1, 2, 3]}]}"
    )

    fun testAnEndOfExpressionAfterACommentIsAtTheComment() = assertLowers(
        "{1, 2, 3} # c\n\n{1, 2, 3}",
        "{:__block__, [], [{:{}, [end_of_expression: [newlines: 2, line: 1, column: 11], closing: [line: 1, column: 9], line: 1, column: 1], [1, 2, 3]}, {:{}, [closing: [line: 3, column: 9], line: 3, column: 1], [1, 2, 3]}]}"
    )

    fun testALiteralTakesNoEndOfExpression() = assertLowers(
        "1\n{1, 2, 3}",
        "{:__block__, [], [1, {:{}, [closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}]}"
    )

    fun testParenthesesAroundOneExpressionAddParensFrom118() = assertLowers(
        "({1, 2, 3})",
        "1.17.3" to "{:{}, [closing: [line: 1, column: 10], line: 1, column: 2], [1, 2, 3]}",
        "1.18.4" to "{:{}, [parens: [line: 1, column: 1, closing: [line: 1, column: 11]], closing: [line: 1, column: 10], line: 1, column: 2], [1, 2, 3]}",
        "1.20.4" to "{:{}, [parens: [closing: [line: 1, column: 11], line: 1, column: 1], closing: [line: 1, column: 10], line: 1, column: 2], [1, 2, 3]}",
    )

    fun testNestedParenthesesAddParensOutermostFirst() = assertLowers(
        "(({1, 2, 3}))",
        "1.17.3" to "{:{}, [closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}",
        "1.18.4" to "{:{}, [parens: [line: 1, column: 1, closing: [line: 1, column: 13]], parens: [line: 1, column: 2, closing: [line: 1, column: 12]], closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}",
        "1.20.4" to "{:{}, [parens: [closing: [line: 1, column: 13], line: 1, column: 1], parens: [closing: [line: 1, column: 12], line: 1, column: 2], closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}",
    )

    fun testParenthesesAroundALiteralAreDropped() = assertLowers("(1)", "1")

    fun testParenthesesAroundExpressionsAreABlockAtTheParentheses() =
        assertLowers("(1; 2)", "{:__block__, [closing: [line: 1, column: 6], line: 1, column: 1], [1, 2]}")

    fun testNestedParenthesesAroundABlockMergeUntil117() = assertLowers(
        "((1; 2))",
        "1.16.3" to "{:__block__, [closing: [line: 1, column: 7], line: 1, column: 2, closing: [line: 1, column: 8], line: 1, column: 1], [1, 2]}",
        "1.17.3" to "{:__block__, [closing: [line: 1, column: 7], line: 1, column: 2], [1, 2]}",
        "1.18.4" to "{:__block__, [parens: [line: 1, column: 1, closing: [line: 1, column: 8]], closing: [line: 1, column: 7], line: 1, column: 2], [1, 2]}",
        "1.20.4" to "{:__block__, [parens: [closing: [line: 1, column: 8], line: 1, column: 1], closing: [line: 1, column: 7], line: 1, column: 2], [1, 2]}",
    )

    fun testEmptyParenthesesAddParensFrom118() = assertLowers(
        "()",
        "1.17.3" to "{:__block__, [], []}",
        "1.18.4" to "{:__block__, [parens: [line: 1, column: 1, closing: [line: 1, column: 2]]], []}",
        "1.20.4" to "{:__block__, [parens: [closing: [line: 1, column: 2], line: 1, column: 1]], []}",
    )

    fun testParenthesesAroundASemicolonAreAnEmptyBlockAtThem() =
        assertLowers("(;)", "{:__block__, [closing: [line: 1, column: 3], line: 1, column: 1], []}")

    fun testAnExpressionEndingInParenthesesHasItsEndOfExpressionFrom117() = assertLowers(
        "(\n{1, 2, 3}\n)",
        "1.16.3" to "{:{}, [closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}",
        "1.17.3" to "{:{}, [end_of_expression: [newlines: 1, line: 2, column: 10], closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}",
        "1.18.4" to "{:{}, [parens: [line: 1, column: 1, closing: [line: 3, column: 1]], end_of_expression: [newlines: 1, line: 2, column: 10], closing: [line: 2, column: 9], line: 2, column: 1], [1, 2, 3]}",
    )

    fun testAnEmptyInterpolationHasLineMetadataFrom120() = assertLowers(
        "[1,\n  \"#{}\"]",
        "1.19.5" to """[1, {:<<>>, [delimiter: "\"", line: 2, column: 3], [{:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 2, column: 6], line: 2, column: 4], [{:__block__, [], []}]}, {:binary, [line: 2, column: 4], nil}]}]}]""",
        "1.20.4" to """[1, {:<<>>, [delimiter: "\"", line: 2, column: 3], [{:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 2, column: 6], line: 2, column: 4], [{:__block__, [line: 1, column: 1], []}]}, {:binary, [line: 2, column: 4], nil}]}]}]""",
    )

    fun testAnInterpolationOfOnlyAnEndOfExpressionIsAtItUntil120() = assertLowers(
        "[1,\n\"#{\n}\"]",
        "1.19.5" to """[1, {:<<>>, [delimiter: "\"", line: 2, column: 1], [{:"::", [line: 2, column: 2], [{{:., [line: 2, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 3, column: 1], line: 2, column: 2], [{:__block__, [line: 2, column: 4], []}]}, {:binary, [line: 2, column: 2], nil}]}]}]""",
        "1.20.4" to """[1, {:<<>>, [delimiter: "\"", line: 2, column: 1], [{:"::", [line: 2, column: 2], [{{:., [line: 2, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 3, column: 1], line: 2, column: 2], [{:__block__, [line: 1, column: 1], []}]}, {:binary, [line: 2, column: 2], nil}]}]}]""",
    )

    fun testAnInterpolationOfExpressionsIsABlock() = assertLowers(
        "\"#{1; 2}\"",
        "1.20.4" to """{:<<>>, [delimiter: "\"", line: 1, column: 1], [{:"::", [line: 1, column: 2], [{{:., [line: 1, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 8], line: 1, column: 2], [{:__block__, [], [1, 2]}]}, {:binary, [line: 1, column: 2], nil}]}]}""",
    )

    fun testAnInterpolationsLastExpressionHasItsEndOfExpressionFrom117() = assertLowers(
        "\"#{{1, 2, 3}\n}\"",
        "1.16.3" to """{:<<>>, [delimiter: "\"", line: 1, column: 1], [{:"::", [line: 1, column: 2], [{{:., [line: 1, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 2, column: 1], line: 1, column: 2], [{:{}, [closing: [line: 1, column: 12], line: 1, column: 4], [1, 2, 3]}]}, {:binary, [line: 1, column: 2], nil}]}]}""",
        "1.17.3" to """{:<<>>, [delimiter: "\"", line: 1, column: 1], [{:"::", [line: 1, column: 2], [{{:., [line: 1, column: 2], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 2, column: 1], line: 1, column: 2], [{:{}, [end_of_expression: [newlines: 1, line: 1, column: 13], closing: [line: 1, column: 12], line: 1, column: 4], [1, 2, 3]}]}, {:binary, [line: 1, column: 2], nil}]}]}""",
    )

    // A lone `not`/`!` or `unquote_splicing` is an operator or a call, so these build blocks from nodes made by hand.

    fun testALoneUnaryIsWrappedInEveryBlockUntil115() = assertBuilds(
        null,
        "1.14.5" to "{:__block__, [], [{:not, [line: 1, column: 2], [true]}]}",
        "1.15.8" to "{:not, [line: 1, column: 2], [true]}",
    )

    fun testALoneUnaryInParenthesesIsWrappedWithoutTheirMetadataFrom115() = assertBuilds(
        Blocks.Enclosing.Parentheses(Meta.Position(1, 1), Meta.Position(1, 10)),
        "1.14.5" to "{:__block__, [closing: [line: 1, column: 10], line: 1, column: 1], [{:not, [line: 1, column: 2], [true]}]}",
        "1.15.8" to "{:__block__, [], [{:not, [line: 1, column: 2], [true]}]}",
        "1.18.4" to "{:__block__, [], [{:not, [line: 1, column: 2], [true]}]}",
    )

    fun testALoneBangInParenthesesIsWrappedLikeNot() = assertBuilds(
        Blocks.Enclosing.Parentheses(Meta.Position(1, 1), Meta.Position(1, 10)),
        "1.14.5" to "{:__block__, [closing: [line: 1, column: 10], line: 1, column: 1], [{:!, [line: 1, column: 2], [true]}]}",
        "1.15.8" to "{:__block__, [], [{:!, [line: 1, column: 2], [true]}]}",
        callee = "!",
    )

    fun testADoBodyHasItsDoAsParensFrom120() = assertBuilds(
        Blocks.Enclosing.Do(Meta.Position(1, 1)),
        "1.19.5" to "{:not, [line: 1, column: 2], [true]}",
        "1.20.4" to "{:not, [parens: [line: 1, column: 1], line: 1, column: 2], [true]}",
    )

    fun testALoneUnquoteSplicingInADoBodyIsABlockAtTheDoFrom120() = assertBuilds(
        Blocks.Enclosing.Do(Meta.Position(1, 1)),
        "1.19.5" to "{:__block__, [], [{:unquote_splicing, [line: 1, column: 2], [true]}]}",
        "1.20.4" to "{:__block__, [line: 1, column: 1], [{:unquote_splicing, [line: 1, column: 2], [true]}]}",
        callee = "unquote_splicing",
    )

    fun testALoneUnquoteSplicingIsWrapped() = assertBuilds(
        Blocks.Enclosing.Parentheses(Meta.Position(1, 1), Meta.Position(1, 10)),
        "1.11.4" to "{:__block__, [closing: [line: 1, column: 10], line: 1, column: 1], [{:unquote_splicing, [line: 1, column: 2], [true]}]}",
        "1.20.4" to "{:__block__, [closing: [line: 1, column: 10], line: 1, column: 1], [{:unquote_splicing, [line: 1, column: 2], [true]}]}",
        callee = "unquote_splicing",
    )

    private fun assertBuilds(
        enclosing: Blocks.Enclosing?,
        vararg expected: Pair<String, String>,
        callee: String = "not",
    ) {
        val file = createPsiFile(getTestName(false), "true") as ElixirFile
        val origin = TextRange(0, 0)
        val at = Meta.Position(1, 2)
        val meta = Meta(origin, at, at)
        val call = ElixirAst.Call(
            Meta(origin, at, at, listOf(Meta.Key.Location(at))),
            ElixirAst.Literal.Atom(meta, callee),
            listOf(ElixirAst.Literal.Atom(meta, "true"))
        )

        assertEquals(
            expected.joinToString("\n") { (version, term) -> "$version: $term" },
            expected.joinToString("\n") { (version, _) ->
                val built = ReadAction.computeBlocking<ElixirAst, Throwable> {
                    Lowering.of(file, ElixirLanguageLevel.of(version)).buildBlock(listOf(call), file, enclosing)
                }

                "$version: " + inspect(built.toOtp(COLUMNS_AND_TOKEN_METADATA))
            }
        )
    }

    fun testASigilEndingParenthesesHasItsEndOfExpressionFrom117() = assertLowers(
        "(\n~S(a)\n)",
        "1.16.3" to "{:sigil_S, [delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
        "1.17.3" to "{:sigil_S, [end_of_expression: [newlines: 1, line: 2, column: 6], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
        "1.18.4" to "{:sigil_S, [parens: [line: 1, column: 1, closing: [line: 3, column: 1]], end_of_expression: [newlines: 1, line: 2, column: 6], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
        "1.20.4" to "{:sigil_S, [parens: [closing: [line: 3, column: 1], line: 1, column: 1], end_of_expression: [newlines: 1, line: 2, column: 6], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
    )

    fun testASigilBeforeACommentEndingParenthesesHasItsEndOfExpressionAtTheComment() = assertLowers(
        "(\n~S(a) # c\n)",
        "1.16.3" to "{:sigil_S, [delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
        "1.17.3" to "{:sigil_S, [end_of_expression: [newlines: 1, line: 2, column: 7], delimiter: \"(\", line: 2, column: 1], [{:<<>>, [line: 2, column: 1], [\"a\"]}, []]}",
    )

    fun testASigilBeforeACommentBeforeAnotherExpressionHasItsEndOfExpressionAtTheComment() = assertLowers(
        "~c\"a\" # c\n2",
        "{:__block__, [], [{:sigil_c, [end_of_expression: [newlines: 1, line: 1, column: 7], delimiter: \"\\\"\", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], [\"a\"]}, []]}, 2]}",
    )
}
