package org.elixir_lang.declaration

import org.elixir_lang.declaration.Applicability.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvedTest {
    private data class Reached(val candidate: Candidate, val delegation: Boolean = false)

    private fun reached(applicability: Applicability, delegation: Boolean = false, offset: Int = 0) =
        Reached(
            Candidate(declaration("f", ArityKnowledge.Exact(1), offset = offset), Reach.OWN, applicability),
            delegation
        )

    private fun resolved(candidates: List<Reached>) = Resolved.of(candidates, Reached::candidate, Reached::delegation)

    @Test
    fun theFirstValidCandidateInPreferenceOrder() {
        val first = reached(VALID, offset = 1)

        assertEquals(first, resolved(listOf(reached(WRONG_ARITY), first, reached(VALID, offset = 2))))
    }

    @Test
    fun neverACandidateThatIsNotValid() {
        for (applicability in listOf(WRONG_ARITY, PRIVATE, OPAQUE)) {
            assertNull(applicability.name, resolved(listOf(reached(applicability))))
        }
    }

    @Test
    fun aValidDelegationBeforeWhatItDelegatesTo() {
        val delegation = reached(VALID, delegation = true, offset = 1)

        assertEquals(delegation, resolved(listOf(reached(VALID), delegation)))
    }

    @Test
    fun aDelegationThatIsNotValidDoesNotOutrankAValidDefinition() {
        val definition = reached(VALID)

        assertEquals(definition, resolved(listOf(reached(WRONG_ARITY, delegation = true, offset = 1), definition)))
    }
}
