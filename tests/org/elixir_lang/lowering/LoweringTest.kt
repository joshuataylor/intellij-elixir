package org.elixir_lang.lowering

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
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
    fun testAnErrorElementIsAnError() =
        assertEquals(Lowering.Bucket.ERROR, Lowering.classifier.classify(PsiErrorElementImpl::class.java))

    fun testAShapeNoRowNamesIsUnknownNotNoNode() =
        assertEquals(Lowering.Bucket.UNKNOWN, Lowering.classifier.classify(LeafPsiElement::class.java))

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

    fun testAnAnonymousFunctionIsLeftUnlowered() = assertUnloweredIn("fn -> 1 end", Lowering.Bucket.CLAUSE)

    fun testAnAttributeIsLeftUnlowered() = assertUnloweredIn("@a 1", Lowering.Bucket.ATTRIBUTE)

    fun testAShapeNoRowNamesIsUnlowered() {
        val file = createPsiFile(getTestName(false), "1") as ElixirFile
        val leaf = PsiTreeUtil.getDeepestFirst(file)

        val lowered = ReadAction.computeBlocking<ElixirAst, Throwable> {
            Lowering.of(file, ElixirLanguageLevel.FALLBACK).lower(leaf)
        }

        assertEquals(leaf.javaClass, ((lowered as ElixirAst.Placeholder).reason as ElixirAst.Placeholder.Reason.Unlowered).shape)
    }

    fun testALiteralLowers() = assertLowers("1", "1")

    fun testParenthesesLowerAsABlock() = assertLowers("(1)", "1")

    fun testAShapeItsParentReadsFailsOnItsOwn() =
        assertFailsOnItsOwn("\"#{1}\"", ElixirInterpolation::class.java)

    fun testAShapeWithNoNodeFailsOnItsOwn() = assertFailsOnItsOwn("1\n2", ElixirEndOfExpression::class.java)

    private fun assertUnloweredIn(code: String, bucket: Lowering.Bucket) {
        val placeholders = placeholders(lower(code))

        assertFalse("no placeholder in $code", placeholders.isEmpty())
        placeholders.forEach { placeholder ->
            val shape = (placeholder.reason as ElixirAst.Placeholder.Reason.Unlowered).shape

            assertEquals(shape.name, bucket, Lowering.classifier.classify(shape))
        }
    }

    private fun placeholders(node: ElixirAst): List<ElixirAst.Placeholder> =
        when (node) {
            is ElixirAst.Placeholder -> listOf(node)
            is ElixirAst.Call -> placeholders(node.callee) + node.arguments.orEmpty().flatMap { placeholders(it) }
            is ElixirAst.Alias -> node.segments.flatMap { placeholders(it) }
            is ElixirAst.Literal -> emptyList()
            is ElixirAst.ListNode -> node.elements.flatMap { placeholders(it) }
            is ElixirAst.Tuple -> node.elements.flatMap { placeholders(it) }
            is ElixirAst.Block -> node.expressions.flatMap { placeholders(it) }
        }

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
