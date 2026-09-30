package org.elixir_lang.parser_definition

import org.elixir_lang.language_level.elixir

/**
 * An unquoted name that is not in NFC is a syntax error before Elixir 1.14 and is normalized to NFC from 1.14. A quoted
 * name is never normalized.
 */
class NameSpellingParsingTestCase : ParsingTestCase() {
    fun testAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDoubleQuotedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testSingleQuotedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testHexEscapeAtom() = assertParsedAndQuotedCorrectly(false)
    fun testPrecomposedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDecomposedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDottedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testElixirPrefixedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testAlias() = assertParsedAndQuotedCorrectly(false)
    fun testElixirDotAlias() = assertParsedAndQuotedCorrectly(false)
    fun testCapitalAtom() = assertParsedAndQuotedCorrectly(false)
    fun testModuleDotAlias() = assertParsedAndQuotedCorrectly(false)
    fun testInterpolatedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDefmoduleDottedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDefmoduleElixirPrefixedAtom() = assertParsedAndQuotedCorrectly(false)
    fun testDefUnquote() = assertParsedAndQuotedCorrectly(false)
    fun testKeyword() = assertParsedAndQuotedCorrectly(false)
    fun testDoubleQuotedKeyword() = assertParsedAndQuotedCorrectly(false)
    fun testSingleQuotedKeyword() = assertParsedAndQuotedCorrectly(false)
    fun testModuleAttribute() = assertParsedAndQuotedCorrectly(false)
    fun testPrecomposedIdentifier() = assertParsedAndQuotedCorrectly(false)
    fun testDecomposedIdentifier() = assertParsedAndQuotedCorrectlyFrom(elixir("1.14.0"), false)
    fun testDecomposedUnquotedAtom() = assertParsedAndQuotedCorrectlyFrom(elixir("1.14.0"), false)
    fun testDecomposedKeywordKey() = assertParsedAndQuotedCorrectlyFrom(elixir("1.14.0"), false)

    override fun getTestDataPath(): String = "${super.getTestDataPath()}/name_spelling_parsing_test_case"
}
