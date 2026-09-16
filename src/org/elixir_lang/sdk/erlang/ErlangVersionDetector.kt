package org.elixir_lang.sdk.erlang

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.elixir_lang.sdk.wsl.wslCompat
import java.io.File

/**
 * Detects the installed Erlang/OTP SDK version by reading the filesystem - no subprocess required.
 *
 * The canonical source of truth for an installed OTP release is:
 *   `<sdkHome>/releases/<N>/OTP_VERSION`
 * where `<N>` is the OTP major release directory (e.g. `26`).
 *
 * [detectRelease] must NOT be called on the EDT. WSL paths (\\wsl.localhost\...) involve
 * the Plan 9 filesystem redirector and directory/file access can take 50–200 ms.
 * Call only from background threads or [kotlinx.coroutines.Dispatchers.IO] contexts.
 * [org.elixir_lang.sdk.SdkVersionsStore] holds what this read, keyed by the home, so code that only needs the
 * version reads it from there rather than coming back here.
 */
object ErlangVersionDetector {
    private val LOGGER = Logger.getInstance(ErlangVersionDetector::class.java)

    /**
     * Reads the installed Erlang/OTP version from `<sdkHome>/releases/<N>/OTP_VERSION`.
     *
     * Returns a [Release] with the OTP major and full patch version, or `null` if the
     * `releases/` directory or `OTP_VERSION` file is absent or unreadable.
     *
     * An `OTP_VERSION` that is not an OTP version keeps its text and takes its major from the `releases/<N>`
     * directory it sits in, so a packaged install is still usable.
     *
     * Must NOT be called on the EDT - see class KDoc.
     */
    @RequiresBackgroundThread
    fun detectRelease(sdkHome: String): Release? {
        ThreadingAssertions.assertBackgroundThread()

        return detectReleaseAt(wslCompat.canonicalizePath(sdkHome))
    }

    /**
     * [canonicalHome] has already been resolved by the caller, so a caller that needed the resolved path itself does
     * not pay for resolving it twice - on a WSL home that is uncached I/O that boots the distro.
     */
    @RequiresBackgroundThread
    fun detectReleaseAt(canonicalHome: String): Release? {
        ThreadingAssertions.assertBackgroundThread()
        val releasesDir = File(canonicalHome, "releases")

        val otpMajorDir = if (!releasesDir.exists()) {
            LOGGER.warn("Can't detect Erlang version: ${releasesDir.path} is missing")
            return null
        } else {
            releasesDir.listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }
                ?.maxByOrNull { it.name.toIntOrNull() ?: 0 }
                ?: run {
                    LOGGER.debug("No numeric releases/ subdirectory found in $canonicalHome")
                    return null
                }
        }

        val otpVersionFile = File(otpMajorDir, "OTP_VERSION")
        val text = try {
            otpVersionFile.readText()
        } catch (e: Exception) {
            LOGGER.debug("Could not read OTP_VERSION from ${otpVersionFile.path}", e)
            return null
        }

        if (text.isBlank()) {
            LOGGER.warn("OTP_VERSION file is empty: ${otpVersionFile.path}")
            return null
        }

        val otpVersion = text.trim().trimEnd('*').trimEnd()
        val otpMajor = otpMajorDir.name
        // A packager's leading numbers are kept when they agree with the directory; when they contradict it, as
        // `8.2 (OTP 27)` under `releases/27` does, the directory wins.
        val release = Release.parse(otpVersion)
            ?: (Release.of(otpVersion)?.takeIf { it.otpMajor == otpMajor } ?: Release.ofOtpMajor(otpMajor, otpVersion))
                ?.also { LOGGER.warn("OTP_VERSION file does not hold an OTP version: ${otpVersionFile.path}") }
            ?: return null
        LOGGER.debug("Detected Erlang release: $release (from ${otpVersionFile.path})")
        return release
    }
}
