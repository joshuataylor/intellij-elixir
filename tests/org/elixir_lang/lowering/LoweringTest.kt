package org.elixir_lang.lowering

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangTuple
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.parser_definition.ParsingTestCase
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.impl.ElixirMatchedQualifiedMultipleAliasesImpl
import org.elixir_lang.psi.impl.ElixirMultipleAliasesImpl
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl

class LoweringTest : ParsingTestCase() {
    fun testAFileWithNoLoweringIsAPlaceholderSpanningIt() {
        val file = createPsiFile("placeholder", "a\n😀b") as ElixirFile

        val placeholder = lower(file) as ElixirAst.Placeholder

        assertEquals(file.javaClass, (placeholder.reason as ElixirAst.Placeholder.Reason.Unlowered).shape)
        assertEquals(TextRange(0, 5), placeholder.meta.origin)
        assertPosition(1, 1, placeholder.meta.start)
        // the emoji is two UTF-16 chars but one code point
        assertPosition(2, 3, placeholder.meta.end)
        assertEquals(
            OtpErlangTuple(arrayOf(OtpErlangAtom("__cursor__"), OtpErlangList(), OtpErlangList())),
            placeholder.toOtp()
        )
    }

    fun testAnEndAfterANewlineIsTheStartOfTheNextLine() {
        val file = createPsiFile("trailing_newline", "a\n") as ElixirFile

        assertPosition(2, 1, lower(file).meta.end)
    }

    fun testAShapeNoRowNamesIsUnknownNotNoNode() =
        assertEquals(Lowering.Bucket.UNKNOWN, Lowering.classifier.classify(PsiErrorElementImpl::class.java))

    fun testQualifiedMultipleAliasesAndTheirAliasListAreCalls() = assertEquals(
        listOf(Lowering.Bucket.CALL, Lowering.Bucket.CALL),
        listOf(ElixirMatchedQualifiedMultipleAliasesImpl::class.java, ElixirMultipleAliasesImpl::class.java)
            .map { Lowering.classifier.classify(it) }
    )

    private fun lower(file: ElixirFile): ElixirAst =
        ReadAction.computeBlocking<ElixirAst, Throwable> { Lowering.lower(file, ElixirLanguageLevel.FALLBACK) }

    private fun assertPosition(line: Int, column: Int, position: Meta.Position) =
        assertEquals("$line:$column", "${position.line}:${position.column}")
}
