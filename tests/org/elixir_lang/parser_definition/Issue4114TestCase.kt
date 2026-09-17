package org.elixir_lang.parser_definition

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager

/**
 * Nesting is depth-first, so a parser that re-reads a nested group's contents once per enclosing group costs
 * exponentially in the nesting depth.
 *
 * Every shape has a shallow case, asserted against its parse tree and the reference quoter, and a deep case asserted
 * only to finish. Only [testDeepNestedParenthesesBlock] through [testDeepNestedParenthesisedAnonymousFunctions] take
 * minutes without `stabOperationAhead`; the rest are controls that stay green when the guard is reverted, because
 * their stab operation either matches or fails on the first token rather than after re-reading the whole group.
 *
 * The `testStabSignature` cases each put a stab operation's `->` after a token the lookahead could miscount - `do`,
 * `end` or `fn` in each of their lexical roles, or the `(` of `.(` - so a scan that miscounts it hides the `->` and the
 * valid source parses with an error.
 */
class Issue4114TestCase : ParsingTestCase() {
    fun testNestedParenthesesBlock() = assertParsedAndQuotedCorrectly()
    fun testNestedAssignedParenthesesBlock() = assertParsedAndQuotedCorrectly()
    fun testNestedCallArgumentParentheses() = assertParsedAndQuotedCorrectly()
    fun testNestedMatchParentheses() = assertParsedAndQuotedCorrectly()
    fun testParenthesisedAnonymousFunctions() = assertParsedAndQuotedCorrectly()
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
    fun testStabSignatureKeywordKeys() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldEndAfterNewline() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureFieldEndAfterComment() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureDotCall() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureWordAfterNumberDo() = assertParsedAndQuotedCorrectly()
    fun testStabSignatureWordAfterNumberEnd() = assertParsedAndQuotedCorrectly()

    fun testDeepNestedParenthesesBlock() = assertParsesWithinBudget(block("(", DEPTH))
    fun testDeepNestedAssignedParenthesesBlock() = assertParsesWithinBudget(block("q = (", DEPTH))
    fun testDeepNestedCallArgumentParentheses() = assertParsesWithinBudget("f(${surround("(", ")", DEPTH)})\n")
    fun testDeepNestedMatchParentheses() = assertParsesWithinBudget("x = ${surround("(", ")", DEPTH)}\n")
    fun testDeepNestedParenthesisedAnonymousFunctions() =
        assertParsesWithinBudget("${surround("(fn -> ", " end)", DEPTH)}\n")

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

    private fun surround(opening: String, closing: String, depth: Int): String =
        opening.repeat(depth) + "1" + closing.repeat(depth)

    /**
     * Parses under a progress indicator a watchdog cancels, so an exponential parse fails in seconds rather than
     * hanging the suite. Both the lookahead and [com.intellij.lang.impl.PsiBuilderImpl] poll cancellation, so either
     * half of a runaway parse stops.
     */
    private fun assertParsesWithinBudget(source: String) {
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
            fail("Parsing $DEPTH levels of nesting took longer than $BUDGET_MILLISECONDS ms")
        } finally {
            watchdog.interrupt()
        }

        assertWithoutLocalError()
    }

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/issue_4114"

    companion object {
        /** Deep enough that a parse doubling per level runs for minutes, shallow enough to stay readable. */
        private const val DEPTH = 12

        /** Orders of magnitude above the milliseconds a linear parse of [DEPTH] levels takes. */
        private const val BUDGET_MILLISECONDS = 30_000L
    }
}
