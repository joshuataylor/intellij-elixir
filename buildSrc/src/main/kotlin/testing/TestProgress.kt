package testing

import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Logs `N tests complete in 1m34s` every [every] tests, counted across all forks, so a run that no longer lists passing
 * tests still shows it is moving.
 */
class TestProgress(private val every: Int) : TestListener {
    private val completed = AtomicInteger()
    private val startedNanos = AtomicLong()

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.parent == null) {
            completed.set(0)
            startedNanos.set(System.nanoTime())
        }
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        val count = completed.incrementAndGet()
        if (count % every == 0) {
            val seconds = (System.nanoTime() - startedNanos.get()) / 1_000_000_000
            val elapsed = if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
            Logging.getLogger(TestProgress::class.java).lifecycle("$count tests complete in $elapsed")
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {}

    override fun beforeTest(testDescriptor: TestDescriptor) {}
}
