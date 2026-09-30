package org.elixir_lang.lowering

import com.ericsson.otp.erlang.OtpErlangAtom
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.intellij_elixir.Quoter
import org.elixir_lang.language_level.ElixirLanguageLevelResolver
import org.elixir_lang.psi.ElixirFile
import org.junit.Assert.assertEquals

/**
 * Holds a parser snippet's lowering, where nothing in it is left unlowered, equal to the quoter's quoting with columns
 * and token metadata. Prints one line per snippet, compared or skipped.
 */
object SnippetDifferential {
    private val COLUMNS_AND_TOKEN_METADATA = ParserOptions(columns = true, tokenMetadata = true)

    @JvmStatic
    fun assertLowersLikeTheQuoter(file: PsiFile) {
        val root = file.viewProvider.getPsi(ElixirLanguage) as ElixirFile
        val lowered = ReadAction.computeBlocking<ElixirAst, Throwable> {
            Lowering.lower(root, ElixirLanguageLevelResolver.languageLevelFor(root))
        }

        if (lowered.hasUnlowered()) {
            println("lowering skipped a snippet with something unlowered")
            return
        }

        println("lowering compared a snippet")

        val reply = Quoter.quote(file.text, COLUMNS_AND_TOKEN_METADATA)

        assertEquals("the quoter's reply to the snippet", OtpErlangAtom("ok"), reply.elementAt(0))
        Quoter.assertQuotedCorrectly(reply.elementAt(1), lowered.toOtp(COLUMNS_AND_TOKEN_METADATA))
    }
}

/** Whether any part of this was left to a family that has no lowering for it yet. */
internal fun ElixirAst.hasUnlowered(): Boolean =
    when (this) {
        is ElixirAst.Placeholder -> when (reason) {
            is ElixirAst.Placeholder.Reason.Unlowered -> true
        }
        is ElixirAst.Call -> callee.hasUnlowered() || arguments.orEmpty().any { it.hasUnlowered() }
        is ElixirAst.Alias -> segments.any { it.hasUnlowered() }
        is ElixirAst.Literal -> false
        is ElixirAst.ListNode -> elements.any { it.hasUnlowered() }
        is ElixirAst.Tuple -> elements.any { it.hasUnlowered() }
        is ElixirAst.Block -> expressions.any { it.hasUnlowered() }
    }
