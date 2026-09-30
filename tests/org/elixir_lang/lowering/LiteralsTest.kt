package org.elixir_lang.lowering

/** Each expected term is `Code.string_to_quoted(code, columns: true, token_metadata: true)` on that version's Elixir. */
class LiteralsTest : LoweringTestCase() {
    fun testNumbersAndCharactersHaveNoMetadata() =
        assertLowers("[1, 0x1F, 0o7, 0b1, 1_000, 1.5, 1.0e3, ?a, ?\\n]", "[1, 31, 7, 1, 1000, 1.5, 1000.0, 97, 10]")

    fun testAtomsHaveNoMetadata() =
        assertLowers("""[:a, :"a b", :'a', true, false, nil]""", """[:a, :"a b", :a, true, false, nil]""")

    fun testAnUnquotedIdentifierIsNormalisedFrom114() = assertLowers(
        "[:\u00B5, :\"\u00B5 b\", [\u00B5: 1]]",
        "1.13.4" to "[:\u00B5, :\"\u00B5 b\", [\u00B5: 1]]",
        "1.14.5" to "[:\u03BC, :\"\u00B5 b\", [\u03BC: 1]]",
    )

    fun testAnInterpolatedAtomIsABinaryToAtomCall() = assertLowers(
        """:"a#{1}"""",
        "1.12.3" to """{{:., [line: 1, column: 1], [:erlang, :binary_to_atom]}, [line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}""",
        "1.13.4" to """{{:., [line: 1, column: 1], [:erlang, :binary_to_atom]}, [delimiter: "\"", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}""",
        "1.16.3" to """{{:., [line: 1, column: 1], [:erlang, :binary_to_atom]}, [delimiter: "\"", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}""",
    )

    fun testAnInterpolatedCharListAtomReportsADoubleQuoteUntil118() = assertLowers(
        """:'a#{1}'""",
        "1.17.3" to """{{:., [line: 1, column: 1], [:erlang, :binary_to_atom]}, [delimiter: "\"", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}""",
        "1.18.4" to """{{:., [line: 1, column: 1], [:erlang, :binary_to_atom]}, [delimiter: "'", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}""",
    )

    fun testAnAliasHasItsLastSegmentFrom113() = assertLowers(
        "Foo",
        "1.12.3" to "{:__aliases__, [line: 1, column: 1], [:Foo]}",
        "1.13.4" to "{:__aliases__, [last: [line: 1, column: 1], line: 1, column: 1], [:Foo]}",
    )

    fun testAQualifiedAliasIsAtItsFirstSegment() = assertLowers(
        "Foo.\nBar",
        "1.12.3" to "{:__aliases__, [line: 1, column: 1], [:Foo, :Bar]}",
        "1.13.4" to "{:__aliases__, [last: [line: 2, column: 1], line: 1, column: 1], [:Foo, :Bar]}",
    )

    fun testAStringWithoutInterpolationIsABinary() = assertLowers("\"x\"", "\"x\"")

    fun testAnInterpolatedStringIsABitString() = assertLowers(
        """"a#{1}b"""",
        "1.15.8" to """{:<<>>, [delimiter: "\"", line: 1, column: 1], ["a", {:"::", [line: 1, column: 3], [{{:., [line: 1, column: 3], [Kernel, :to_string]}, [closing: [line: 1, column: 6], line: 1, column: 3], [1]}, {:binary, [line: 1, column: 3], nil}]}, "b"]}""",
        "1.16.3" to """{:<<>>, [delimiter: "\"", line: 1, column: 1], ["a", {:"::", [line: 1, column: 3], [{{:., [line: 1, column: 3], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 6], line: 1, column: 3], [1]}, {:binary, [line: 1, column: 3], nil}]}, "b"]}""",
    )

    fun testAnInterpolatedCharListIsAToCharListCall() = assertLowers(
        """'a#{1}'""",
        "1.15.8" to """{{:., [line: 1, column: 1], [List, :to_charlist]}, [delimiter: "'", line: 1, column: 1], [["a", {{:., [line: 1, column: 3], [Kernel, :to_string]}, [closing: [line: 1, column: 6], line: 1, column: 3], [1]}]]}""",
        "1.16.3" to """{{:., [line: 1, column: 1], [List, :to_charlist]}, [delimiter: "'", line: 1, column: 1], [["a", {{:., [line: 1, column: 3], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 6], line: 1, column: 3], [1]}]]}""",
    )

    fun testAnInterpolatedHeredocHasItsIndentationFrom113() = assertLowers(
        "\"\"\"\n  a#{1}\n  \"\"\"",
        "1.11.4" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["a", {:"::", [line: 2, column: 2], [{{:., [line: 2, column: 2], [Kernel, :to_string]}, [closing: [line: 2, column: 5], line: 2, column: 2], [1]}, {:binary, [line: 2, column: 2], nil}]}, "\n"]}""",
        "1.12.3" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["a", {:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [closing: [line: 2, column: 7], line: 2, column: 4], [1]}, {:binary, [line: 2, column: 4], nil}]}, "\n"]}""",
        "1.13.4" to """{:<<>>, [delimiter: "\"\"\"", indentation: 2, line: 1, column: 1], ["a", {:"::", [line: 2, column: 4], [{{:., [line: 2, column: 4], [Kernel, :to_string]}, [closing: [line: 2, column: 7], line: 2, column: 4], [1]}, {:binary, [line: 2, column: 4], nil}]}, "\n"]}""",
    )

    fun testAHeredocLineStartingWithAnInterpolation() = assertLowers(
        "\"\"\"\n  #{{1, 2, 3}}\n  \"\"\"",
        "1.11.4" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], [{:"::", [line: 2, column: 1], [{{:., [line: 2, column: 1], [Kernel, :to_string]}, [closing: [line: 2, column: 12], line: 2, column: 1], [{:{}, [closing: [line: 2, column: 11], line: 2, column: 3], [1, 2, 3]}]}, {:binary, [line: 2, column: 1], nil}]}, "\n"]}""",
        "1.12.3" to """{:<<>>, [delimiter: "\"\"\"", line: 1, column: 1], ["", {:"::", [line: 2, column: 3], [{{:., [line: 2, column: 3], [Kernel, :to_string]}, [closing: [line: 2, column: 14], line: 2, column: 3], [{:{}, [closing: [line: 2, column: 13], line: 2, column: 5], [1, 2, 3]}]}, {:binary, [line: 2, column: 3], nil}]}, "\n"]}""",
    )

    fun testACharListHeredocIsAList() = assertLowers("'''\n  a\n  '''", "~c\"a\\n\"")

    fun testAnInterpolatedSigil() = assertLowers(
        """~s(a#{1})""",
        "1.15.8" to """{:sigil_s, [delimiter: "(", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 5], [{{:., [line: 1, column: 5], [Kernel, :to_string]}, [closing: [line: 1, column: 8], line: 1, column: 5], [1]}, {:binary, [line: 1, column: 5], nil}]}]}, []]}""",
        "1.16.3" to """{:sigil_s, [delimiter: "(", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a", {:"::", [line: 1, column: 5], [{{:., [line: 1, column: 5], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 8], line: 1, column: 5], [1]}, {:binary, [line: 1, column: 5], nil}]}]}, []]}""",
    )

    fun testASigilHeredocHasItsIndentation() = assertLowers(
        "~S\"\"\"\n  x\n  \"\"\"",
        """{:sigil_S, [delimiter: "\"\"\"", line: 1, column: 1], [{:<<>>, [indentation: 2, line: 1, column: 1], ["x\n"]}, []]}"""
    )

    fun testASigilsModifiersAreACharList() = assertLowers(
        "~r/x/i",
        """{:sigil_r, [delimiter: "/", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["x"]}, ~c"i"]}"""
    )

    fun testAListsTrailingKeywordsArePairs() = assertLowers("[1, a: 2]", "[1, {:a, 2}]")

    fun testAQuotedKeywordKeyIsAnAtom() = assertLowers("""["a b": 1]""", """["a b": 1]""")

    fun testAnInterpolatedKeywordKeyHasItsDelimiterFrom118() = assertLowers(
        """["a#{1}": 1]""",
        "1.17.3" to """[{{{:., [line: 1, column: 2], [:erlang, :binary_to_atom]}, [format: :keyword, line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}, 1}]""",
        "1.18.4" to """[{{{:., [line: 1, column: 2], [:erlang, :binary_to_atom]}, [delimiter: "\"", format: :keyword, line: 1, column: 2], [{:<<>>, [line: 1, column: 2], ["a", {:"::", [line: 1, column: 4], [{{:., [line: 1, column: 4], [Kernel, :to_string]}, [from_interpolation: true, closing: [line: 1, column: 7], line: 1, column: 4], [1]}, {:binary, [line: 1, column: 4], nil}]}]}, :utf8]}, 1}]""",
    )

    fun testAnEmptyTupleHasItsClosing() = assertLowers("{}", "{:{}, [closing: [line: 1, column: 2], line: 1, column: 1], []}")

    fun testAOneTupleHasItsClosing() = assertLowers("{1}", "{:{}, [closing: [line: 1, column: 3], line: 1, column: 1], [1]}")

    fun testAPairIsItsOwnTerm() = assertLowers("{1, 2}", "{1, 2}")

    fun testATupleOpenedBeforeANewlineCountsIt() = assertLowers(
        "{\n1, 2, 3}",
        "{:{}, [newlines: 1, closing: [line: 2, column: 8], line: 1, column: 1], [1, 2, 3]}"
    )

    fun testAMapIsAtItsPercentFrom117() = assertLowers(
        "%{a: 1}",
        "1.16.3" to "{:%{}, [closing: [line: 1, column: 7], line: 1, column: 2], [a: 1]}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 7], line: 1, column: 1], [a: 1]}",
    )

    fun testAMapKeyWithMetadataHasItsAssociationFrom118() = assertLowers(
        "%{{1, 2, 3} => 1}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 17], line: 1, column: 1], [{{:{}, [closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}, 1}]}",
        "1.18.4" to "{:%{}, [closing: [line: 1, column: 17], line: 1, column: 1], [{{:{}, [assoc: [line: 1, column: 13], closing: [line: 1, column: 11], line: 1, column: 3], [1, 2, 3]}, 1}]}",
    )

    fun testAMapUpdateIsAPipe() = assertLowers(
        "%{%{} | a: 1}",
        "1.16.3" to "{:%{}, [closing: [line: 1, column: 13], line: 1, column: 2], [{:|, [line: 1, column: 7], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 4], []}, [a: 1]]}]}",
        "1.17.3" to "{:%{}, [closing: [line: 1, column: 13], line: 1, column: 1], [{:|, [line: 1, column: 7], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 3], []}, [a: 1]]}]}",
    )

    fun testAStructsMapIsAtItsCurly() = assertLowers(
        "%Foo{a: 1}",
        "1.12.3" to "{:%, [line: 1, column: 1], [{:__aliases__, [line: 1, column: 2], [:Foo]}, {:%{}, [closing: [line: 1, column: 10], line: 1, column: 5], [a: 1]}]}",
        "1.20.4" to "{:%, [line: 1, column: 1], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, {:%{}, [closing: [line: 1, column: 10], line: 1, column: 5], [a: 1]}]}",
    )

    fun testABitStringHasItsClosing() =
        assertLowers("<<1, 2>>", "{:<<>>, [closing: [line: 1, column: 7], line: 1, column: 1], [1, 2]}")

    fun testAnUpdatePipeHasTheNewlinesAfterIt() = assertLowers(
        "%{%{} |\n a: 1}",
        "1.11.4" to "{:%{}, [closing: [line: 2, column: 6], line: 1, column: 2], [{:|, [newlines: 1, line: 1, column: 7], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 4], []}, [a: 1]]}]}",
        "1.20.4" to "{:%{}, [closing: [line: 2, column: 6], line: 1, column: 1], [{:|, [newlines: 1, line: 1, column: 7], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 3], []}, [a: 1]]}]}",
    )

    fun testAnUpdatePipeHasTheNewlinesBeforeIt() = assertLowers(
        "%{%{}\n| a: 1}",
        "1.11.4" to "{:%{}, [closing: [line: 2, column: 7], line: 1, column: 2], [{:|, [newlines: 1, line: 2, column: 1], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 4], []}, [a: 1]]}]}",
        "1.20.4" to "{:%{}, [closing: [line: 2, column: 7], line: 1, column: 1], [{:|, [newlines: 1, line: 2, column: 1], [{:%{}, [closing: [line: 1, column: 5], line: 1, column: 3], []}, [a: 1]]}]}",
    )

    fun testAStructUpdatePipeHasItsNewlines() = assertLowers(
        "%Foo{%{} |\n\n a: 1}",
        "1.11.4" to "{:%, [line: 1, column: 1], [{:__aliases__, [line: 1, column: 2], [:Foo]}, {:%{}, [closing: [line: 3, column: 6], line: 1, column: 5], [{:|, [newlines: 2, line: 1, column: 10], [{:%{}, [closing: [line: 1, column: 8], line: 1, column: 7], []}, [a: 1]]}]}]}",
        "1.20.4" to "{:%, [line: 1, column: 1], [{:__aliases__, [last: [line: 1, column: 2], line: 1, column: 2], [:Foo]}, {:%{}, [closing: [line: 3, column: 6], line: 1, column: 5], [{:|, [newlines: 2, line: 1, column: 10], [{:%{}, [closing: [line: 1, column: 8], line: 1, column: 6], []}, [a: 1]]}]}]}",
    )

    fun testAParenthesizedLeftAliasKeepsItsMetadata() = assertLowers(
        "(Foo).Bar",
        "1.12.3" to "{:__aliases__, [line: 1, column: 2], [:Foo, :Bar]}",
        "1.17.3" to "{:__aliases__, [last: [line: 1, column: 7], line: 1, column: 2], [:Foo, :Bar]}",
        "1.18.4" to "{:__aliases__, [parens: [line: 1, column: 1, closing: [line: 1, column: 5]], last: [line: 1, column: 7], line: 1, column: 2], [:Foo, :Bar]}",
        "1.20.4" to "{:__aliases__, [parens: [closing: [line: 1, column: 5], line: 1, column: 1], last: [line: 1, column: 7], line: 1, column: 2], [:Foo, :Bar]}",
    )

    fun testAParenthesizedLeftAliasKeepsItsEndOfExpression() = assertLowers(
        "(Foo\n).Bar",
        "1.16.3" to "{:__aliases__, [last: [line: 2, column: 3], line: 1, column: 2], [:Foo, :Bar]}",
        "1.17.3" to "{:__aliases__, [end_of_expression: [newlines: 1, line: 1, column: 5], last: [line: 2, column: 3], line: 1, column: 2], [:Foo, :Bar]}",
        "1.20.4" to "{:__aliases__, [parens: [closing: [line: 2, column: 1], line: 1, column: 1], end_of_expression: [newlines: 1, line: 1, column: 5], last: [line: 2, column: 3], line: 1, column: 2], [:Foo, :Bar]}",
    )

    fun testADoubledBackslashBeforeANewlineInALiteralSigilIsKept() = assertLowers(
        "~S(a\\\\\nb)",
        """{:sigil_S, [delimiter: "(", line: 1, column: 1], [{:<<>>, [line: 1, column: 1], ["a\\\\\nb"]}, []]}""",
    )

    // Elixir's parser rejects these, so there is no quoted form to compare with.
    fun testDigitsOutsideTheirBaseLowerToTheConversionThatRaises() = assertLowers(
        "0b12",
        "{{:., [line: 1, column: 1], [String, :to_integer]}, [line: 1, column: 1], [\"12\", 2]}",
    )
}
