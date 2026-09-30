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
}
