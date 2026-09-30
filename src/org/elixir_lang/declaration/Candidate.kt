package org.elixir_lang.declaration

/** A declaration a use could mean: how resolution reached it, and whether it applies there. */
data class Candidate(val declaration: Declaration, val reach: Reach, val applicability: Applicability) {
    companion object {
        /** `null` when [declaration] is not a candidate for the use, as [Applicability.of] decides. */
        fun of(declaration: Declaration, reach: Reach, reachedAs: String, valid: Boolean): Candidate? =
            Applicability.of(declaration, reachedAs, valid)?.let { Candidate(declaration, reach, it) }
    }
}
