package org.elixir_lang.declaration

import org.elixir_lang.psi.ArityInterval
import org.elixir_lang.psi.arityKnowledge
import org.junit.Assert.assertEquals
import org.junit.Test

class ArityKnowledgeTest {
    @Test
    fun oneArityIsExact() {
        assertEquals(ArityKnowledge.Exact(2), ArityInterval(2, 2).arityKnowledge())
    }

    @Test
    fun defaultArgumentsGiveARange() {
        assertEquals(ArityKnowledge.Range(1, 3), ArityInterval(1, 3).arityKnowledge())
    }

    @Test
    fun unquoteSplicingAfterAParameterIsOpenAboveIt() {
        assertEquals(ArityKnowledge.Open(1), ArityInterval(1, null).arityKnowledge())
    }

    @Test
    fun noMinimumIsAnyArity() {
        assertEquals(ArityKnowledge.Open(0), ArityInterval(0, null).arityKnowledge())
    }
}
