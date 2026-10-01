package org.elixir_lang.psi

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.blockKeyword
import org.elixir_lang.psi.impl.call.blockKeywordPairs
import org.elixir_lang.psi.impl.childExpressions
import org.elixir_lang.psi.impl.stripAccessExpression

/** The statements of an `if` or `unless` call's `do` and `else` bodies, written as blocks or as keywords. */
class Branches @RequiresReadLock constructor(call: Call) {
    val primaryChildExpressions: Sequence<PsiElement>
    val alternativeChildExpressions: Sequence<PsiElement>

    init {
        val doBlock = call.doBlock

        if (doBlock != null) {
            primaryChildExpressions = doBlock.stab?.stabBody?.childExpressions().orEmpty()
            alternativeChildExpressions = doBlock
                .blockList
                ?.blockItemList
                ?.firstOrNull { blockItem -> blockItem.blockIdentifier.textMatches("else") }
                ?.stab
                ?.stabBody
                ?.childExpressions()
                .orEmpty()
        } else {
            val pairs = call.blockKeywordPairs()

            primaryChildExpressions = keywordStatements(pairs, "do")
            alternativeChildExpressions = keywordStatements(pairs, "else")
        }
    }

    private fun keywordStatements(pairs: List<QuotableKeywordPair>, keyword: String): Sequence<PsiElement> =
        pairs.asSequence().filter { it.blockKeyword() == keyword }.flatMap { statements(it.keywordValue) }

    private fun statements(value: PsiElement): Sequence<PsiElement> =
        when (value) {
            is Call -> sequenceOf(value)
            is ElixirParentheticalStab -> value.stab?.stabBody?.childExpressions().orEmpty()
            is ElixirList -> value.childExpressions().map { it.stripAccessExpression() }
            else -> emptySequence()
        }
}
