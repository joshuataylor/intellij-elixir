package org.elixir_lang.psi.scope

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.annotations.TestOnly
import java.util.EnumMap

/**
 * Counts of the work one resolve does, for the tests that pin it as independent of a module's size, and the points at
 * which those tests cancel it. Both are off outside such a test.
 */
object WalkProbe {
    enum class Counter {
        /** Clauses handed to `executeOnCallDefinitionClause` outside the implicit imports. */
        CLAUSE_HANDLER_MODULE,

        /** Clauses handed to `executeOnCallDefinitionClause` by the implicit `import Kernel`/`Kernel.SpecialForms`. */
        CLAUSE_HANDLER_KERNEL,

        /** Implicit-import name index builds. */
        KERNEL_INDEX_BUILD,
    }

    enum class CancelPoint {
        /** Each clause of an implicit-import name index build. */
        KERNEL_INDEX,
    }

    @Volatile
    private var counting = false

    private val counts = EnumMap<Counter, Long>(Counter::class.java)

    fun count(counter: Counter) {
        if (counting) {
            synchronized(counts) { counts.merge(counter, 1L, Long::plus) }
        }
    }

    @TestOnly
    fun <T> counting(block: () -> T): T {
        counting = true

        return try {
            block()
        } finally {
            counting = false
        }
    }

    @TestOnly
    fun snapshot(): Map<Counter, Long> = synchronized(counts) { Counter.entries.associateWith { counts[it] ?: 0L } }

    @TestOnly
    fun reset() = synchronized(counts) { counts.clear() }

    @Volatile
    private var armed: CancelPoint? = null
    private var remaining = 0

    @Volatile
    private var fired = false

    fun cancelPoint(point: CancelPoint) {
        if (armed == point && --remaining == 0) {
            armed = null
            fired = true
            throw ProcessCanceledException()
        }
    }

    /** Throw a [ProcessCanceledException] at the [k]th time [point] is reached, once. */
    @TestOnly
    fun armCancel(point: CancelPoint, k: Int) {
        fired = false
        remaining = k
        armed = point
    }

    /** Disarms, answering whether the armed point threw. */
    @TestOnly
    fun disarmCancel(): Boolean {
        armed = null

        return fired.also { fired = false }
    }
}
