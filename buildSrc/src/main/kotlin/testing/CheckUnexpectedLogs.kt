package testing

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * Fails on the warnings and errors that the test JVMs could not fail a test for, or only lists them when [mode] is
 * `report`. A finalizer of `test` rather than a `doLast` on it, which Gradle skips once a test has failed.
 */
abstract class CheckUnexpectedLogs : DefaultTask() {
    @get:Input
    abstract val mode: Property<String>

    /** Where `org.elixir_lang.junit.logs.UnexpectedLogs` in each test JVM writes what it could not fail a test for. */
    @get:Internal
    abstract val reportDir: Property<File>

    @TaskAction
    fun check() {
        val unexpectedLogs = reportDir.get()
        // Named by `UnexpectedLogs.PLATFORM_WARNINGS_PREFIX`: platform warnings are listed, never failed on.
        val (platformWarnings, reports) = unexpectedLogs.listFiles().orEmpty()
            .filter(File::isFile)
            .partition { it.name.startsWith("platform-warnings-") }
        if (platformWarnings.isNotEmpty()) {
            logger.warn(
                "Platform warnings logged during the tests (${unexpectedLogs.absolutePath}):\n\n" +
                    platformWarnings.joinToString("\n") { it.readText() }
            )
        }
        if (reports.isNotEmpty()) {
            val summary = "Unexpected logs no test was failed for (${unexpectedLogs.absolutePath}):\n\n" +
                reports.joinToString("\n") { it.readText() }
            if (mode.get() == "report") logger.warn(summary) else throw GradleException(summary)
        }
    }
}

/**
 * `org.elixir_lang.junit.logs.UnexpectedLogs` fails a test that warns or errs without expecting it; what it could not
 * pin on a failing test each test JVM writes to [reportDir], for [check] to fail on.
 */
fun Test.reportUnexpectedLogs(reportDir: File, mode: String, check: TaskProvider<CheckUnexpectedLogs>) {
    systemProperty("elixir.test.logs", mode)
    systemProperty("elixir.test.logs.report", reportDir.absolutePath)
    // The guard's own tests run their sample classes in a nested launcher.
    exclude("org/elixir_lang/junit/logs/samples/**")
    // An output, so an up-to-date or cached `test` leaves the report of the run it stands for.
    outputs.dir(reportDir).withPropertyName("unexpectedLogs")
    doFirst { reportDir.deleteRecursively() }
    finalizedBy(check)
}
