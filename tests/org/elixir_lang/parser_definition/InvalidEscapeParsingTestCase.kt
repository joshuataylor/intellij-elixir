package org.elixir_lang.parser_definition

/** A sigil receives an invalid escape as written, so Elixir parses each of these on every release. */
class InvalidEscapeParsingTestCase : ParsingTestCase() {
    fun testCustomSigil() = assertParsedAndQuotedCorrectly(false)
    fun testRegexSigil() = assertParsedAndQuotedCorrectly(false)
    fun testUnescapingSigils() = assertParsedAndQuotedCorrectly(false)

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/invalid_escape"
}
