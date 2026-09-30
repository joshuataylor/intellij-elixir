package org.elixir_lang.declaration

import org.elixir_lang.declaration.Reach.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ReachTest {
    @Test
    fun heldIsWhatTheModuleDeclaresOrAUseInjects() {
        assertEquals(setOf(OWN, USE), Reach.entries.filter { it.held }.toSet())
    }

    @Test
    fun remoteAddsTheTargetOfAHeldDelegation() {
        assertEquals(setOf(OWN, USE, DELEGATION_TARGET), Reach.entries.filter { it.remote }.toSet())
    }

    @Test
    fun delegationTargetIsEitherTarget() {
        assertEquals(
            setOf(DELEGATION_TARGET, UNHELD_DELEGATION_TARGET),
            Reach.entries.filter { it.delegationTarget }.toSet()
        )
    }

    @Test
    fun exportsWhatItHoldsThatIsPublic() {
        assertEquals(
            cross(setOf(OWN, USE), PUBLIC),
            admitted { reach, capabilities -> reach.exports(capabilities) }
        )
    }

    @Test
    fun exportsAtRuntimeOnlyPublicFunctions() {
        assertEquals(
            cross(setOf(OWN, USE), PUBLIC_FUNCTIONS),
            admitted { reach, capabilities -> reach.exports(capabilities, runtime = true) }
        )
    }

    @Test
    fun remotelyReachesWhatItExportsAndTheTargetsOfItsDelegations() {
        assertEquals(
            cross(setOf(OWN, USE, DELEGATION_TARGET), PUBLIC),
            admitted { reach, capabilities -> reach.remotelyReaches(capabilities) }
        )
    }

    @Test
    fun remotelyReachesAtRuntimeOnlyPublicFunctions() {
        assertEquals(
            cross(setOf(OWN, USE, DELEGATION_TARGET), PUBLIC_FUNCTIONS),
            admitted { reach, capabilities -> reach.remotelyReaches(capabilities, runtime = true) }
        )
    }

    private fun admitted(predicate: (Reach, Capabilities) -> Boolean): Set<Pair<Reach, Definer>> =
        Reach.entries.flatMap { reach ->
            Definer.entries.filter { predicate(reach, it.capabilities) }.map { reach to it }
        }.toSet()

    private fun cross(reaches: Set<Reach>, definers: Set<Definer>): Set<Pair<Reach, Definer>> =
        reaches.flatMap { reach -> definers.map { reach to it } }.toSet()

    private companion object {
        val PUBLIC_FUNCTIONS = setOf(Definer.DEF, Definer.DEFMEMO)
        val PUBLIC = PUBLIC_FUNCTIONS + setOf(Definer.DEFMACRO, Definer.DEFGUARD)
    }
}
