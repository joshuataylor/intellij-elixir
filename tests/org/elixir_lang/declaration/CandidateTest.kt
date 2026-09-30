package org.elixir_lang.declaration

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandidateTest {
    private val declaration =
        Declaration(
            "f",
            ArityKnowledge.Exact(1),
            Definer.DEF.capabilities,
            Declared.Source(Form.CLAUSE, SourceOrigin(LightVirtualFile("m.ex"), 0, TextRange(0, 0)))
        )

    @Test
    fun aValidDeclarationKeepsHowItWasReached() {
        assertEquals(
            Candidate(declaration, Reach.IMPORT, Applicability.VALID),
            Candidate.of(declaration, Reach.IMPORT, "f", true)
        )
    }

    @Test
    fun aWrongArityIsStillACandidate() {
        assertEquals(
            Candidate(declaration, Reach.OWN, Applicability.WRONG_ARITY),
            Candidate.of(declaration, Reach.OWN, "f", false)
        )
    }

    @Test
    fun aNameTheUseOnlyStartsIsNoCandidate() {
        assertNull(Candidate.of(declaration.copy(name = "fun"), Reach.OWN, "f", false))
    }
}
