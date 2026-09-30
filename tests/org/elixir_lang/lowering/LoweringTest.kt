package org.elixir_lang.lowering

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.junit.logs.expectErrors
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.psi.ElixirEndOfExpression
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.ElixirInterpolation
import org.elixir_lang.psi.impl.ElixirMatchedQualifiedMultipleAliasesImpl
import org.elixir_lang.psi.impl.ElixirMultipleAliasesImpl
import org.elixir_lang.psi.impl.ElixirParentheticalStabImpl
import org.elixir_lang.psi.impl.ElixirStabBodyImpl
import org.elixir_lang.psi.impl.ElixirStabImpl

class LoweringTest : LoweringTestCase() {
    fun testAShapeNoRowNamesIsUnknownNotNoNode() =
        assertEquals(Lowering.Bucket.UNKNOWN, Lowering.classifier.classify(PsiErrorElementImpl::class.java))

    fun testQualifiedMultipleAliasesAndTheirAliasListAreCalls() = assertEquals(
        listOf(Lowering.Bucket.CALL, Lowering.Bucket.CALL),
        listOf(ElixirMatchedQualifiedMultipleAliasesImpl::class.java, ElixirMultipleAliasesImpl::class.java)
            .map { Lowering.classifier.classify(it) }
    )

    fun testStabsTheirBodiesAndParenthesesAreBlocks() = assertEquals(
        listOf(Lowering.Bucket.BLOCK, Lowering.Bucket.BLOCK, Lowering.Bucket.BLOCK),
        listOf(ElixirStabImpl::class.java, ElixirStabBodyImpl::class.java, ElixirParentheticalStabImpl::class.java)
            .map { Lowering.classifier.classify(it) }
    )

    fun testAShapeItsParentReadsFailsOnItsOwn() =
        assertFailsOnItsOwn("\"#{1}\"", ElixirInterpolation::class.java)

    fun testAShapeWithNoNodeFailsOnItsOwn() = assertFailsOnItsOwn("1\n2", ElixirEndOfExpression::class.java)

    private fun <T : PsiElement> assertFailsOnItsOwn(code: String, shape: Class<T>) {
        val file = createPsiFile(getTestName(false), code) as ElixirFile
        val element = PsiTreeUtil.findChildOfType(file, shape)!!

        val errors = expectErrors(Lowering::class.java, Regex(".*${shape.simpleName}.*")) {
            val lowered = ReadAction.computeBlocking<ElixirAst, Throwable> {
                Lowering.of(file, ElixirLanguageLevel.FALLBACK).lower(element)
            }

            assertInstanceOf((lowered as ElixirAst.Placeholder).reason, ElixirAst.Placeholder.Reason.Unlowered::class.java)
        }

        assertEquals(1, errors.size)
    }
}
