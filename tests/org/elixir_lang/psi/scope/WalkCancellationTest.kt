package org.elixir_lang.psi.scope

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.scope.WalkProbe.CancelPoint
import org.elixir_lang.psi.scope.WalkProbe.Counter
import org.elixir_lang.psi.scope.WalkTestSupport.location

/**
 * A resolve cancelled part-way through building what the walk caches: the caller gets the
 * [ProcessCanceledException] itself, nothing half-built is kept, and the next resolve answers as a fresh one does.
 */
class WalkCancellationTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
    }

    override fun tearDown() {
        try {
            WalkProbe.disarmCancel()
            WalkProbe.reset()
        } finally {
            super.tearDown()
        }
    }

    fun testKernelIndex() = withKernel {
        assertCancelled(CancelPoint.KERNEL_INDEX) { before, after ->
            // `Kernel`'s index again, then `Kernel.SpecialForms`', which the cancelled resolve never reached. More if
            // `Kernel`'s PSI is dropped and reloaded in between, which takes its cached index with it.
            val builds = after.getValue(Counter.KERNEL_INDEX_BUILD) - before.getValue(Counter.KERNEL_INDEX_BUILD)
            assertTrue("index builds by the retry: $builds", builds >= 2)
        }
    }

    /** One module, its first definition calling `target`, then [CLAUSES] `target/1` clauses. */
    private fun big() {
        myFixture.configureByText(
            "big.ex",
            buildString {
                appendLine("defmodule WalkCancellation.Big do")
                appendLine("  def entry(x), do: target(x)")
                for (i in 0 until CLAUSES) appendLine("  def target($i), do: $i")
                appendLine("end")
            }
        )
    }

    private fun withKernel(block: () -> Unit) =
        WalkTestSupport.withLibrary(project, myFixture.module, testRootDisposable, LIBRARY, WalkTestSupport.DOCS_KERNEL) {
            big()
            block()
        }

    private fun answers(): List<String> {
        val entrance = PsiTreeUtil.findChildrenOfType(myFixture.file, Call::class.java).single { it.text == "target(x)" }

        // `incompleteCode` keeps the walk going past the module's own clauses into the implicit imports.
        return listOf(false, true).map { incompleteCode ->
            "incompleteCode=$incompleteCode " + (entrance.reference as PsiPolyVariantReference).multiResolve(incompleteCode)
                .joinToString(" | ") { "${location(it.element)}${if (it.isValidResult) "" else " (invalid)"}" }
        }
    }

    private fun assertCancelled(point: CancelPoint, retried: (Map<Counter, Long>, Map<Counter, Long>) -> Unit) {
        WalkProbe.armCancel(point, K)
        val thrown = try {
            WalkProbe.counting { answers() }
            null
        } catch (throwable: Throwable) {
            throwable
        }

        assertTrue("$point was never reached", WalkProbe.disarmCancel())
        assertEquals(ProcessCanceledException::class.java, thrown?.javaClass)

        val before = WalkProbe.snapshot()
        val retry = WalkProbe.counting { answers() }
        retried(before, WalkProbe.snapshot())

        PsiManager.getInstance(project).dropPsiCaches()
        assertEquals("the retry against a fresh resolve", answers(), retry)
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"

    private companion object {
        const val LIBRARY = "walk_cancellation_kernel"
        const val CLAUSES = 100
        const val K = 50
    }
}
