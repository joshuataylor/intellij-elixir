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

    /** Uses after the module's other definitions, as in a large decompiled module. */
    fun testModuleBody() = assertSizeIndependent(SIZES.associateWith(::moduleBody), Counter.GATE)

    /** Uses in `test` blocks, after many others. */
    fun testExUnit() = assertSizeIndependent(SIZES.associateWith(::exUnit), Counter.GATE)

    /** Uses after many `scope ... do` blocks, as in a router. */
    fun testRouter() = assertSizeIndependent(SIZES.associateWith(::router), Counter.GATE)

    private fun moduleBody(size: Int): List<Map<Counter, Long>> {
        val file = myFixture.addFileToProject(
            "module_body_$size.ex",
            buildString {
                appendLine("defmodule ModuleBody$size do")
                for (i in 0 until size) appendLine("  def unrelated_$i(a), do: a")
                appendLine("  def target(a), do: a")
                for (i in 0 until USES) appendLine("  def use_$i, do: target($i)")
                appendLine("end")
            }
        )

        return measure(file, USE)
    }

    private fun exUnit(size: Int): List<Map<Counter, Long>> {
        if (size == SIZES.first()) myFixture.addFileToProject("ex_unit_case.ex", EX_UNIT_CASE)
        val file = myFixture.addFileToProject(
            "ex_unit_$size.ex",
            buildString {
                appendLine("defmodule ExUnit$size do")
                appendLine("  use ExUnit.Case")
                appendLine("  def target(a), do: a")
                for (i in 0 until size) appendLine("  test \"unrelated $i\" do\n    :ok\n  end")
                for (i in 0 until USES) appendLine("  test \"use $i\" do\n    target($i)\n  end")
                appendLine("end")
            }
        )

        return measure(file, USE)
    }

    private fun router(size: Int): List<Map<Counter, Long>> {
        val file = myFixture.addFileToProject(
            "router_$size.ex",
            buildString {
                appendLine("defmodule Router$size do")
                appendLine("  def target(a), do: a")
                for (i in 0 until size) appendLine("  scope \"/unrelated_$i\" do\n    get \"/\", Controller, :index\n  end")
                for (i in 0 until USES) appendLine("  def use_$i, do: target($i)")
                appendLine("end")
            }
        )

        return measure(file, USE)
    }

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
            measure(user, Regex("""kernel_target\([0-9]"""))
        } finally {
            // Only one `Kernel` at a time, or the implicit import would walk every size's.
            WriteAction.runAndWait<Throwable> {
                kernel.virtualFile.delete(this)
                user.virtualFile.delete(this)
            }
        }
    }

    /** Per use, each counter's work for one resolve. */
    private fun measure(file: PsiFile, use: Regex): List<Map<Counter, Long>> {
        val offsets = use.findAll(file.text).map { it.range.first }.toList()
        check(offsets.size == USES) { "expected $USES uses matching $use, found ${offsets.size}" }

        return offsets.map { offset ->
            val reference = file.viewProvider.findReferenceAt(offset, org.elixir_lang.ElixirLanguage) as PsiPolyVariantReference
            WalkProbe.reset()
            val results = WalkProbe.counting { reference.multiResolve(false) }
            check(results.any { it.isValidResult }) { "the use at $offset of ${file.name} did not resolve" }

            WalkProbe.snapshot()
        }
    }

    /** For [counters], or every counter when none is named. */
    private fun assertSizeIndependent(perSize: Map<Int, List<Map<Counter, Long>>>, vararg counters: Counter) {
        val failures = mutableListOf<String>()
        val smallest = SIZES.first()

        for (counter in counters.ifEmpty { Counter.entries.toTypedArray() }) {
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
        val USE = Regex("""\btarget\([0-9]""")

        val EX_UNIT_CASE = """
            defmodule ExUnit.Case do
              defmacro __using__(_opts) do
                quote do
                  import ExUnit.Case
                end
              end

              defmacro test(message, do: block) do
                quote do
                  unquote(message)
                  unquote(block)
                end
              end
            end
        """.trimIndent()
    }
}
