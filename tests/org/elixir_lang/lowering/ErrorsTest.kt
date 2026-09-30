package org.elixir_lang.lowering

/** Broken code lowers, each error to a placeholder that quotes as `__cursor__`. */
class ErrorsTest : LoweringTestCase() {
    fun testAnErrorElementIsAnErrorPlaceholder() {
        val lowered = lower("[1,")

        assertEquals(ERROR, placeholderReasons(lowered))
        assertFalse(lowered.hasUnlowered())
    }

    fun testAnUnfinishedClauseIsAnErrorPlaceholder() = assertEquals(ERROR, placeholderReasons(lower("fn ->")))

    fun testAnOperatorMissingAnOperandIsBroken() {
        assertBroken("1 +")
        assertBroken("1 in")
        assertBroken("1 not in")
        assertBroken("1..2//")
        assertBroken("x = !")
    }

    fun testAnUnterminatedHeredocIsBroken() {
        assertBroken("x = \"\"\"\nabc")
        assertBroken("x = ~S\"\"\"\nabc")
    }

    fun testAnOperandTheParserRollsBackIsBroken() = assertBroken("a + %Foo")

    fun testAnInterpolatedRemoteNameIsBroken() {
        for (version in listOf(OLDEST, NEWEST)) {
            assertBroken("Foo.\"a#{b}\"()", version)
            assertBroken("Foo.\"a#{b}\"[0]", version)
            assertBroken("Foo.\"a#{b}\"", version)
            assertBroken("Foo.\"a#{b}\" 1", version)
        }
    }

    private fun assertBroken(code: String, elixirVersion: String = NEWEST) {
        val reasons = placeholderReasons(lower(code, elixirVersion))

        assertTrue("$code: $reasons", reasons.isNotEmpty() && reasons.all { it == ElixirAst.Placeholder.Reason.Error })
    }

    private fun placeholderReasons(ast: ElixirAst): List<ElixirAst.Placeholder.Reason> = ast.placeholders().map { it.reason }
}

private val ERROR = listOf(ElixirAst.Placeholder.Reason.Error)
