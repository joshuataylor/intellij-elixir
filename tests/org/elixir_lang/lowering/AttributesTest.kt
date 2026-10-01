package org.elixir_lang.lowering

/** Module attributes. Each expected term is `Code.string_to_quoted(code, columns: true, token_metadata: true)`. */
class AttributesTest : LoweringTestCase() {
    fun testAnAttributeOfALiteralIsAnAtCall() = assertLowers("@1", "{:@, [line: 1, column: 1], [1]}")

    fun testAnAttributeOfAContainer() =
        assertLowers("@{1, 2, 3}", "{:@, [line: 1, column: 1], [{:{}, [closing: [line: 1, column: 10], line: 1, column: 2], [1, 2, 3]}]}")

    fun testAnAttributeOfAString() = assertLowers("@\"foo\"", "{:@, [line: 1, column: 1], [\"foo\"]}")

    fun testANewlineAfterTheAtIsNotRecorded() = assertLowers("@\n1", "{:@, [line: 1, column: 1], [1]}")

    fun testAnAttributeOfAnAttribute() = assertLowers("@@1", "{:@, [line: 1, column: 1], [{:@, [line: 1, column: 2], [1]}]}")

    fun testAnAttributeOfAParenthesizedLiteral() = assertLowers("@(1)", "{:@, [line: 1, column: 1], [1]}")

    fun testAReadAttributeIsTheAtOfAVariable() = assertLowers("@foo", "{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], nil}]}")

    fun testAnAttributeOfAParenthesesCall() =
        assertLowers("@foo(1)", "{:@, [line: 1, column: 1], [{:foo, [closing: [line: 1, column: 7], line: 1, column: 2], [1]}]}")

    fun testASetAttributeIsTheAtOfANoParenthesesCall() =
        assertLowers("@foo 1", "{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], [1]}]}")

    fun testASetAttributeOfAContainer() = assertLowers(
        "@foo {1, 2, 3}",
        "{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], [{:{}, [closing: [line: 1, column: 14], line: 1, column: 6], [1, 2, 3]}]}]}"
    )

    fun testASetAttributeWithManyArguments() =
        assertLowers("@foo 1, 2", "{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], [1, 2]}]}")

    fun testASetAttributeOfKeywords() =
        assertLowers("@foo a: 1", "{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], [[a: 1]]}]}")

    fun testASetAttributeOfANegatedNumberIsAmbiguous() = assertLowers(
        "@foo -1",
        "{:@, [line: 1, column: 1], [{:foo, [ambiguous_op: nil, line: 1, column: 2], [{:-, [line: 1, column: 6], [1]}]}]}"
    )

    fun testASetAttributeWithADoBlock() = assertLowers(
        "@foo 1 do 2 end",
        "{:@, [line: 1, column: 1], [{:foo, [do: [line: 1, column: 8], end: [line: 1, column: 13], line: 1, column: 2], [1, [do: 2]]}]}"
    )

    fun testBracketAccessOnANamedAttribute() = assertLowers(
        "@foo[:a]",
        "1.11.4" to "{{:., [line: 1, column: 5], [Access, :get]}, [line: 1, column: 5], [{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], nil}]}, :a]}",
        "1.12.3" to "{{:., [closing: [line: 1, column: 8], line: 1, column: 5], [Access, :get]}, [closing: [line: 1, column: 8], line: 1, column: 5], [{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], nil}]}, :a]}",
        "1.16.3" to "{{:., [from_brackets: true, closing: [line: 1, column: 8], line: 1, column: 5], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 8], line: 1, column: 5], [{:@, [line: 1, column: 1], [{:foo, [line: 1, column: 2], nil}]}, :a]}",
    )

    fun testBracketAccessOnANumericAttribute() = assertLowers(
        "@1[\n:a]",
        "1.11.4" to "{{:., [line: 1, column: 3], [Access, :get]}, [line: 1, column: 3], [{:@, [line: 1, column: 1], [1]}, :a]}",
        "1.12.3" to "{{:., [newlines: 1, closing: [line: 2, column: 3], line: 1, column: 3], [Access, :get]}, [newlines: 1, closing: [line: 2, column: 3], line: 1, column: 3], [{:@, [line: 1, column: 1], [1]}, :a]}",
        "1.16.3" to "{{:., [from_brackets: true, newlines: 1, closing: [line: 2, column: 3], line: 1, column: 3], [Access, :get]}, [from_brackets: true, newlines: 1, closing: [line: 2, column: 3], line: 1, column: 3], [{:@, [line: 1, column: 1], [1]}, :a]}",
    )

    fun testAnEllipsisAttributeBeforeAnOperandIsLeftUnloweredFrom117() {
        assertLowers(
            "@...[0]",
            "1.16.3" to "{{:., [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [{:@, [line: 1, column: 1], [{:..., [line: 1, column: 2], nil}]}, 0]}",
        )
        assertLowers("@... 1", "1.16.3" to "{:@, [line: 1, column: 1], [{:..., [line: 1, column: 2], [1]}]}")
        assertLeftUnlowered("@...[0]", "1.17.3")
        assertLeftUnlowered("@... 1", "1.17.3")
    }

    fun testAnEllipsisAttributeTakesTheWholeExpressionFrom117() {
        assertLowers(
            "@...[0] + 1",
            "1.16.3" to "{:+, [line: 1, column: 9], [{{:., [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [Access, :get]}, [from_brackets: true, closing: [line: 1, column: 7], line: 1, column: 5], [{:@, [line: 1, column: 1], [{:..., [line: 1, column: 2], nil}]}, 0]}, 1]}",
        )
        assertLeftUnlowered("@...[0] + 1", "1.17.3")
    }

    fun testAnAttributeTakesItsEndOfExpression() = assertLowers(
        "@1\n1",
        "{:__block__, [], [{:@, [end_of_expression: [newlines: 1, line: 1, column: 3], line: 1, column: 1], [1]}, 1]}"
    )

    /** [code] lowers at [elixirVersion] to one placeholder for the whole expression, left unlowered. */
    private fun assertLeftUnlowered(code: String, elixirVersion: String) {
        val lowered = lower(code, elixirVersion)

        assertTrue("$code on $elixirVersion: $lowered", (lowered as? ElixirAst.Placeholder)?.reason is ElixirAst.Placeholder.Reason.Unlowered)
    }
}
