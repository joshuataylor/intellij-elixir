package org.elixir_lang.declaration

/**
 * What a reference resolves to among [candidates][of]: a [Applicability.VALID] candidate or nothing. The others stay
 * candidates, offered by `multiResolve` to explain why a use does not compile, but are never its target.
 */
object Resolved {
    /**
     * The first valid of [candidates], which are in the order the reference prefers them, a delegation the use names
     * before what it delegates to; or nothing.
     */
    fun <T> of(candidates: List<T>, candidate: (T) -> Candidate, isDelegation: (T) -> Boolean): T? =
        DelegationPrecedence
            .namedFirst(candidates.filter { candidate(it).applicability == Applicability.VALID }, isDelegation)
            .firstOrNull()
}
