package org.elixir_lang.junit.logs

import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.PostDiscoveryFilter
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

internal class Finished(val result: TestExecutionResult, val millis: Long)

/**
 * Runs [selector] in a nested launcher, which loads the same listener and extension as the build, and returns each
 * test's result by display name.
 */
internal fun runNested(
    selector: DiscoverySelector,
    mode: String = "fail",
    filter: PostDiscoveryFilter? = null,
): Map<String, Finished> {
    val finished = mutableMapOf<String, Finished>()
    val started = mutableMapOf<String, Long>()
    val recorder = object : TestExecutionListener {
        override fun executionStarted(testIdentifier: TestIdentifier) {
            if (testIdentifier.isTest) started[testIdentifier.displayName] = System.nanoTime()
        }

        override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
            if (!testIdentifier.isTest) return
            val millis = (System.nanoTime() - started.getValue(testIdentifier.displayName)) / 1_000_000

            finished[testIdentifier.displayName] = Finished(testExecutionResult, millis)
        }
    }
    val previousMode = System.setProperty(UnexpectedLogs.MODE_PROPERTY, mode)

    try {
        LauncherFactory.create().execute(
            LauncherDiscoveryRequestBuilder.request()
                .selectors(selector)
                .apply { if (filter != null) filters(filter) }
                // The samples fail on purpose; the platform's extension would save and announce each one's log.
                .configurationParameter(
                    "junit.jupiter.extensions.autodetection.exclude",
                    "com.intellij.testFramework.junit5.impl.TestLoggerExtension",
                )
                .build(),
            recorder,
        )
    } finally {
        if (previousMode == null) System.clearProperty(UnexpectedLogs.MODE_PROPERTY)
        else System.setProperty(UnexpectedLogs.MODE_PROPERTY, previousMode)
    }

    return finished
}
