package org.elixir_lang.injection.markdown

import com.intellij.openapi.util.TextRange
import org.elixir_lang.injection.PsiLanguageInjectionHost.isDocumentation
import org.elixir_lang.psi.ElixirLine
import org.elixir_lang.psi.HeredocLiteral
import org.elixir_lang.psi.Parent

class LiteralTextEscaper(parent: Parent) : com.intellij.psi.LiteralTextEscaper<Parent>(parent) {
    /**
     * The range the paired `offsets` (or its absence, when `null`) was decoded for.
     *
     * Read locks are shared, not exclusive, so two threads can call [decode]/[getOffsetInHost] on the
     * same escaper instance concurrently for two different ranges. Bundling the range and its offsets
     * into one immutable pair behind a single `@Volatile` reference means a reader always sees a range
     * and its own matching offsets together, never one thread's range paired with another's table.
     */
    private class Decoded(val range: TextRange, val offsets: IntArray?)

    @Volatile
    private var decoded: Decoded? = null

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val contentRanges = markdownContentRanges(rangeInsideHost)

        if (contentRanges.isEmpty()) {
            decoded = Decoded(rangeInsideHost, null)
            outChars.append(rangeInsideHost.substring(myHost.text))

            return true
        }

        val hostText = myHost.text
        val offsets = IntArray(rangeInsideHost.length + 1)
        offsets.fill(rangeInsideHost.endOffset)
        var offsetInDecoded = 0

        for (contentRange in contentRanges) {
            for (offsetInContent in 0 until contentRange.length) {
                offsets[offsetInDecoded++] = contentRange.startOffset + offsetInContent
            }

            outChars.append(contentRange.substring(hostText))
        }

        decoded = Decoded(rangeInsideHost, offsets)

        return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        // The contract does not guarantee decode() was last called for this same range - answering from
        // a stale offsets table built for a different range would silently mismatch, so recompute rather
        // than assume.
        var current = decoded

        if (current == null || current.range != rangeInsideHost) {
            decode(rangeInsideHost, StringBuilder())
            current = decoded
        }

        val offsets = current?.offsets ?: return rangeInsideHost.startOffset + offsetInDecoded

        return if (offsetInDecoded in offsets.indices) offsets[offsetInDecoded] else -1
    }

    override fun getRelevantTextRange(): TextRange =
        when (val host = myHost) {
            is HeredocLiteral -> markdownRangeInHost(host) ?: TextRange.from(1, 0)
            is ElixirLine -> host.lineBody?.textRangeInParent ?: TextRange.from(1, 0)
            else -> super.getRelevantTextRange()
        }

    override fun isOneLine(): Boolean = false

    /**
     * The Markdown [rangeInsideHost] covers, clipped to it; empty when it covers none, which is how the
     * Elixir injected into a documentation code block is recognised and decoded verbatim.
     *
     * Clipping rather than matching the range against the registered places matters because
     * `InjectionRegistrarImpl.reparse` hands back a range grown from the shred's own marker, which is
     * greedy at both ends, and against a host in the uncommitted tree. A grown Markdown range still covers
     * its own content, and a code block's indent and prompt are not host ranges at all, so a grown Elixir
     * range still covers none.
     */
    private fun markdownContentRanges(rangeInsideHost: TextRange): List<TextRange> {
        val host = myHost as? HeredocLiteral ?: return emptyList()

        if (!isDocumentation(host)) return emptyList()

        return markdownInjection(host)
            .contentRanges
            .mapNotNull { contentRange -> contentRange.intersection(rangeInsideHost) }
            .filterNot { it.isEmpty }
    }
}
