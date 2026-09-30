package org.elixir_lang.declaration

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicabilityTest {
    @Test
    fun validIsTheWalksFlag() {
        assertEquals(Applicability.VALID, Applicability.of(declaration("f", ArityKnowledge.Exact(1)), "f", true))
    }

    @Test
    fun validEvenUnderAnotherName() {
        assertEquals(Applicability.VALID, Applicability.of(declaration("bar", ArityKnowledge.Exact(1)), "foo", true))
    }

    @Test
    fun validEvenWhenTheAritiesAreUnknown() {
        assertEquals(Applicability.VALID, Applicability.of(declaration("f", ArityKnowledge.Unknown), "f", true))
    }

    @Test
    fun invalidAtTheUsesNameIsWrongArity() {
        for (arity in listOf(ArityKnowledge.Exact(2), ArityKnowledge.Range(2, 3), ArityKnowledge.Open(2))) {
            assertEquals(
                arity.toString(),
                Applicability.WRONG_ARITY,
                Applicability.of(declaration("f", arity), "f", false)
            )
        }
    }

    @Test
    fun invalidWithUnknownAritiesIsOpaque() {
        assertEquals(Applicability.OPAQUE, Applicability.of(declaration("f", ArityKnowledge.Unknown), "f", false))
    }

    @Test
    fun aPrivateDeclarationIsNeverPrivateYet() {
        assertEquals(
            listOf(Applicability.VALID, Applicability.WRONG_ARITY),
            listOf(true, false).map {
                Applicability.of(declaration("f", ArityKnowledge.Exact(1), Definer.DEFP), "f", it)
            }
        )
    }

    @Test
    fun aNameTheUseOnlyStartsIsNoCandidate() {
        for (arity in listOf(ArityKnowledge.Exact(1), ArityKnowledge.Unknown)) {
            assertNull(arity.toString(), Applicability.of(declaration("fun", arity), "f", false))
        }
    }

    private fun declaration(name: String, arity: ArityKnowledge, definer: Definer = Definer.DEF): Declaration =
        Declaration(
            name,
            arity,
            definer.capabilities,
            Declared.Source(Form.CLAUSE, SourceOrigin(LightVirtualFile("m.ex"), 0, TextRange(0, 0)))
        )
}
