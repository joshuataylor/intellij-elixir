package org.elixir_lang.lowering

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.language_level.ElixirLanguageFeature.END_OF_EXPRESSION_ON_LAST_EXPRESSION
import org.elixir_lang.language_level.ElixirLanguageFeature.END_OF_EXPRESSION_ON_STAB_OPERATOR
import org.elixir_lang.language_level.ElixirLanguageFeature.PARENS_ON_PARENTHESIZED_EXPRESSION
import org.elixir_lang.psi.Arguments
import org.elixir_lang.psi.ElixirAnonymousFunction
import org.elixir_lang.psi.ElixirKeywords
import org.elixir_lang.psi.ElixirParenthesesArguments
import org.elixir_lang.psi.ElixirStabBody
import org.elixir_lang.psi.ElixirStabInfixOperator
import org.elixir_lang.psi.ElixirStabNoParenthesesSignature
import org.elixir_lang.psi.ElixirStabOperation
import org.elixir_lang.psi.ElixirStabParenthesesSignature
import org.elixir_lang.psi.ElixirTypes

/** `fn` and `->`, whose signatures only their `->` reads. */
internal fun Lowering.clause(element: PsiElement): ElixirAst =
    when (element) {
        is ElixirAnonymousFunction -> anonymousFunction(element)
        is ElixirStabOperation -> stabOperation(element)
        else -> unlowered(element)
    }

/** `fn ... end`, which Elixir's parser rejects unless its stab is `->` clauses. */
private fun Lowering.anonymousFunction(anonymousFunction: ElixirAnonymousFunction): ElixirAst {
    val node = anonymousFunction.node
    val fn = node.firstChildNode
    val end = node.findChildByType(ElixirTypes.END)
    val stab = anonymousFunction.stab

    if (end == null || stab.stabBody != null) return broken(anonymousFunction)

    val newlines = endOfExpression(fn.textRange.endOffset)
        ?.takeUnless { it.isNewline && stab.stabOperationList.firstOrNull()?.firstChild is ElixirStabInfixOperator }
        ?.newlines
        ?.takeIf { it > 0 }
        ?.let { tokenMetadata("newlines", it) }

    return ElixirAst.Call(
        meta(anonymousFunction, newlines, closing(end.startOffset), location(fn)),
        ElixirAst.Literal.Atom(meta(fn.textRange), "fn"),
        stab.stabOperationList.map { lower(it) }
    )
}

private fun Lowering.stabOperation(stabOperation: ElixirStabOperation): ElixirAst {
    val operator = stabOperation.stabInfixOperator
    val signature = signature(stabOperation, operator)
    val stabBody = stabOperation.stabBody
    val followed = PsiTreeUtil.getNextSiblingOfType(stabOperation, ElixirStabOperation::class.java) != null
    val body = stabBody?.let { body(it, followed) } ?: ElixirAst.Literal.Atom(meta(operator), "nil")
    val arrow = ElixirAst.Call(
        meta(stabOperation, signature.parens, newlines(operator), location(operator)),
        ElixirAst.Literal.Atom(meta(operator), "->"),
        listOf(signature.arguments, body)
    )

    if (!isAvailable(END_OF_EXPRESSION_ON_STAB_OPERATOR)) return arrow

    val endOfExpression =
        if (stabBody != null) {
            arrowEndOfExpression(stabBody, body, followed)
        } else {
            endOfExpression(operator.textRange.endOffset)?.takeIf { followed }?.let { endOfExpressionKey(it) }
        }

    return endOfExpression?.let { decorate(arrow, it) } ?: arrow
}

/**
 * `newlines:` after [operator], or else those of an end of expression straight before it, which the tokenizer gives
 * to the operator after it.
 */
private fun Lowering.newlines(operator: PsiElement): Meta.Key? =
    newlines(operator.textRange.endOffset)
        ?: generateSequence(PsiTreeUtil.prevLeaf(operator)) { PsiTreeUtil.prevLeaf(it) }
            .firstOrNull { it !is PsiWhiteSpace && it !is PsiComment && it.node.elementType !in END_OF_EXPRESSION }
            ?.let { endOfExpression(it.textRange.endOffset) }
            ?.newlines
            ?.takeIf { it > 0 }
            ?.let { tokenMetadata("newlines", it) }

private val END_OF_EXPRESSION = TokenSet.create(ElixirTypes.EOL, ElixirTypes.SEMICOLON)

/** A `->`'s arguments, and the `parens:` its parentheses give the `->`. */
private class Signature(val arguments: ElixirAst, val parens: Meta.Key?)

private fun Lowering.signature(stabOperation: ElixirStabOperation, operator: PsiElement): Signature =
    stabOperation.stabParenthesesSignature?.let { parenthesesSignature(it) }
        ?: stabOperation.stabNoParenthesesSignature?.let { noParenthesesSignature(it) }
        ?: Signature(ElixirAst.ListNode(meta(TextRange.from(operator.textRange.startOffset, 0)), emptyList()), null)

private fun Lowering.noParenthesesSignature(signature: ElixirStabNoParenthesesSignature): Signature {
    val noParenthesesArguments = signature.noParenthesesArguments
    val arguments = (noParenthesesArguments.children.singleOrNull() as? Arguments)?.arguments()
        ?: noParenthesesArguments.children

    return Signature(
        ElixirAst.ListNode(meta(signature), unwrapWhen(unwrapSplice(arguments.map { lower(it) }))),
        null
    )
}

/**
 * `(...) -> ` or `(...) when guard ->`: parentheses around one argument that is not keywords are that expression's own,
 * as in `fn (x) -> x end`, and give the `->` nothing.
 */
private fun Lowering.parenthesesSignature(signature: ElixirStabParenthesesSignature): Signature {
    val parenthesesArguments = signature.parenthesesArguments
    val arguments = parenthesesArguments.arguments()
    val enclosing = parentheses(parenthesesArguments)
    val single = arguments.singleOrNull()?.takeUnless { it is ElixirKeywords }
    val lowered =
        if (single != null) {
            listOf(buildBlock(listOf(lower(single)), parenthesesArguments, enclosing))
        } else {
            arguments.map { lower(it) }
        }
    val unwrapped = unwrapSplice(lowered)
    val whenOperator = signature.whenInfixOperator
    val guard = whenOperator?.let { signature.children.dropWhile { child -> child != it }.getOrNull(1) }
    val elements =
        if (whenOperator != null) {
            val newlines = if (single != null) newlines(whenOperator) else null

            listOf(
                ElixirAst.Call(
                    meta(signature, newlines, location(whenOperator)),
                    ElixirAst.Literal.Atom(meta(whenOperator), "when"),
                    unwrapped + (guard?.let { lower(it) } ?: broken(signature))
                )
            )
        } else {
            unwrapped
        }
    val parens = if (single == null && isAvailable(PARENS_ON_PARENTHESIZED_EXPRESSION)) parens(enclosing) else null

    return Signature(ElixirAst.ListNode(meta(signature), elements), parens)
}

private fun Lowering.parentheses(parenthesesArguments: ElixirParenthesesArguments): Blocks.Enclosing.Parentheses {
    val node = parenthesesArguments.node

    return Blocks.Enclosing.Parentheses(
        position(node.firstChildNode.startOffset),
        position(node.lastChildNode.startOffset)
    )
}

/** The arguments without the `__block__` that parentheses put around a lone `unquote_splicing`. */
private fun unwrapSplice(arguments: List<ElixirAst>): List<ElixirAst> {
    val block = arguments.singleOrNull() as? ElixirAst.Block ?: return arguments
    val splice = block.expressions.singleOrNull() as? ElixirAst.Call ?: return arguments

    return if ((splice.callee as? ElixirAst.Literal.Atom)?.name == "unquote_splicing") listOf(splice) else arguments
}

/** `a, b when c` as one `when` over every argument, as `when` binds tighter than the commas before it. */
private fun unwrapWhen(arguments: List<ElixirAst>): List<ElixirAst> {
    val last = arguments.lastOrNull() as? ElixirAst.Call ?: return arguments
    val operands = last.arguments?.takeIf { it.size == 2 } ?: return arguments

    return if ((last.callee as? ElixirAst.Literal.Atom)?.name == "when") {
        listOf(ElixirAst.Call(last.meta, last.callee, arguments.dropLast(1) + operands))
    } else {
        arguments
    }
}

/**
 * [stabBody] as its block, whose last expression takes the end of expression before the next clause even where the
 * block builder leaves it off the last expression.
 */
private fun Lowering.body(stabBody: ElixirStabBody, followed: Boolean): ElixirAst {
    val nodes = expressionNodes(stabBody)
    val last = nodes.lastOrNull()
    val endOfExpression = last
        ?.takeIf { followed && !isAvailable(END_OF_EXPRESSION_ON_LAST_EXPRESSION) }
        ?.let { endOfExpression(contentEnd(it)) }
        ?: return lower(stabBody)
    val key = endOfExpressionKey(endOfExpression)

    return if (nodes.size == 1) {
        buildBlock(listOf(decorate(lower(last.psi), key)), stabBody, null)
    } else {
        val block = lower(stabBody) as ElixirAst.Block

        ElixirAst.Block(block.meta, block.expressions.dropLast(1) + decorate(block.expressions.last(), key))
    }
}

/**
 * Before 1.17, the end of expression after the body's first expression, when that expression has no metadata and the
 * end of expression is not the last before `end` or `)`.
 */
private fun Lowering.arrowEndOfExpression(stabBody: ElixirStabBody, body: ElixirAst, followed: Boolean): Meta.Key? {
    val nodes = expressionNodes(stabBody)
    val first = nodes.firstOrNull() ?: return null
    val firstExpression = if (nodes.size == 1) body else (body as ElixirAst.Block).expressions.first()

    return if ((nodes.size > 1 || followed) && !firstExpression.hasMetadata()) {
        endOfExpression(contentEnd(first))?.let { endOfExpressionKey(it) }
    } else {
        null
    }
}
