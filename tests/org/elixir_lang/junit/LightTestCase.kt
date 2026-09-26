package org.elixir_lang.junit

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.elixir_lang.junit.logs.UnexpectedLogs

/** A [BasePlatformTestCase] failed for any warning, error or uncaught exception it does not expect. */
abstract class LightTestCase : BasePlatformTestCase() {
    @Throws(Throwable::class)
    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        UnexpectedLogs.failOnUnexpectedLogs { super.runBare(testRunnable) }
    }
}
