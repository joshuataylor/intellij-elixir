package org.elixir_lang.psi.scope.callable_table

import com.intellij.psi.PsiManager
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.PlatformTestCase

/**
 * The table looks a name up by the atom each definition declares. A head named `unquote(name)` declares no atom, so a
 * use the walk names by its text `unquote` no longer reaches it, as a prefix match, the way walking the module did.
 * A head named by a literal atom is still found by that atom. Nor does a use reach, as a prefix match, a name whose
 * combining mark composes with the character the use ends at: `snoc` and the atom `snoć` that `snoc` + U+0301 quotes
 * to.
 */
class AnswerChangeTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
    }

    fun testCallbackHeadNamedByUnquote() = assertNoLongerReachesUnquoteName("unquotes.ex:16:13")
    fun testDelegationHeadNamedByUnquote() = assertNoLongerReachesUnquoteName("unquotes.ex:18:15")
    fun testUseNamedByUnquote() = assertNoLongerReachesUnquoteName("unquotes.ex:28:31")

    fun testLiteralAtomHeadIsFoundByItsAtom() {
        val dump = dump(UNQUOTES)

        for (incompleteCode in listOf(false, true)) {
            assertEquals(
                listOf("unquotes.ex:7:3"),
                multiResolve(dump, "unquotes.ex:27:24", incompleteCode, table = true)
            )
        }
    }

    fun testCallNoLongerReachesComposedLookalike() =
        assertNoLongerReaches(dump(COMBINING), "caller.ex:6:31", "def.ex:10:3 (invalid)", incompleteCode = true)

    fun testDelegationNoLongerReachesComposedLookalike() = assertNoLongerReaches(
        dump(COMBINING), "defdelegate.ex:2:15", "defdelegate.ex:9:3 (invalid)", incompleteCode = true
    )

    private fun assertNoLongerReachesUnquoteName(location: String) {
        val dump = dump(UNQUOTES)

        for (incompleteCode in listOf(false, true)) {
            assertNoLongerReaches(dump, location, UNQUOTE_NAME, incompleteCode)
        }
    }

    private fun assertNoLongerReaches(dump: WalkDump, location: String, result: String, incompleteCode: Boolean) {
        assertTrue(
            "walking the module reaches $result from $location",
            multiResolve(dump, location, incompleteCode, table = false).any { result in it }
        )
        assertFalse(
            "the table reaches $result from $location",
            multiResolve(dump, location, incompleteCode, table = true).any { result in it }
        )
    }

    private fun dump(directory: String): WalkDump =
        WalkDump(project, myFixture.copyDirectoryToProject("psi/scope/callable_table/$directory", ""))

    /** Every use starting at [location]: a call and the call applying it can share one. */
    private fun multiResolve(dump: WalkDump, location: String, incompleteCode: Boolean, table: Boolean): List<String> {
        val file = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(location.substringBefore(':')))!!
        val kind = "  incompleteCode=$incompleteCode  multiResolve  "

        return dump.lines(myFixture, listOf(file.virtualFile), table)
            .filter { it.startsWith("$location ") && kind in it }
            .map { it.substringAfter(kind) }
            .also { check(it.isNotEmpty()) { "no use at $location" } }
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"

    companion object {
        val CHANGED = setOf("unquotes.ex:16:13", "unquotes.ex:18:15", "unquotes.ex:28:31")

        /** The uses of a `snoc` name that walking the module answered with a `snoc` + U+0301 lookalike. */
        val COMBINING_CHANGED = setOf(
            "caller.ex:6:31", "caller.ex:7:29", "caller.ex:8:32", "caller.ex:13:38", "caller.ex:14:30",
            "caller.ex:15:29", "def.ex:12:29", "defdelegate.ex:2:15", "defdelegate.ex:3:15", "defdelegate.ex:4:15",
            "defdelegate.ex:11:29"
        )
        private const val UNQUOTES = "unquotes"
        private const val COMBINING = "combining"
        private const val UNQUOTE_NAME = "unquotes.ex:9:3 (invalid)"
    }
}
