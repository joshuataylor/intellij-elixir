package org.elixir_lang.psi.impl

import com.ericsson.otp.erlang.OtpErlangAtom
import com.intellij.openapi.util.Computable
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.Macro.KEYWORD_BLOCK_KEYWORDS
import org.elixir_lang.mix.project.computeReadAction
import org.elixir_lang.psi.ElixirKeywordPair
import org.elixir_lang.psi.ElixirNoParenthesesKeywordPair
import org.elixir_lang.psi.Quotable
import org.elixir_lang.psi.QuotableKeywordList
import org.elixir_lang.psi.QuotableKeywordPair

fun QuotableKeywordPair.hasKeywordKey(keywordKeyText: String): Boolean {
    val keywordKey = keywordKey
    var has = false

    if (computeReadAction(Computable<String> { keywordKey.text }) == keywordKeyText) {
        has = true
    } else {
        val quotedKeywordKey = keywordKey.quote()

        if (quotedKeywordKey is OtpErlangAtom) {

            if (quotedKeywordKey.atomValue() == keywordKeyText) {
                has = true
            }
        }
    }

    return has
}

/**
 * This pair's key when it is a key of [KEYWORD_BLOCK_KEYWORDS] in a keyword list that also has `do`, as `else` in
 * `if c, do: a, else: b`, but not in `Keyword.keys(else: b)`.
 */
@RequiresReadLock
fun QuotableKeywordPair.blockKeyword(): String? =
    keywordAtom()?.takeIf { keyword ->
        keyword in KEYWORD_BLOCK_KEYWORDS &&
            (keyword == "do" ||
                (parent as? QuotableKeywordList)?.quotableKeywordPairList().orEmpty().any { it.keywordAtom() == "do" })
    }

private fun QuotableKeywordPair.keywordAtom(): String? {
    val text = keywordKey.text

    return if (text in KEYWORD_BLOCK_KEYWORDS) text else (keywordKey.quote() as? OtpErlangAtom)?.atomValue()
}

object QuotableKeywordPairImpl {
    @JvmStatic
    fun getKeywordValue(keywordPair: ElixirKeywordPair): Quotable {
        val children = keywordPair.children

        assert(children.size >= 2)

        return children[1].stripAccessExpression() as Quotable
    }

    @JvmStatic
    fun getKeywordValue(noParenthesesKeywordPair: ElixirNoParenthesesKeywordPair): Quotable {
        val children = noParenthesesKeywordPair.children

        assert(children.size >= 2)

        return children[1].stripAccessExpression() as Quotable
    }
}
