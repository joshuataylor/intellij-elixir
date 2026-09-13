package org.elixir_lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.psi.ElixirAccessExpression
import org.elixir_lang.psi.ElixirAnonymousFunction
import org.elixir_lang.psi.ElixirAtom
import org.elixir_lang.psi.ElixirAtomKeyword
import org.elixir_lang.psi.ElixirCharToken
import org.elixir_lang.psi.ElixirDotInfixOperator
import org.elixir_lang.psi.ElixirInterpolation
import org.elixir_lang.psi.ElixirMapOperation
import org.elixir_lang.psi.ElixirMatchedQualifiedAlias
import org.elixir_lang.psi.ElixirParentheticalStab
import org.elixir_lang.psi.ElixirQuoteHexadecimalEscapeSequence
import org.elixir_lang.psi.ElixirRelativeIdentifier
import org.elixir_lang.psi.ElixirTypes
import org.elixir_lang.psi.ElixirUnmatchedQualifiedAlias
import org.elixir_lang.psi.quoting.QuotingDialect
import org.elixir_lang.psi.quoting.QuotingDialectResolver

/**
 * Reports, as errors, constructs that Elixir's parser or string unescaping rejects and the plugin's parser accepts.
 */
internal class InvalidConstruct : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val problem = when (element) {
            is ElixirMatchedQualifiedAlias, is ElixirUnmatchedQualifiedAlias -> atomFollowedByAlias(element)
            is ElixirAnonymousFunction -> anonymousFunctionWithoutClause(element)
            is ElixirMapOperation -> spaceBeforeBrace(element)
            is ElixirQuoteHexadecimalEscapeSequence -> invalidCodePoint(element)
            else -> null
        } ?: return

        if (Injection.of(element) == Injection.UNCOMPILED) return

        holder.newAnnotation(HighlightSeverity.ERROR, problem.second).range(problem.first).create()
    }

    private fun atomFollowedByAlias(qualifiedAlias: PsiElement): Pair<TextRange, String>? {
        val qualifier = qualifiedAlias.firstChild ?: return null
        if (!isAtom(qualifier)) return null

        val dot = qualifiedAlias.children.firstOrNull { it is ElixirDotInfixOperator } ?: return null

        return dot.textRange to
            "atom cannot be followed by an alias. If the '.' was meant to be part of the atom's name, the atom name " +
            "must be quoted. Syntax error before: '.'"
    }

    /** An interpolated atom is a call to `:erlang.binary_to_atom/2`, which may be followed by an alias. */
    private fun isAtom(element: PsiElement): Boolean =
        when (element) {
            is ElixirAccessExpression -> element.children.singleOrNull()?.let(::isAtom) == true
            is ElixirAtom -> PsiTreeUtil.findChildOfType(element, ElixirInterpolation::class.java) == null
            is ElixirAtomKeyword -> true
            is ElixirParentheticalStab ->
                element.stab?.let { stab -> stab.stabOperationList.isEmpty() && stab.stabBody?.children?.singleOrNull()?.let(::isAtom) == true } == true
            else -> false
        }

    private fun anonymousFunctionWithoutClause(anonymousFunction: ElixirAnonymousFunction): Pair<TextRange, String>? {
        if (anonymousFunction.stab.stabOperationList.isNotEmpty()) return null

        val fn = anonymousFunction.node.findChildByType(ElixirTypes.FN) ?: return null

        return fn.textRange to "expected anonymous functions to be defined with -> inside: 'fn'"
    }

    /** Before 1.15 Elixir reported only a syntax error whose position depends on what follows; the later message is used. */
    private fun spaceBeforeBrace(mapOperation: ElixirMapOperation): Pair<TextRange, String>? {
        val prefix = mapOperation.mapPrefixOperator.textRange
        val arguments = mapOperation.mapArguments.textRange
        val between = mapOperation.containingFile.viewProvider.contents
            .subSequence(prefix.endOffset, arguments.startOffset)
            .toString()
            .replace("\\\r\n", " ")
            .replace("\\\n", " ")

        if (between.isEmpty() || between.any { it != ' ' && it != '\t' }) return null

        return TextRange(prefix.startOffset, arguments.startOffset + 1) to "unexpected space between % and {"
    }

    private fun invalidCodePoint(escape: ElixirQuoteHexadecimalEscapeSequence): Pair<TextRange, String>? {
        // Elixir unescapes a quoted remote call name only from 1.18. In `?\u{…}` it reads `?\u` and then reports a syntax
        // error before `{`.
        if (PsiTreeUtil.getParentOfType(escape, ElixirRelativeIdentifier::class.java, ElixirCharToken::class.java) != null) {
            return null
        }

        val digits = PsiTreeUtil.collectElements(escape) { it.node.elementType == ElixirTypes.VALID_HEXADECIMAL_DIGITS }
            .singleOrNull()
            ?.text
            ?: return null
        val codePoint = digits.toLongOrNull(16) ?: return null

        if (codePoint in 0xD800..0xDFFF || codePoint > 0x10FFFF) {
            val dialect = QuotingDialectResolver.dialectFor(escape)
            val hexadecimal = escape.hexadecimalEscapePrefix.text.endsWith("x")

            return escape.textRange to when {
                dialect < QuotingDialect.V1_12 -> "invalid or reserved Unicode code point $codePoint"
                hexadecimal && dialect >= QuotingDialect.V1_20 ->
                    "invalid hex escape character, expected \\xHH where H is a hexadecimal digit. Syntax error after: \\x"
                else -> "invalid or reserved Unicode code point \\u{$digits}. Syntax error after: \\u"
            }
        }

        return null
    }
}
