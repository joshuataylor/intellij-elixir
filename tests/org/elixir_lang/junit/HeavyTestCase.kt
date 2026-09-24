package org.elixir_lang.junit

import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.elixir_lang.junit.logs.UnexpectedLogs

/** A [HeavyPlatformTestCase] failed for any warning, error or uncaught exception it does not expect. */
abstract class HeavyTestCase : HeavyPlatformTestCase() {
    @Throws(Throwable::class)
    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        UnexpectedLogs.failOnUnexpectedLogs { super.runBare(testRunnable) }
    }
}
