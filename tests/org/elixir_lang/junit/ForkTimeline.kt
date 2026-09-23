package org.elixir_lang.junit

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Appends `slot, class, start, end` for every test class to the file `elixir.test.timeline` names, so a parallel run
 * shows which fork ran what and which class set its finish time. Gradle's XML does not record the fork.
 */
class ForkTimeline : TestExecutionListener {
    private val file = System.getProperty("elixir.test.timeline")?.takeIf(String::isNotBlank)?.let(::File)
    private val started = ConcurrentHashMap<String, Long>()

    // Vintage gives a generated `suite()` case a class source too, so only a container is a test class.
    private fun isTestClass(testIdentifier: TestIdentifier): Boolean =
        testIdentifier.isContainer && testIdentifier.source.orElse(null) is ClassSource

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (file != null && isTestClass(testIdentifier)) {
            started[testIdentifier.uniqueId] = System.currentTimeMillis()
        }
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        val file = file ?: return
        if (!isTestClass(testIdentifier)) return
        val source = testIdentifier.source.get() as ClassSource
        val start = started.remove(testIdentifier.uniqueId) ?: return
        synchronized(ForkTimeline::class.java) {
            file.appendText("${ForkIsolation.slot ?: 0},${source.className},$start,${System.currentTimeMillis()}\n")
        }
    }
}
