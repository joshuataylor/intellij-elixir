package org.elixir_lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.psi.ElixirAccessExpression
import org.elixir_lang.psi.ElixirAnonymousFunction
import org.elixir_lang.psi.ElixirAtom
import org.elixir_lang.psi.ElixirAtomKeyword
import org.elixir_lang.psi.ElixirCharToken
import org.elixir_lang.psi.ElixirDotInfixOperator
import org.elixir_lang.psi.ElixirEscapedCharacter
import org.elixir_lang.psi.ElixirInterpolation
import org.elixir_lang.psi.ElixirMapOperation
import org.elixir_lang.psi.ElixirMatchedMultiplicationOperation
import org.elixir_lang.psi.ElixirMatchedQualifiedAlias
import org.elixir_lang.psi.ElixirMultiplicationInfixOperator
import org.elixir_lang.psi.ElixirParentheticalStab
import org.elixir_lang.psi.ElixirQuoteHexadecimalEscapeSequence
import org.elixir_lang.psi.ElixirRelativeIdentifier
import org.elixir_lang.psi.ElixirTypes
import org.elixir_lang.psi.ElixirUnmatchedMultiplicationOperation
import org.elixir_lang.psi.ElixirUnmatchedQualifiedAlias
import org.elixir_lang.psi.Quote
import org.elixir_lang.psi.Sigil
import org.elixir_lang.psi.UnqualifiedNoArgumentsCall
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
            is ElixirEscapedCharacter -> invalidEscape(element)
            is ElixirAtom -> divisionAtom(element)
            is ElixirMatchedMultiplicationOperation, is ElixirUnmatchedMultiplicationOperation -> operatorReference(element)
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

    /** Elixir's tokenizer has no `//` atom: `:// 1` is `:/ / 1`, and before a closing token or the end `://` is an error. */
    private fun divisionAtom(atom: ElixirAtom): Pair<TextRange, String>? {
        val fragment = atom.node.findChildByType(ElixirTypes.ATOM_FRAGMENT)?.takeIf { it.text == "//" } ?: return null
        val next = generateSequence(PsiTreeUtil.nextVisibleLeaf(atom)) { PsiTreeUtil.nextVisibleLeaf(it) }
            .firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
        if (next == null || isFinalBackslash(next)) {
            return fragment.textRange to if (endsWithBackslash(atom)) INVALID_ESCAPE_AT_END else "syntax error before: "
        }

        val token = when {
            next.parent is ElixirInterpolation -> ""
            next.text in CLOSING_TOKENS -> "'${next.text}'"
            else -> return null
        }

        return fragment.textRange to "syntax error before: $token"
    }

    /**
     * Elixir rejects `=>` before `/` on every release, and `//` when a newline or line continuation comes before the `/`.
     * Elsewhere, `=>` in a map is the association and `//` differs by release.
     */
    private fun operatorReference(operation: PsiElement): Pair<TextRange, String>? {
        val operator = operation.children.firstOrNull { it is ElixirMultiplicationInfixOperator && it.text == "/" } ?: return null
        val operand = operation.firstChild as? UnqualifiedNoArgumentsCall<*> ?: return null
        val previous = generateSequence(PsiTreeUtil.prevLeaf(operand)) { PsiTreeUtil.prevLeaf(it) }
            .firstOrNull { it !is PsiWhiteSpace && it !is PsiComment && it.text.isNotBlank() }
            ?.text

        return when (operand.text) {
            "=>" -> if (previous in setOf(null, "&", "(", "=")) operand.textRange to "syntax error before: '=>'" else null
            "//" -> {
                val between = operation.containingFile.viewProvider.contents.subSequence(operand.textRange.endOffset, operator.textRange.startOffset)

                if ('\n' in between && (previous == null || previous == "&")) operator.textRange to "syntax error before: '/'" else null
            }
            else -> null
        }
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

    /**
     * Elixir unescapes a string, charlist, quoted atom or key as it parses, and `sigil_s`, `sigil_c` and `sigil_w` when the
     * code is compiled; other sigils receive an escape as written. A quoted call name is unescaped only from 1.18, which
     * [VersionedSyntax] reports. On 1.11 the hexadecimal message ends with the quote's opening delimiter, Elixir's token,
     * and a sigil's message never has the tokenizer's "Syntax error after".
     */
    private fun invalidEscape(escaped: ElixirEscapedCharacter): Pair<TextRange, String>? {
        val letter = escaped.lastChild?.text?.takeIf { it == "x" || it == "u" } ?: return null

        val holder = PsiTreeUtil.getParentOfType(escaped, Sigil::class.java, Quote::class.java, ElixirCharToken::class.java)
        val delimiter = when (holder) {
            is Sigil -> if (holder.sigilName() in UNESCAPING_SIGILS) "" else return null
            is Quote -> when (holder.parent) {
                is ElixirRelativeIdentifier -> return null
                is ElixirAtom -> ":" + holder.firstChild.text
                else -> holder.firstChild.text
            }
            else -> return null
        }

        val hexadecimal = letter == "x"

        return escaped.textRange to when {
            QuotingDialectResolver.dialectFor(escaped) < QuotingDialect.V1_12 ->
                if (hexadecimal) "missing hex sequence after \\x, expected \\xHH$delimiter"
                else "invalid Unicode sequence after \\u, expected \\uHHHH or \\u{H*}"
            holder is Sigil -> if (hexadecimal) INVALID_HEX_ESCAPE_WHEN_COMPILED else INVALID_UNICODE_ESCAPE_WHEN_COMPILED
            hexadecimal -> INVALID_HEX_ESCAPE
            else -> INVALID_UNICODE_ESCAPE
        }
    }

    private fun invalidCodePoint(escape: ElixirQuoteHexadecimalEscapeSequence): Pair<TextRange, String>? {
        // Elixir unescapes a quoted remote call name only from 1.18. In `?\u{…}` it reads `?\u` and then reports a syntax
        // error before `{`.
        when (PsiTreeUtil.getParentOfType(escape, ElixirRelativeIdentifier::class.java, ElixirCharToken::class.java)) {
            null -> Unit
            is ElixirRelativeIdentifier -> if (QuotingDialectResolver.dialectFor(escape) < QuotingDialect.V1_18) return null
            else -> return null
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
                hexadecimal && dialect >= QuotingDialect.V1_20 -> INVALID_HEX_ESCAPE
                else -> "invalid or reserved Unicode code point \\u{$digits}. Syntax error after: \\u"
            }
        }

        return null
    }
}

private val UNESCAPING_SIGILS = setOf("s", "c", "w")

private val CLOSING_TOKENS = setOf(",", ")", ">>", "]", "}")

internal const val INVALID_HEX_ESCAPE_WHEN_COMPILED = "invalid hex escape character, expected \\xHH where H is a hexadecimal digit"
internal const val INVALID_HEX_ESCAPE = "$INVALID_HEX_ESCAPE_WHEN_COMPILED. Syntax error after: \\x"

internal const val INVALID_UNICODE_ESCAPE_WHEN_COMPILED =
    "invalid Unicode escape character, expected \\uHHHH or \\u{H*} where H is a hexadecimal digit"
internal const val INVALID_UNICODE_ESCAPE = "$INVALID_UNICODE_ESCAPE_WHEN_COMPILED. Syntax error after: \\u"

internal const val INVALID_ESCAPE_AT_END = "invalid escape \\ at end of file"

/** Elixir's tokenizer stops first at a backslash, with or without a newline, that ends the file. */
internal fun endsWithBackslash(element: PsiElement): Boolean {
    val file = element.containingFile
    val text = file.viewProvider.contents
    val backslash = when {
        text.endsWith("\\\r\n") -> text.length - 3
        text.endsWith("\\\n") -> text.length - 2
        text.endsWith("\\") -> text.length - 1
        else -> return false
    }
    val leaf = file.findElementAt(backslash)

    return leaf is PsiWhiteSpace || isFinalBackslash(leaf)
}

internal fun isFinalBackslash(leaf: PsiElement?): Boolean =
    leaf != null && leaf.text == "\\" && leaf.textRange.endOffset == leaf.containingFile.textLength
