package org.elixir_lang.parser_definition

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.elixir_lang.intellij_elixir.Quoter
import org.elixir_lang.psi.quoting.QuotingDialect
import org.elixir_lang.psi.quoting.QuotingDialectResolver

/**
 * Nesting is depth-first, so a parser that re-reads a nested group's contents once per enclosing group costs
 * exponentially in the nesting depth.
 *
 * Every valid shape has a shallow case, asserted against its parse tree and the reference quoter, and a deep case
 * asserted only to finish. [testDeepNestedParenthesesBlock] through [testDeepClosersDeletedWith] each took minutes
 * before the lookaheads in `ElixirParserUtil`, and [testManyNoParenthesesCallsWithoutCommas] takes minutes if each
 * statement's lookahead scans on to the end of the file. The rest are controls that were already linear, because
 * every alternative tried on them fails on its first token rather than after re-reading the whole group.
 *
 * The `testStabSignature` cases each put a stab operation's `->` after a token the lookahead could miscount - `do`,
 * `end`, `fn` or a block keyword such as `else` in each of their lexical roles, or the `(` of `.(` - so a scan that
 * miscounts it hides the `->` and the valid source parses with an error.
 */
class Issue4114TestCase : ParsingTestCase() {
    fun testNestedParenthesesBlock() = assertParsedAndQuotedCorrectly()
    fun testNestedAssignedParenthesesBlock() = assertParsedAndQuotedCorrectly()
    fun testNestedCallArgumentParentheses() = assertParsedAndQuotedCorrectly()
    fun testNestedMatchParentheses() = assertParsedAndQuotedCorrectly()
    fun testParenthesisedAnonymousFunctions() = assertParsedAndQuotedCorrectly()
    fun testNestedSpacedCallArguments() = assertParsedAndQuotedCorrectly()
    fun testNestedSpacedCallArgumentsWithCommas() = assertParsedAndQuotedCorrectly()
    fun testNestedParenthesisedNoParenthesesCalls() = assertParsedAndQuotedCorrectly()
    fun testNestedNoParenthesesCalls() = assertParsedAndQuotedCorrectly()
    fun testParenthesisedDoBlockCalls() = assertParsedAndQuotedCorrectly()
    fun testNestedQuoteUnquote() = assertParsedAndQuotedCorrectly()
    fun testNestedStabsInParentheses() = assertParsedAndQuotedCorrectly()
    fun testNestedTryRescue() = assertParsedAndQuotedCorrectly()
    fun testParenthesisedDoBlocks() = assertParsedAndQuotedCorrectly()
    fun testNestedLists() = assertParsedAndQuotedCorrectly()
    fun testNestedTuples() = assertParsedAndQuotedCorrectly()
    fun testNestedMaps() = assertParsedAndQuotedCorrectly()
    fun testNestedAnonymousFunctions() = assertParsedAndQuotedCorrectly()
    fun testNestedDoBlocks() = assertParsedAndQuotedCorrectly()

    fun testStabSignatureDoBlock() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureAnonymousFunction() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldDo() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldEnd() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldElse() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureKeywordKeys() = assertParsedAndQuotedCorrectly()
    fun testNoParenthesesArgumentsKeywordKeys() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldEndAfterNewline() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldEndAfterComment() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureDotCall() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureWordAfterNumberDo() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureWordAfterNumberEnd() = assertParsedAndQuotedCorrectly()

    /**
     * Half-typed source whose groups do not balance, where error recovery reads a `->` or `,` inside an unclosed group
     * as the stab operation's or the arguments'. The trees are the ones the parser produced before the lookaheads.
     * Where the error lists the operators that could come next, `//` is one only from Elixir 1.12, so those trees are
     * checked in both dialects.
     */
    fun testHalfTypedStabGuardBelow1_12() = assertParsedWithErrorsIn(QuotingDialect.V1_11, "HalfTypedStabGuard")
    fun testHalfTypedStabGuardFrom1_12() = assertParsedWithErrorsIn(QuotingDialect.V1_12, "HalfTypedStabGuard")
    fun testHalfTypedStabGuardAtEnd() = assertParsedWithErrors()
    fun testHalfTypedCallArguments() = assertParsedWithErrors()
    fun testHalfTypedCaseClauseAtEndBelow1_12() =
        assertParsedWithErrorsIn(QuotingDialect.V1_11, "HalfTypedCaseClauseAtEnd")
    fun testHalfTypedCaseClauseAtEndFrom1_12() =
        assertParsedWithErrorsIn(QuotingDialect.V1_12, "HalfTypedCaseClauseAtEnd")
    fun testHalfTypedCallArgumentsAtEnd() = assertParsedWithErrors()

    /** [source]`.ex` parsed in [dialect] against the golden named for the test. */
    private fun assertParsedWithErrorsIn(dialect: QuotingDialect, source: String) {
        QuotingDialectResolver.overrideDialect(project, dialect)
        val expectedName = getTestName(false)
        parseFile(expectedName, loadFile("$source.ex"))
        checkResult(expectedName, myFile)
        assertWithLocalError()
        Quoter.assertError(myFile)
    }

    fun testDeepNestedParenthesesBlock() = assertParsesWithinBudget(block("(", DEPTH))
    fun testDeepNestedAssignedParenthesesBlock() = assertParsesWithinBudget(block("q = (", DEPTH))
    fun testDeepNestedCallArgumentParentheses() = assertParsesWithinBudget("f(${surround("(", ")", DEPTH)})\n")
    fun testDeepNestedMatchParentheses() = assertParsesWithinBudget("x = ${surround("(", ")", DEPTH)}\n")
    fun testDeepNestedParenthesisedAnonymousFunctions() =
        assertParsesWithinBudget("${surround("(fn -> ", " end)", DEPTH)}\n")

    fun testDeepNestedSpacedCallArguments() = assertParsesWithinBudget("${surround("f (", ")", DEPTH)}\n")
    fun testDeepNestedSpacedCallArgumentsWithCommas() =
        assertParsesWithinBudget("${surround("f (", "), 2", 22)}\n")

    fun testDeepNestedParenthesisedNoParenthesesCalls() = assertParsesWithinBudget("${surround("(f ", ")", DEPTH)}\n")
    fun testDeepNestedNoParenthesesCalls() = assertParsesWithinBudget("${"f ".repeat(16)}1\n")
    fun testDeepParenthesisedDoBlockCalls() = assertParsesWithinBudget("${surround("(foo do ", " end)", DEPTH)}\n")
    fun testDeepNestedQuoteUnquote() = assertParsesWithinBudget("${surround("quote do unquote(", ") end", DEPTH)}\n")
    fun testDeepNestedStabsInParentheses() =
        assertParsesWithinBudget("${surround("((a -> ", ") -> b)", 16, "c")}\n")

    fun testDeepNestedTryRescue() =
        assertParsesWithinBudget("${surround("try do (", ") rescue e -> e end", DEPTH)}\n")

    fun testDeepTruncatedSpacedCallArguments() =
        assertParsesWithinBudget(truncated(surround("f (", ")", 16)), valid = false)

    fun testDeepTruncatedWith() =
        assertParsesWithinBudget(truncated(surround("with {:ok, a} <- (", "), do: a", 20)), valid = false)

    fun testDeepTruncatedFor() =
        assertParsesWithinBudget(truncated(surround("for x <- (", "), do: x", 16)), valid = false)

    fun testDeepTruncatedHeredocInterpolation() =
        assertParsesWithinBudget(truncated(surround("\"\"\"\n#{", "}\n\"\"\"", 32)), valid = false)

    fun testDeepTruncatedSigilHeredocInterpolation() =
        assertParsesWithinBudget(truncated(surround("~s\"\"\"\n#{", "}\n\"\"\"", 32)), valid = false)

    fun testDeepClosersDeletedWith() =
        assertParsesWithinBudget(
            surround("with {:ok, a} <- (", "), do: a", 16).replaceFirst("), do: a", ", do: a"),
            valid = false
        )

    /** Not nested at all: each statement's scan for a `,` must not run on to the end of the file. */
    fun testManyNoParenthesesCallsWithoutCommas() = assertParsesWithinBudget("foo 1\n".repeat(64_000))

    fun testDeepNestedParenthesisedDoBlocks() =
        assertParsesWithinBudget("${surround("(case 1 do 1 -> ", " end)", DEPTH)}\n")

    fun testDeepNestedLists() = assertParsesWithinBudget("${surround("[", "]", DEPTH)}\n")
    fun testDeepNestedTuples() = assertParsesWithinBudget("${surround("{", "}", DEPTH)}\n")
    fun testDeepNestedMaps() = assertParsesWithinBudget("${surround("%{a: ", "}", DEPTH)}\n")
    fun testDeepNestedAnonymousFunctions() = assertParsesWithinBudget("${surround("fn -> ", " end", DEPTH)}\n")
    fun testDeepNestedDoBlocks() = assertParsesWithinBudget(block("foo do", DEPTH, "end"))

    /** `opening` on its own line per level, wrapping `1`, closed by `closing` on its own line. */
    private fun block(opening: String, depth: Int, closing: String = ")"): String {
        val builder = StringBuilder()

        for (level in 0 until depth) {
            builder.append("  ".repeat(level)).append(opening).append('\n')
        }

        builder.append("  ".repeat(depth)).append("1\n")

        for (level in depth - 1 downTo 0) {
            builder.append("  ".repeat(level)).append(closing).append('\n')
        }

        return builder.toString()
    }

    private fun surround(opening: String, closing: String, depth: Int, core: String = "1"): String =
        opening.repeat(depth) + core + closing.repeat(depth)

    /** The first half of [source], as while it is still being typed. */
    private fun truncated(source: String): String = source.substring(0, source.length / 2) + "\n"

    /**
     * Parses under a progress indicator a watchdog cancels, so an exponential parse fails in seconds rather than
     * hanging the suite. Both the lookahead and [com.intellij.lang.impl.PsiBuilderImpl] poll cancellation, so either
     * half of a runaway parse stops.
     */
    private fun assertParsesWithinBudget(source: String, valid: Boolean = true) {
        val indicator = EmptyProgressIndicator()
        val watchdog = Thread({
            try {
                Thread.sleep(BUDGET_MILLISECONDS)
            } catch (_: InterruptedException) {
                return@Thread
            }

            indicator.cancel()
        }, "Issue4114TestCase budget")
        watchdog.isDaemon = true
        watchdog.start()

        try {
            ProgressManager.getInstance().runProcess(
                {
                    myFile = createPsiFile("a", source)
                    ensureParsed(myFile)
                },
                indicator
            )
        } catch (_: ProcessCanceledException) {
            fail("Parsing took longer than $BUDGET_MILLISECONDS ms")
        } finally {
            watchdog.interrupt()
        }

        if (valid) {
            assertWithoutLocalError()
        }
    }

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/issue_4114"

    companion object {
        /** Deep enough that a parse doubling per level runs for minutes, shallow enough to stay readable. */
        private const val DEPTH = 12

        /** Orders of magnitude above the milliseconds a linear parse of [DEPTH] levels takes. */
        private const val BUDGET_MILLISECONDS = 30_000L
    }
}
