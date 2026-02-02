package quoter

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * BuildService that manages the Quoter (Burrito binary) lifecycle.
 *
 * The Burrito binary is a self-contained Elixir application packaged as a single
 * executable. It starts an Erlang distributed node that tests communicate with
 * via JInterface (OtpNode).
 *
 * The process is started on first use and automatically stopped when the build ends,
 * regardless of whether the build succeeded or failed.
 *
 * See: https://github.com/burrito-elixir/burrito
 * See: https://docs.gradle.org/current/userguide/build_services.html
 */
abstract class QuoterService : BuildService<QuoterService.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        /** Path to the Burrito binary */
        val executable: RegularFileProperty

        /** Temporary directory for quoter runtime files */
        val tmpDir: DirectoryProperty
    }

    private val logger = Logging.getLogger(QuoterService::class.java)

    @Volatile
    private var started = false

    @Volatile
    private var process: Process? = null

    /**
     * Ensures the Quoter process is started and ready.
     * Safe to call multiple times -- only starts once.
     */
    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            startProcess()
            started = true
        }
    }

    private fun startProcess() {
        val executable = parameters.executable.get().asFile
        val installDir = parameters.tmpDir.get().asFile
        logger.lifecycle("Starting Quoter: ${executable.absolutePath}")

        val pb = ProcessBuilder(executable.absolutePath)
        pb.redirectErrorStream(false)
        pb.environment()["INTELLIJ_ELIXIR_INSTALL_DIR"] = installDir.absolutePath

        val proc = pb.start()

        // Consume streams in background threads to prevent buffer blocking.
        // If we don't drain these, the process will hang when OS pipe buffers fill.
        thread(name = "quoter-stdout", isDaemon = true) {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) logger.debug("[QUOTER] $line")
                }
            }
        }
        thread(name = "quoter-stderr", isDaemon = true) {
            proc.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) logger.debug("[QUOTER-ERR] $line")
                }
            }
        }

        // Wait briefly, then verify the process is still alive.
        // The Burrito binary self-extracts on first run and starts the BEAM VM.
        Thread.sleep(3000)

        if (!proc.isAlive) {
            val exitCode = proc.exitValue()
            throw RuntimeException(
                "Quoter process died immediately (exit code $exitCode). " +
                "Binary: ${executable.absolutePath}"
            )
        }

        process = proc
        logger.lifecycle("Quoter process started (PID: ${proc.pid()})")
    }

    override fun close() {
        val proc = process ?: return
        if (!started) return

        logger.lifecycle("Shutting down Quoter process...")

        try {
            proc.destroy()
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                logger.lifecycle("Quoter did not stop gracefully, forcing...")
                proc.destroyForcibly()
                proc.waitFor(2, TimeUnit.SECONDS)
            }
            logger.lifecycle("Quoter process stopped")
        } catch (e: Exception) {
            logger.error("Error stopping Quoter: ${e.message}")
        }
    }
}
