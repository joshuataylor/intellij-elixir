package org.elixir_lang.junit

import junit.framework.TestCase
import org.elixir_lang.junit.logs.UnexpectedLogs

/** A JUnit 3 [TestCase] failed for any warning, error or uncaught exception it does not expect. */
abstract class UnitTestCase : TestCase() {
    @Throws(Throwable::class)
    override fun runBare() {
        UnexpectedLogs.failOnUnexpectedLogs { super.runBare() }
    }
}
