package org.elixir_lang.psi.scope.callable_table

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.scope.CallableTable
import org.elixir_lang.psi.scope.WalkProbe
import org.elixir_lang.psi.scope.WalkProbe.Counter

/**
 * A module-level call in `Kernel` whose resolve builds `Kernel`'s table, where building it resolves that same call.
 * Each step answers as walking the module does, and no step re-enters a table while it is being built.
 */
class ReentryTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
        RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    }

    override fun tearDown() {
        try {
            CallableTable.enabled = true
            WalkProbe.reset()
        } finally {
            super.tearDown()
        }
    }

    fun testUnimported() = assertSteps(
        "reentry_unimported",
        "kernel.ex:8:3" to false,
        "kernel.ex:8:3" to true,
        "kernel.ex:16:27" to false,
        "kernel.ex:12:18" to false,
        "kernel.ex:12:18" to true
    )

    fun testQualifier() = assertSteps(
        "reentry_unimported",
        "kernel.ex:9:3" to false,
        "kernel.ex:9:3" to true,
        "kernel.ex:17:26" to false,
        "kernel.ex:12:18" to false,
        "kernel.ex:12:18" to true
    )

    fun testImported() = assertSteps(
        "reentry_imported",
        "kernel.ex:10:3" to true,
        "kernel.ex:10:3" to false,
        "kernel.ex:16:23" to false,
        "kernel.ex:12:18" to false,
        "kernel.ex:12:18" to true
    )

    /**
     * Each step resolves the use at a location, with `incompleteCode`. The last two resolve one use in both modes, so
     * the second finds `Kernel`'s table cached.
     */
    private fun assertSteps(directory: String, vararg steps: Pair<String, Boolean>) {
        val root = myFixture.copyDirectoryToProject("psi/scope/callable_table/$directory", "")
        val dump = WalkDump(project, root)
        val file = PsiManager.getInstance(project).findFile(root.findChild("kernel.ex")!!)!!
        val calls = PsiTreeUtil.findChildrenOfType(file, Call::class.java)
            .filter { it.reference is PsiPolyVariantReference }
            .associateBy { dump.location(it) }

        fun run(table: Boolean): List<Pair<String, Map<Counter, Long>>> {
            CallableTable.enabled = table
            // A file added is a PSI change, so this run computes every walk and table afresh.
            myFixture.addFileToProject("run_$table.ex", "")

            return steps.map { (location, incompleteCode) ->
                val reference = calls.getValue(location).reference as PsiPolyVariantReference
                WalkProbe.reset()
                val answer = WalkProbe.counting { reference.multiResolve(incompleteCode) }
                    .joinToString(" | ") { dump.location(it.element) + if (it.isValidResult) "" else " (invalid)" }

                "$location incompleteCode=$incompleteCode -> $answer" to WalkProbe.snapshot()
            }
        }

        val tabled = run(table = true)
        val live = run(table = false)

        assertEquals(live.joinToString("\n") { it.first }, tabled.joinToString("\n") { it.first })
        assertEquals(
            "tables re-entered while being built",
            steps.map { 0L },
            tabled.map { it.second.getValue(Counter.TABLE_REENTRY) }
        )
        val builds = tabled.map { it.second.getValue(Counter.TABLE_BUILD) }
        assertTrue("tables built per step: $builds", builds.sum() > 0 && builds.last() == 0L)
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"
}
