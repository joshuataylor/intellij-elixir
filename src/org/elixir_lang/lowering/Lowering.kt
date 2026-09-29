package org.elixir_lang.lowering

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.walk.ShapeTable

/** Lowers PSI to [ElixirAst]. Which lowering a shape gets is its [ShapeTable] row's `lowering` bucket. */
class Lowering private constructor(val languageLevel: ElixirLanguageLevel, private val lines: Lines) {
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
         * clauses of `do` and keyword blocks.
         */
        CALL,

        /** `fn`, stab clauses and their signatures. */
        CLAUSE,

        /** Module attributes: `@name`, `@name value`, `@name[key]`. */
        ATTRIBUTE,

        /**
         * No `quote()` of its own; the shape above it reads it as it lowers: most argument lists, the parts of strings,
         * heredocs and sigils, escape sequences, `do` blocks.
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

            return Lowering(languageLevel, Lines(file.text)).lower(file)
        }
    }

    private fun lower(element: PsiElement): ElixirAst {
        ProgressManager.checkCanceled()

        return ElixirAst.Placeholder(meta(element), ElixirAst.Placeholder.Reason.Unlowered(element.javaClass))
    }

    private fun meta(element: PsiElement): Meta {
        val range = element.textRange

        return Meta(range, lines.position(range.startOffset), lines.position(range.endOffset))
    }
}
