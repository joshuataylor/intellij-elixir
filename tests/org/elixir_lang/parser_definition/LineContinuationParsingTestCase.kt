package org.elixir_lang.parser_definition

/**
 * A line continuation (`\` and a newline) between a call name, an operator and what follows, quoted against each leg's
 * Elixir, whose outcomes differ by release for `not in` (1.19) and a sign after a space (1.20).
 */
class LineContinuationParsingTestCase : ParsingTestCase() {
    fun testKeywordKeyAfterCallName() = assertParsedAndQuotedCorrectly(false)
    fun testDualOperatorAfterCallName() = assertParsedAndQuotedCorrectly(false)
    fun testDualOperatorAfterSpaceAndContinuation() = assertParsedAndQuotedCorrectly(false)
    fun testNotIn() = assertParsedAndQuotedCorrectly(false)

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/line_continuation"
}
