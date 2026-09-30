package org.elixir_lang.lowering

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import org.elixir_lang.language_level.ElixirLanguageFeature.CLOSING_FIRST_IN_PARENS
import org.elixir_lang.language_level.ElixirLanguageFeature.ENCLOSING_PARENS_MERGE_BLOCK_METADATA
import org.elixir_lang.language_level.ElixirLanguageFeature.END_OF_EXPRESSION_ON_LAST_EXPRESSION
import org.elixir_lang.language_level.ElixirLanguageFeature.LINE_METADATA_ON_BLOCK
import org.elixir_lang.language_level.ElixirLanguageFeature.PARENS_ON_PARENTHESIZED_EXPRESSION
import org.elixir_lang.language_level.ElixirLanguageFeature.SOLITARY_UNARY_WRAPPED_IN_EVERY_BLOCK
import org.elixir_lang.psi.ElixirEmptyParentheses
import org.elixir_lang.psi.ElixirEndOfExpression
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.ElixirInterpolation
import org.elixir_lang.psi.ElixirParentheticalStab
import org.elixir_lang.psi.ElixirStab
import org.elixir_lang.psi.ElixirStabBody

/** The one block builder: the file, interpolation, stabs and their bodies, and parentheses. */
object Blocks {
    /** What encloses a block and gives it metadata of its own. */
    sealed class Enclosing {
        class Parentheses(val opening: Meta.Position, val closing: Meta.Position) : Enclosing()

        /** A `do` token, which gives its block metadata only from 1.20. */
        class Do(val opening: Meta.Position) : Enclosing()
    }
}

internal fun Lowering.block(element: PsiElement): ElixirAst =
    when (element) {
        is ElixirParentheticalStab -> parentheticalStab(element)
        is ElixirStab -> stab(element, null)
        is ElixirStabBody -> stabBody(element, null)
        else -> unlowered(element)
    }

/** A stab's body as a block, or its `->` clauses as a list. */
internal fun Lowering.stab(stab: ElixirStab, enclosing: Blocks.Enclosing?): ElixirAst =
    stab.stabBody?.let { stabBody(it, enclosing) }
        ?: ElixirAst.ListNode(meta(stab), stab.stabOperationList.map { lower(it) })

internal fun Lowering.stabBody(stabBody: ElixirStabBody, enclosing: Blocks.Enclosing?): ElixirAst =
    buildBlock(expressions(stabBody), stabBody, enclosing)

internal fun Lowering.file(file: ElixirFile): ElixirAst = topLevel(file, file.textRange.startOffset)

/** An interpolation's body: its own file to Elixir's parser, whose empty block is at line 1 whatever its place. */
internal fun Lowering.interpolation(interpolation: ElixirInterpolation): ElixirAst =
    topLevel(interpolation, interpolation.node.firstChildNode.textRange.endOffset)

private fun Lowering.topLevel(element: PsiElement, bodyStart: Int): ElixirAst {
    val expressions = expressions(element)

    return if (expressions.isEmpty()) {
        val location =
            if (isAvailable(LINE_METADATA_ON_BLOCK)) {
                Meta.Key.Location(Meta.Position(1, 1))
            } else {
                endOfExpression(bodyStart)?.let { location(it.offset) }
            }

        ElixirAst.Block(meta(element, location), emptyList())
    } else {
        buildBlock(expressions, element, null)
    }
}

private fun Lowering.parentheticalStab(parentheticalStab: ElixirParentheticalStab): ElixirAst {
    val node = parentheticalStab.node
    val opening = node.firstChildNode.startOffset
    val closing = node.lastChildNode.startOffset
    val stab = parentheticalStab.stab
        ?: return ElixirAst.Block(meta(parentheticalStab, closing(closing), location(opening)), emptyList())

    return stab(stab, Blocks.Enclosing.Parentheses(position(opening), position(closing)))
}

/** `()`, which only has metadata from 1.18. */
internal fun Lowering.emptyParentheses(emptyParentheses: ElixirEmptyParentheses): ElixirAst {
    val node = emptyParentheses.node
    val enclosing = Blocks.Enclosing.Parentheses(
        position(node.firstChildNode.startOffset),
        position(node.lastChildNode.startOffset)
    )

    return ElixirAst.Block(
        meta(emptyParentheses, parens(enclosing).takeIf { isAvailable(PARENS_ON_PARENTHESIZED_EXPRESSION) }),
        emptyList()
    )
}

/**
 * The expressions of [parent], each followed by an end of expression carrying it as `end_of_expression`: all but the
 * last, and the last too from 1.17.
 */
private fun Lowering.expressions(parent: PsiElement): List<ElixirAst> {
    val children = expressionNodes(parent)

    return children.mapIndexed { index, child ->
        val lowered = unaryEllipsis(child.psi)?.let { unlowered(child.psi, it) } ?: lower(child.psi)
        val endOfExpression =
            if (index < children.lastIndex || isAvailable(END_OF_EXPRESSION_ON_LAST_EXPRESSION)) {
                endOfExpression(contentEnd(child))
            } else {
                null
            }

        if (endOfExpression != null) decorate(lowered, endOfExpressionKey(endOfExpression)) else lowered
    }
}

internal fun expressionNodes(parent: PsiElement): List<ASTNode> =
    generateSequence(parent.node.firstChildNode, ASTNode::getTreeNext)
        .filter { it !is LeafElement && it.psi !is ElixirEndOfExpression }
        .toList()

/** `end_of_expression:`, as the parser adds it to the expression [endOfExpression] follows. */
internal fun Lowering.endOfExpressionKey(endOfExpression: Lowering.EndOfExpression): Meta.Key =
    Meta.Key.Entry(
        "end_of_expression",
        Meta.Value.Keywords(
            listOf(
                Meta.Key.Entry("newlines", Meta.Value.Integer(endOfExpression.newlines.toLong())),
                location(endOfExpression.offset)
            )
        ),
        tokenMetadata = true
    )

/**
 * [expressions] as one expression, as Elixir's `build_block` and `build_paren_stab` make it: a lone expression is
 * itself, except that some are wrapped in a `__block__` and, inside [enclosedBy], some take its metadata.
 */
internal fun Lowering.buildBlock(
    expressions: List<ElixirAst>,
    origin: PsiElement,
    enclosedBy: Blocks.Enclosing?
): ElixirAst {
    val enclosing = enclosedBy.takeUnless { it is Blocks.Enclosing.Do && !isAvailable(LINE_METADATA_ON_BLOCK) }
    val blockMeta = meta(origin, *blockKeys(enclosing).toTypedArray())
    val single = expressions.singleOrNull() ?: return ElixirAst.Block(blockMeta, expressions)
    val rearranged = single.isLocalCall("not", 1) || single.isLocalCall("!", 1)

    return when {
        single.isLocalCall("unquote_splicing", 1) -> ElixirAst.Block(blockMeta, expressions)
        rearranged && isAvailable(SOLITARY_UNARY_WRAPPED_IN_EVERY_BLOCK) -> ElixirAst.Block(blockMeta, expressions)
        rearranged && enclosing is Blocks.Enclosing.Parentheses -> ElixirAst.Block(meta(origin), expressions)
        enclosing == null || !single.hasMetadata() -> single
        single is ElixirAst.Block && enclosing is Blocks.Enclosing.Parentheses &&
            isAvailable(ENCLOSING_PARENS_MERGE_BLOCK_METADATA) ->
            decorate(single, *blockKeys(enclosing).toTypedArray(), placement = Lowering.Placement.LAST)
        isAvailable(PARENS_ON_PARENTHESIZED_EXPRESSION) -> decorate(single, parens(enclosing))
        else -> single
    }
}

private fun Lowering.blockKeys(enclosing: Blocks.Enclosing?): List<Meta.Key> =
    when (enclosing) {
        is Blocks.Enclosing.Parentheses -> listOf(closing(enclosing.closing), Meta.Key.Location(enclosing.opening))
        is Blocks.Enclosing.Do -> listOf(Meta.Key.Location(enclosing.opening))
        null -> emptyList()
    }

internal fun Lowering.parens(enclosing: Blocks.Enclosing): Meta.Key {
    val keys = when (enclosing) {
        is Blocks.Enclosing.Parentheses ->
            if (isAvailable(CLOSING_FIRST_IN_PARENS)) {
                listOf(closing(enclosing.closing), Meta.Key.Location(enclosing.opening))
            } else {
                listOf(Meta.Key.Location(enclosing.opening), closing(enclosing.closing))
            }
        is Blocks.Enclosing.Do -> listOf(Meta.Key.Location(enclosing.opening))
    }

    return Meta.Key.Entry("parens", Meta.Value.Keywords(keys), tokenMetadata = true)
}

private fun ElixirAst.isLocalCall(name: String, arity: Int): Boolean =
    this is ElixirAst.Call && (callee as? ElixirAst.Literal.Atom)?.name == name && arguments?.size == arity
