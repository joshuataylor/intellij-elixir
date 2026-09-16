package org.elixir_lang.sdk

import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.elixir_lang.PlatformTestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SdkVersionFileWatcherTest : PlatformTestCase() {
    private val homeByWatchedPath = mapOf(
        "/opt/erlang/27/releases/27/OTP_VERSION" to "/opt/erlang/27",
        "/opt/elixir/1.20.5/lib/elixir/ebin/elixir.app" to "/opt/elixir/1.20.5",
        "/opt/elixir/1.20.5/lib/elixir/ebin/Elixir.System.beam" to "/opt/elixir/1.20.5",
    )

    fun testAChangedVersionFileNamesItsHome() {
        val events = listOf(contentChange("/opt/erlang/27/releases/27/OTP_VERSION"))

        assertEquals(
            setOf("/opt/erlang/27"),
            SdkVersionFileWatcher.homesToRevalidate(events, homeByWatchedPath),
        )
    }

    fun testAnUnwatchedPathNamesNothing() {
        val events = listOf(contentChange("/opt/erlang/27/releases/27/OTP_VERSION.bak"))

        assertEmpty(SdkVersionFileWatcher.homesToRevalidate(events, homeByWatchedPath))
    }

    fun testADeletedVersionFileNamesItsHome() {
        val events = listOf(delete("/opt/elixir/1.20.5/lib/elixir/ebin/elixir.app"))

        assertEquals(
            setOf("/opt/elixir/1.20.5"),
            SdkVersionFileWatcher.homesToRevalidate(events, homeByWatchedPath),
        )
    }

    fun testARecreatedVersionFileNamesItsHome() {
        // A package that replaces an install writes the file again rather than editing it in place.
        val events = listOf(create("/opt/erlang/27/releases/27/OTP_VERSION"))

        assertEquals(
            setOf("/opt/erlang/27"),
            SdkVersionFileWatcher.homesToRevalidate(events, homeByWatchedPath),
        )
    }

    fun testAReleaseDirectoryAddedByAnUpgradeNamesItsHome() {
        // An in-place OTP upgrade adds releases/28 beside releases/27 rather than rewriting the watched file.
        val events = listOf(create("/opt/erlang/27/releases/28"))

        assertEquals(
            setOf("/opt/erlang/27"),
            SdkVersionFileWatcher.homesToRevalidate(
                events,
                homeByWatchedPath + ("/opt/erlang/27/releases" to "/opt/erlang/27"),
            ),
        )
    }

    fun testAFileOtpWritesBesideTheReleaseDirectoriesNamesNothing() {
        // `release_handler` rewrites RELEASES and start_erl.data in the directory holding the releases. Treating
        // those as a new release would re-read the installation, and that read clears what it cannot re-read.
        val events = listOf(
            create("/opt/erlang/27/releases/RELEASES"),
            contentChange("/opt/erlang/27/releases/start_erl.data"),
        )

        assertEmpty(
            SdkVersionFileWatcher.homesToRevalidate(
                events,
                homeByWatchedPath + ("/opt/erlang/27/releases" to "/opt/erlang/27"),
            ),
        )
    }

    fun testPathsAreComparedSystemIndependently() {
        val events = listOf(contentChange("C:\\opt\\erlang\\27\\releases\\27\\OTP_VERSION"))

        assertEquals(
            setOf("C:/opt/erlang/27"),
            SdkVersionFileWatcher.homesToRevalidate(
                events,
                mapOf("C:/opt/erlang/27/releases/27/OTP_VERSION" to "C:/opt/erlang/27"),
            ),
        )
    }

    fun testSeveralChangesUnderOneHomeNameItOnce() {
        val events = listOf(
            contentChange("/opt/elixir/1.20.5/lib/elixir/ebin/elixir.app"),
            contentChange("/opt/elixir/1.20.5/lib/elixir/ebin/Elixir.System.beam"),
        )

        assertEquals(
            setOf("/opt/elixir/1.20.5"),
            SdkVersionFileWatcher.homesToRevalidate(events, homeByWatchedPath),
        )
    }

    fun testAnEventWithNoPathNamesNothing() {
        assertEmpty(SdkVersionFileWatcher.homesToRevalidate(listOf(mock(VFileEvent::class.java)), homeByWatchedPath))
    }

    fun testTheFilesWatchedForAnElixirHome() {
        assertEquals(
            listOf(
                "/opt/elixir/1.20.5/lib/elixir/ebin/elixir.app",
                "/opt/elixir/1.20.5/lib/elixir/ebin/Elixir.System.beam",
            ),
            SdkVersionFileWatcher.elixirVersionFiles("/opt/elixir/1.20.5"),
        )
    }

    private fun contentChange(path: String): VFileContentChangeEvent =
        mock(VFileContentChangeEvent::class.java).also { `when`(it.path).thenReturn(path) }

    private fun delete(path: String): VFileDeleteEvent =
        mock(VFileDeleteEvent::class.java).also { `when`(it.path).thenReturn(path) }

    private fun create(path: String): VFileCreateEvent =
        mock(VFileCreateEvent::class.java).also { `when`(it.path).thenReturn(path) }
}
