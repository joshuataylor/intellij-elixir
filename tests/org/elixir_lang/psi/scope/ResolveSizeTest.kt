package org.elixir_lang.psi.scope

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.scope.WalkProbe.Counter

/**
 * The work one resolve does, at 10, 100 and 1,000 unrelated definitions. [USES] distinct uses sit at a fixed offset
 * from the module's end: the first may pay a linear build, and every later one must cost the same at every size.
 */
class ResolveSizeTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // At the platform's random rate a cached value is recomputed on a read, which a count of work would include.
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
    }

    override fun tearDown() {
        try {
            WalkProbe.reset()
        } finally {
            super.tearDown()
        }
    }

    /** A call that only the implicit `import Kernel` declares, with `Kernel` growing. */
    fun testKernel() = assertSizeIndependent(SIZES.associateWith(::kernel))

    private fun kernel(size: Int): List<Map<Counter, Long>> {
        val kernel = myFixture.addFileToProject(
            "kernel_$size.ex",
            buildString {
                appendLine("defmodule Kernel do")
                for (i in 0 until size) appendLine("  def kernel_unrelated_$i(a), do: a")
                appendLine("  def kernel_target(a), do: a")
                appendLine("end")
            }
        )
        val user = myFixture.addFileToProject(
            "kernel_user_$size.ex",
            buildString {
                appendLine("defmodule KernelUser$size do")
                for (i in 0 until USES) appendLine("  def use_$i, do: kernel_target($i)")
                appendLine("end")
            }
        )

        return try {
            measure(user, "kernel_target(")
        } finally {
            // Only one `Kernel` at a time, or the implicit import would walk every size's.
            WriteAction.runAndWait<Throwable> {
                kernel.virtualFile.delete(this)
                user.virtualFile.delete(this)
            }
        }
    }

    /** Per use of [marker], each counter's work for one resolve. */
    private fun measure(file: PsiFile, marker: String): List<Map<Counter, Long>> {
        val text = file.text
        val offsets = generateSequence(text.indexOf(marker)) { text.indexOf(marker, it + 1).takeIf { next -> next >= 0 } }
            .toList()
        check(offsets.size == USES) { "expected $USES uses of $marker, found ${offsets.size}" }

        return offsets.map { offset ->
            val reference = file.viewProvider.findReferenceAt(offset, org.elixir_lang.ElixirLanguage) as PsiPolyVariantReference
            WalkProbe.reset()
            val results = WalkProbe.counting { reference.multiResolve(false) }
            check(results.any { it.isValidResult }) { "the use at $offset of ${file.name} did not resolve" }

            WalkProbe.snapshot()
        }
    }

    private fun assertSizeIndependent(perSize: Map<Int, List<Map<Counter, Long>>>) {
        val failures = mutableListOf<String>()
        val smallest = SIZES.first()

        for (counter in Counter.entries) {
            val later = perSize.mapValues { (_, perUse) -> perUse.drop(1).map { it.getValue(counter) } }
            if (later.values.distinct().size > 1) failures += "$counter uses 2..$USES differ by size: $later"

            val firstSmall = perSize.getValue(smallest)[0].getValue(counter)
            for (size in SIZES.drop(1)) {
                val first = perSize.getValue(size)[0].getValue(counter)
                val bound = (firstSmall + 1) * (size / smallest) * 2
                if (first > bound) failures += "$counter first use at $size = $first > linear bound $bound"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        val SIZES = listOf(10, 100, 1000)
        const val USES = 5
    }
}
