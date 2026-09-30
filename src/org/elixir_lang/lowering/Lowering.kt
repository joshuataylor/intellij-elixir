package org.elixir_lang.lowering

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.language_level.ElixirLanguageFeature
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.walk.ShapeTable

/** Lowers PSI to [ElixirAst]. Which lowering a shape gets is its [ShapeTable] row's `lowering` bucket. */
class Lowering private constructor(
    val languageLevel: ElixirLanguageLevel,
    private val text: CharSequence,
    private val lines: Lines,
) {
    /**
     * A shape with its own `quote()` belongs to a family, even when only its parent reaches it, as a digit or an
     * operator token does; [BY_PARENT] is for shapes without one.
     */
    enum class Bucket {
        /**
         * Literals and containers: numbers, atoms, aliases, strings, charlists, sigils, heredocs, lists, tuples, maps,
         * structs, bitstrings, keywords, and the file's own block.
         */
        LITERAL,

        /** Operators of every precedence, including captures, `..` and `..//`. */
        OPERATOR,

        /**
         * Calls, qualified or not, with or without parentheses and their keywords, `A.{B, C}`, bracket access, and the
         * `do` and keyword blocks of a call, whose bodies are [BLOCK]s.
         */
        CALL,

        /** Stabs, their bodies, and parentheses: what the one block builder builds. A stab's `->` is a [CLAUSE]. */
        BLOCK,

        /** `fn`, `->` and its signatures. */
        CLAUSE,

        /** Module attributes: `@name`, `@name value`, `@name[key]`. */
        ATTRIBUTE,

        /**
         * No `quote()` of its own; the shape above it reads it as it lowers: most argument lists, the parts of strings,
         * heredocs and sigils, escape sequences, interpolation, `do` blocks.
         */
        BY_PARENT,

        /** Contributes no node: an end of expression, EEx tags. */
        NOT_ALONE,

        /** No row names it: an error element, whitespace, a comment, a bare token. */
        UNKNOWN,
    }

    companion object {
        val classifier = ShapeTable.column(Bucket.UNKNOWN) { it.lowering }

        /** [file] lowered for [languageLevel], which the caller resolves once for the file. */
        @RequiresReadLock
        fun lower(file: ElixirFile, languageLevel: ElixirLanguageLevel): ElixirAst {
            ThreadingAssertions.assertReadAccess()

            return of(file, languageLevel).lower(file)
        }

        @RequiresReadLock
        internal fun of(file: ElixirFile, languageLevel: ElixirLanguageLevel): Lowering {
            ThreadingAssertions.assertReadAccess()

            val text = file.text

            return Lowering(languageLevel, text, Tokenization.lines(file, text, languageLevel))
        }
    }

    /** [element] lowered by its shape's family. */
    @RequiresReadLock
    internal fun lower(element: PsiElement): ElixirAst {
        ThreadingAssertions.assertReadAccess()
        ProgressManager.checkCanceled()

        return when (classifier.classify(element.javaClass)) {
            Bucket.LITERAL -> literal(element)
            Bucket.BLOCK -> block(element)
            Bucket.OPERATOR -> operator(element)
            Bucket.CALL -> call(element)
            Bucket.CLAUSE -> clause(element)
            Bucket.ATTRIBUTE -> attribute(element)
            Bucket.BY_PARENT, Bucket.NOT_ALONE -> {
                logger<Lowering>().error("${element.javaClass.simpleName} reached on its own: its parent's family lowers it")
                unlowered(element)
            }
            Bucket.UNKNOWN -> unlowered(element)
        }
    }

    internal fun unlowered(element: PsiElement): ElixirAst =
        ElixirAst.Placeholder(meta(element, location(element)), ElixirAst.Placeholder.Reason.Unlowered(element.javaClass))

    internal fun isAvailable(feature: ElixirLanguageFeature): Boolean = feature.isSufficient(languageLevel)

    /** Elixir's line and column for [offset]. */
    internal fun position(offset: Int): Meta.Position = lines.position(offset)

    internal fun meta(element: PsiElement, vararg keys: Meta.Key?): Meta = meta(element.textRange, *keys)

    internal fun meta(range: TextRange, vararg keys: Meta.Key?): Meta =
        Meta(range, position(range.startOffset), position(range.endOffset), keys.filterNotNull())

    internal fun location(offset: Int): Meta.Key = Meta.Key.Location(position(offset))

    internal fun location(element: PsiElement): Meta.Key = location(element.textRange.startOffset)

    internal fun location(node: ASTNode): Meta.Key = location(node.startOffset)

    internal fun closing(offset: Int): Meta.Key = closing(position(offset))

    internal fun closing(position: Meta.Position): Meta.Key =
        Meta.Key.Entry("closing", Meta.Value.Keywords(listOf(Meta.Key.Location(position))), tokenMetadata = true)

    /** `newlines:` for the newlines straight after an opening token ending before [offset], if any. */
    internal fun newlines(offset: Int): Meta.Key? =
        endOfExpression(offset)
            ?.takeIf { it.isNewline && it.newlines > 0 }
            ?.let { Meta.Key.Entry("newlines", Meta.Value.Integer(it.newlines.toLong()), tokenMetadata = true) }

    /** Where [decorate] puts a parent's keys among a child's own. */
    internal enum class Placement {
        /** Before them, as a parent adds `end_of_expression`, `parens` or `assoc`. */
        FIRST,

        /** After them, as parentheses merge their metadata into the block they enclose. */
        LAST,
    }

    /** [node] with [keys] added as a parent adds them to a child, and only to a node that has metadata. */
    internal fun decorate(node: ElixirAst, vararg keys: Meta.Key, placement: Placement = Placement.FIRST): ElixirAst =
        if (node.hasMetadata()) {
            node.withMeta(
                when (placement) {
                    Placement.FIRST -> keys.toList() + node.meta.keys
                    Placement.LAST -> node.meta.keys + keys
                }
            )
        } else {
            node
        }

    /** An end of expression, `;` or newlines, as the tokenizer merges them. */
    internal class EndOfExpression(val offset: Int, val newlines: Int, val isNewline: Boolean)

    /**
     * The end of expression token the tokenizer makes from the text at [offset], or `null` when the next token is
     * not one. Newlines merge into the `;` or newline token before them, a comment leaves the column where it began and
     * resets a newline token's count, and `\` + newline is space.
     */
    internal fun endOfExpression(offset: Int): EndOfExpression? {
        var first: EndOfExpression? = null
        var last: EndOfExpression? = null
        var commentStart: Int? = null
        var index = offset

        fun replaceLast(replacement: EndOfExpression) {
            if (first === last) first = replacement
            last = replacement
        }

        while (index < text.length) {
            when (text[index]) {
                ' ', '\t' -> index++
                '\\' ->
                    index += when {
                        text.startsWith("\\\n", index) -> 2
                        text.startsWith("\\\r\n", index) -> 3
                        else -> break
                    }
                '\r', '\n' -> {
                    val current = last

                    if (current != null) {
                        replaceLast(EndOfExpression(current.offset, current.newlines + 1, current.isNewline))
                    } else {
                        val newline = EndOfExpression(commentStart ?: index, 1, true)
                        if (first == null) first = newline
                        last = newline
                    }

                    commentStart = null
                    index += if (text[index] == '\r') 2 else 1
                }
                ';' -> {
                    if (last?.isNewline == false) break
                    val semicolon = EndOfExpression(index, 0, false)
                    if (first == null) first = semicolon
                    last = semicolon
                    index++
                }
                '#' -> {
                    commentStart = index
                    last?.takeIf { it.isNewline }?.let { replaceLast(EndOfExpression(it.offset, 0, true)) }
                    while (index < text.length && text[index] != '\n' && text[index] != '\r') index++
                }
                else -> break
            }
        }

        return first
    }
}

/** This node with [keys] in place of its own metadata keys, which only [Lowering.decorate] may change. */
private fun ElixirAst.withMeta(keys: List<Meta.Key>): ElixirAst {
    val meta = Meta(meta.origin, meta.start, meta.end, keys)

    return when (this) {
        is ElixirAst.Call -> ElixirAst.Call(meta, callee, arguments)
        is ElixirAst.Alias -> ElixirAst.Alias(meta, segments)
        is ElixirAst.Literal.Atom -> ElixirAst.Literal.Atom(meta, name)
        is ElixirAst.Literal.Integer -> ElixirAst.Literal.Integer(meta, value)
        is ElixirAst.Literal.Float -> ElixirAst.Literal.Float(meta, value)
        is ElixirAst.Literal.Binary -> ElixirAst.Literal.Binary(meta, bytes)
        is ElixirAst.ListNode -> ElixirAst.ListNode(meta, elements)
        is ElixirAst.Tuple -> ElixirAst.Tuple(meta, elements)
        is ElixirAst.Block -> ElixirAst.Block(meta, expressions)
        is ElixirAst.Placeholder -> ElixirAst.Placeholder(meta, reason)
    }
}
