package org.elixir_lang.lowering

/**
 * Elixir's line and column for a node: code points outside quoted text, grapheme clusters as the running OTP segments
 * them inside it from 1.13, and newlines older tokenizers did not count. Each expected term is
 * `Code.string_to_quoted(code, columns: true, token_metadata: true)` on that version's Elixir, and on the OTP after its
 * `/`.
 */
class PositionsTest : LoweringTestCase() {
    fun testATabIsOneColumn() =
        assertLowers("[\t{1, 2, 3}]", "[{:{}, [closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}]")

    fun testAnAstralCharacterIsOneColumn() = assertLowers(
        "[\"😀\", {1, 2, 3}]",
        "[\"😀\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]"
    )

    fun testANonAsciiAtomIsOneColumnPerCodePoint() = assertLowers(
        "[:é, {1, 2, 3}]",
        "[:é, {:{}, [closing: [line: 1, column: 14], line: 1, column: 6], [1, 2, 3]}]"
    )

    fun testADecomposedAtomIsOneColumnPerCodePoint() = assertLowers(
        "[:e\u0301, {1, 2, 3}]",
        "1.14.5" to "[:é, {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
        "1.20.4" to "[:é, {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testACombiningSequenceInAStringIsOneColumnFrom113() = assertLowers(
        "[\"e\u0301\", {1, 2, 3}]",
        "1.12.3" to "[\"e\u0301\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.13.4" to "[\"e\u0301\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testAJoinedEmojiInAStringIsOneColumnFrom113() = assertLowers(
        "[\"👨\u200d👩\u200d👧\", {1, 2, 3}]",
        "1.12.3" to "[\"👨\u200d👩\u200d👧\", {:{}, [closing: [line: 1, column: 19], line: 1, column: 11], [1, 2, 3]}]",
        "1.13.4" to "[\"👨\u200d👩\u200d👧\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testAJoinerThenAMarkAfterAnEmojiIsTwoColumns() = assertLowers(
        "[\"😀\u200d\u0300\", {1, 2, 3}]",
        "1.15.8/24.3.4.6" to "[\"😀\u200d\u0300\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.20.4/29.0.6" to "[\"😀\u200d\u0300\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
    )

    fun testAMarkFromUnicode14JoinsItsBaseFromOtp25() = assertLowers(
        "[\"a\u0898\", {1, 2, 3}]",
        "1.15.8/24.3.4.6" to "[\"a\u0898\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.15.8/25.3.2.21" to "[\"a\u0898\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testAMarkFromUnicode15JoinsItsBaseFromOtp26() = assertLowers(
        "[\"a\u0CF3\", {1, 2, 3}]",
        "1.15.8/25.3.2.21" to "[\"a\u0CF3\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.15.8/26.2.5.21" to "[\"a\u0CF3\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
        "1.18.4/27.3.4" to "[\"a\u0CF3\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testAConjunctIsOneColumnFromOtp28() = assertLowers(
        "[\"\u0915\u094D\u0937\", {1, 2, 3}]",
        "1.18.4/27.3.4" to "[\"\u0915\u094D\u0937\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.18.4/28.4" to "[\"\u0915\u094D\u0937\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testAWordOfConjunctsIsOneColumnPerConjunctFromOtp28() = assertLowers(
        "[\"স্পর্শমণি\", {1, 2, 3}]",
        "1.18.4/27.3.4" to "[\"স্পর্শমণি\", {:{}, [closing: [line: 1, column: 20], line: 1, column: 12], [1, 2, 3]}]",
        "1.18.4/28.4" to "[\"স্পর্শমণি\", {:{}, [closing: [line: 1, column: 18], line: 1, column: 10], [1, 2, 3]}]",
    )

    fun testAConjunctWithAnIndependentVowelIsOneColumnFromOtp29() = assertLowers(
        "[\"\u0915\u094D\u0904\", {1, 2, 3}]",
        "1.20.4/28.4" to "[\"\u0915\u094D\u0904\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
        "1.20.4/29.0.6" to "[\"\u0915\u094D\u0904\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
        "1.20.4" to "[\"\u0915\u094D\u0904\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
    )

    fun testASymbolThatStopsBeingPictographicOnOtp29BreaksAnEmojiSequence() = assertLowers(
        "[\"\u2388\u200d😀\", {1, 2, 3}]",
        "1.20.4/28.4" to "[\"\u2388\u200d😀\", {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
        "1.20.4/29.0.6" to "[\"\u2388\u200d😀\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
    )

    fun testAnEscapedCharacterIsOneClusterAfterItsBackslash() = assertLowers(
        "[\"\\e\u0301\", {1, 2, 3}]",
        "1.12.3" to "[\"\\e\u0301\", {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]",
        "1.13.4" to "[\"\\e\u0301\", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]",
    )

    fun testAnEscapedBackslashIsOneClusterWithTheMarkAfterIt() = assertLowers(
        "[\"\\\\\u0301\", {1, 2, 3}]",
        "1.12.3" to """["\\$ACUTE", {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]""",
        "1.13.4" to """["\\$ACUTE", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]""",
    )

    fun testAnEscapedBackslashInAQuotedAtomIsOneClusterWithTheMarkAfterIt() = assertLowers(
        "[:\"\\\\\u0301\", {1, 2, 3}]",
        "1.12.3" to """[:"\\$ACUTE", {:{}, [closing: [line: 1, column: 18], line: 1, column: 10], [1, 2, 3]}]""",
        "1.13.4" to """[:"\\$ACUTE", {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]""",
    )

    fun testAnEscapedDelimiterIsTwoColumnsWithoutTheMarkAfterIt() = assertLowers(
        "[\"\\\"\u0301\", {1, 2, 3}]",
        "1.12.3" to """["\"$ACUTE", {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]""",
        "1.13.4" to """["\"$ACUTE", {:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]""",
    )

    fun testAnEscapedSigilTerminatorIsTwoColumnsWithoutTheMarkAfterIt() = assertLowers(
        "[~s(\\)\u0301), {1, 2, 3}]",
        "1.13.4" to """[{:sigil_s, [delimiter: "(", line: 1, column: 2], [{:<<>>, [line: 1, column: 2], [")$ACUTE"]}, []]}, {:{}, [closing: [line: 1, column: 19], line: 1, column: 11], [1, 2, 3]}]""",
    )

    fun testAnEscapedInterpolationIsThreeColumnsWithoutTheMarkAfterItFrom114() = assertLowers(
        "[\"\\#{\u0301\", {1, 2, 3}]",
        "1.13.4" to """["\#{$ACUTE", {:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]""",
        "1.14.5" to """["\#{$ACUTE", {:{}, [closing: [line: 1, column: 18], line: 1, column: 10], [1, 2, 3]}]""",
    )

    fun testAnEscapedHeredocTerminatorIsFourColumnsWithoutTheMarkAfterIt() = assertLowers(
        "\"\"\"\n\\\"\"\"\u0301#{{1, 2, 3}}\n\"\"\"",
        "1.13.4" to """{:<<>>, [delimiter: "\"\"\"", indentation: 0, line: 1, column: 1], ["\"\"\"$ACUTE", {:"::", [line: 2, column: 6], [{{:., [line: 2, column: 6], [Kernel, :to_string]}, [closing: [line: 2, column: 17], line: 2, column: 6], [{:{}, [closing: [line: 2, column: 16], line: 2, column: 8], [1, 2, 3]}]}, {:binary, [line: 2, column: 6], nil}]}, "\n"]}""",
    )

    fun testACombiningSequenceInASigilIsOneColumnFrom113() = assertLowers(
        "[~s(e\u0301#{{1, 2, 3}})]",
        "1.12.3" to """[{:sigil_s, [delimiter: "(", line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["e$ACUTE", {:"::", [line: 1, column: 7], [{{:., [line: 1, column: 7], [Kernel, :to_string]}, [closing: [line: 1, column: 18], line: 1, column: 7], [{:{}, [closing: [line: 1, column: 17], line: 1, column: 9], [1, 2, 3]}]}, {:binary, [line: 1, column: 7], nil}]}]}, []]}]""",
        "1.13.4" to """[{:sigil_s, [delimiter: "(", line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["e$ACUTE", {:"::", [line: 1, column: 6], [{{:., [line: 1, column: 6], [Kernel, :to_string]}, [closing: [line: 1, column: 17], line: 1, column: 6], [{:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]}, {:binary, [line: 1, column: 6], nil}]}]}, []]}]""",
    )

    fun testAHeredocBodyOn111IsAfterItsIndentation() = assertLowers(
        "\"\"\"\n  e\u0301#{{1, 2, 3}}\n  \"\"\"",
        "1.11.4" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["e$ACUTE", {:"::", [line: 2, column: 3], [{{:., [line: 2, column: 3], [Kernel, :to_string]}, [closing: [line: 2, column: 14], line: 2, column: 3], [{:{}, [closing: [line: 2, column: 13], line: 2, column: 5], [1, 2, 3]}]}, {:binary, [line: 2, column: 3], nil}]}, "\n"]}""",
        "1.12.3" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["e$ACUTE", {:"::", [line: 2, column: 5], [{{:., [line: 2, column: 5], [Kernel, :to_string]}, [closing: [line: 2, column: 16], line: 2, column: 5], [{:{}, [closing: [line: 2, column: 15], line: 2, column: 7], [1, 2, 3]}]}, {:binary, [line: 2, column: 5], nil}]}, "\n"]}""",
        "1.13.4" to """{:<<>>, [delimiter: "\"\"\"", indentation: 2, line: 1, column: 1], ["e$ACUTE", {:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [closing: [line: 2, column: 15], line: 2, column: 4], [{:{}, [closing: [line: 2, column: 14], line: 2, column: 6], [1, 2, 3]}]}, {:binary, [line: 2, column: 4], nil}]}, "\n"]}""",
    )

    fun testAHeredocLineInsideAnInterpolationOn111IsAfterItsIndentation() = assertLowers(
        "\"\"\"\n  a#{\n  {1, 2, 3}}\n  \"\"\"",
        "1.11.4" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["a", {:"::", [line: 2, column: 2], [{{:., [line: 2, column: 2], [Kernel, :to_string]}, [closing: [line: 3, column: 10], line: 2, column: 2], [{:{}, [closing: [line: 3, column: 9], line: 3, column: 1], [1, 2, 3]}]}, {:binary, [line: 2, column: 2], nil}]}, "\n"]}""",
        "1.12.3" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["a", {:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [closing: [line: 3, column: 12], line: 2, column: 4], [{:{}, [closing: [line: 3, column: 11], line: 3, column: 3], [1, 2, 3]}]}, {:binary, [line: 2, column: 4], nil}]}, "\n"]}""",
    )

    fun testAnEscapedInterpolationIsOneColumnBefore114() = assertLowers(
        """["\#{a}#{{1, 2, 3}}"]""",
        "1.13.4" to """[{:<<>>, [delimiter: "\"", line: 1, column: 2], ["\#{a}", {:"::", [line: 1, column: 6], [{{:., [line: 1, column: 6], [Kernel, :to_string]}, [closing: [line: 1, column: 17], line: 1, column: 6], [{:{}, [closing: [line: 1, column: 16], line: 1, column: 8], [1, 2, 3]}]}, {:binary, [line: 1, column: 6], nil}]}]}]""",
        "1.15.8" to """[{:<<>>, [delimiter: "\"", line: 1, column: 2], ["\#{a}", {:"::", [line: 1, column: 8], [{{:., [line: 1, column: 8], [Kernel, :to_string]}, [closing: [line: 1, column: 19], line: 1, column: 8], [{:{}, [closing: [line: 1, column: 18], line: 1, column: 10], [1, 2, 3]}]}, {:binary, [line: 1, column: 8], nil}]}]}]""",
    )

    fun testAnEscapedNewlineInALiteralSigilIsUncountedOn111() = assertLowers(
        "[~S(a\\\nb), {1, 2, 3}]",
        "1.11.4" to """[{:sigil_S, [delimiter: "(", line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["a\\\nb"]}, []]}, {:{}, [closing: [line: 1, column: 20], line: 1, column: 12], [1, 2, 3]}]""",
        "1.12.3" to """[{:sigil_S, [delimiter: "(", line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["a\\\nb"]}, []]}, {:{}, [closing: [line: 2, column: 13], line: 2, column: 5], [1, 2, 3]}]""",
    )

    fun testANewlineCharacterIsUncountedBefore119() = assertLowers(
        "[?\n, {1, 2, 3}]",
        "1.18.4" to "[10, {:{}, [closing: [line: 1, column: 14], line: 1, column: 6], [1, 2, 3]}]",
        "1.19.5" to "[10, {:{}, [closing: [line: 2, column: 11], line: 2, column: 3], [1, 2, 3]}]",
    )

    fun testAnEscapedNewlineCharacterIsUncountedBefore119() = assertLowers(
        "[?\\\n, {1, 2, 3}]",
        "1.18.4" to "[10, {:{}, [closing: [line: 1, column: 15], line: 1, column: 7], [1, 2, 3]}]",
        "1.19.5" to "[10, {:{}, [closing: [line: 2, column: 11], line: 2, column: 3], [1, 2, 3]}]",
    )

    fun testAnEscapedNewlineInAStringIsCounted() = assertLowers(
        "[\"a\\\nb\", {1, 2, 3}]",
        "[\"ab\", {:{}, [closing: [line: 2, column: 13], line: 2, column: 5], [1, 2, 3]}]"
    )
}

/** A combining acute accent, spelled out as text is not normalized and would otherwise look like a precomposed `é`. */
private const val ACUTE = "́"
