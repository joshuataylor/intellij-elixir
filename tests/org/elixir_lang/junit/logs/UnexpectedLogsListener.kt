package org.elixir_lang.junit.logs

import com.intellij.openapi.application.AccessToken
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.initializeTestEnvironment
import org.elixir_lang.junit.logs.UnexpectedLogs.Level
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

/** Installs [UnexpectedLogs] for the run and tells it which test is running. */
class UnexpectedLogsListener : TestExecutionListener {
    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        synchronized(UnexpectedLogsListener::class.java) {
            if (plans++ > 0) return
            // Before it, loggers are the platform's `DefaultLogger`, which never consults a `LoggedErrorProcessor`.
            @Suppress("UnstableApiUsage")
            initializeTestEnvironment()
            installed = LoggedErrorProcessor.executeWith(GuardedLoggedErrorProcessor())
        }
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (!testIdentifier.isTest) return
        // Checked per test: the application installs its own default handler when it starts.
        recordUncaughtExceptions()
        UnexpectedLogs.testStarted(testIdentifier.uniqueId, nameOf(testIdentifier))
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (testIdentifier.isTest) UnexpectedLogs.testFinished(testIdentifier.uniqueId)
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan) {
        synchronized(UnexpectedLogsListener::class.java) {
            if (--plans > 0) return
            UnexpectedLogs.writeReport()
            installed?.finish()
            installed = null
        }
    }

    private fun nameOf(test: TestIdentifier): String =
        (test.source.orElse(null) as? MethodSource)
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
            ?: test.displayName

    private fun recordUncaughtExceptions() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current !is Recording) Thread.setDefaultUncaughtExceptionHandler(Recording(current))
    }

    private class Recording(private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, e: Throwable) {
            UnexpectedLogs.record(Level.UNCAUGHT, e.javaClass.name, e.message.orEmpty(), e)

            if (next != null) {
                next.uncaughtException(thread, e)
            } else {
                System.err.print("Exception in thread \"${thread.name}\" ")
                e.printStackTrace()
            }
        }
    }

    private companion object {
        // A test can run a nested launcher; only the outermost plan installs the guard and writes the report.
        var plans = 0
        var installed: AccessToken? = null
    }
}
