package org.elixir_lang.junit.logs.samples

import com.intellij.openapi.diagnostic.Logger
import org.elixir_lang.junit.logs.expectWarnings
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

/** Run only by `UnexpectedLogsTest`, in a nested launcher; the `test` task excludes this package. */
@Suppress("LoggingSimilarMessage") // The same warning, expected and not.
class JupiterSample {
    @Test
    fun warns() {
        logger().warn(SAMPLE_WARNING)
    }

    @Test
    fun expectsItsWarning() {
        expectWarnings(JupiterSample::class.java, Regex("^$SAMPLE_WARNING$")) { logger().warn(SAMPLE_WARNING) }
    }

    @Test
    fun expectsAWarningThatNeverComes() {
        expectWarnings(JupiterSample::class.java, Regex("^$SAMPLE_WARNING$")) {}
    }

    @Test
    fun ignoredWarning() {
        Logger.getInstance("#com.intellij.util.ui.StyleSheetUtil").warn("Missing global CSS sheet")
    }

    @Test
    fun warnsOnAnotherThread() {
        thread { logger().warn(BACKGROUND_WARNING) }.join()
    }

    @Test
    fun failsAndWarns() {
        logger().warn(SAMPLE_WARNING)
        fail<Unit>(SAMPLE_FAILURE)
    }

    private fun logger() = Logger.getInstance(JupiterSample::class.java)
}
