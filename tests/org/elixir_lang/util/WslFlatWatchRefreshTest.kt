package org.elixir_lang.util

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.elixir_lang.junit.HeavyTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class WslFlatWatchRefreshTest : HeavyTestCase() {
    private val lfs get() = LocalFileSystem.getInstance()

    fun testOnlyAWslPathIsRefreshedAndOnlyBeforeThePlatformFix() {
        assertTrue(WslFlatWatchRefresh.isAffected("//wsl.localhost/IntellijElixirWSLDistribution/home/u/.local/share/mise/installs/elixir", 261, true))
        assertTrue(WslFlatWatchRefresh.isAffected("\\\\wsl$\\IntellijElixirWSLDistribution\\home\\u\\.tool-versions", 261, true))
        assertTrue(WslFlatWatchRefresh.isAffected("//WSL.LOCALHOST/IntellijElixirWSLDistribution/home/u", 261, true))

        assertFalse("a local path", WslFlatWatchRefresh.isAffected("C:/Users/u/.local/share/mise", 261, true))
        assertFalse("another UNC share", WslFlatWatchRefresh.isAffected("//server/share/mise", 261, true))
        assertFalse("the fixed platform", WslFlatWatchRefresh.isAffected("//wsl.localhost/IntellijElixirWSLDistribution/home/u", 262, true))
        assertFalse("the EEL watcher turned off", WslFlatWatchRefresh.isAffected("//wsl.localhost/IntellijElixirWSLDistribution/home/u", 261, false))
    }

    fun testARefreshReportsAChildCreatedAndOneDeleted() {
        val root = createTempDirectory().toPath()
        val dir = Files.createDirectories(root.resolve("installs/elixir"))
        val old = Files.createDirectories(dir.resolve("1.20.4-otp-28"))
        lfs.loadForEvents(path(dir))
        Files.createDirectories(dir.resolve("1.20.5-otp-28"))
        Files.delete(old)

        val events = events(root) { WslFlatWatchRefresh.refresh(listOf(path(dir))) }

        assertTrue(events.toString(), events.any { it is VFileCreateEvent && it.path == path(dir.resolve("1.20.5-otp-28")) })
        assertTrue(events.toString(), events.any { it is VFileDeleteEvent && it.path == path(old) })
    }

    fun testARefreshReportsAWatchedFileChanged() {
        val root = createTempDirectory().toPath()
        val file = Files.writeString(root.resolve(".tool-versions"), "elixir 1.20.4-otp-28\n")
        lfs.loadForEvents(path(file))
        Files.writeString(file, "elixir 1.20.5-otp-28\n")
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000))

        val events = events(root) { WslFlatWatchRefresh.refresh(listOf(path(file))) }

        assertTrue(events.toString(), events.any { it is VFileContentChangeEvent && it.path == path(file) })
    }

    fun testAPathIsRefreshedUntilEveryOwnerIsDisposed() {
        val scope = CoroutineScope(Job())
        try {
            val refresh = WslFlatWatchRefresh(scope)
            val first = Disposer.newDisposable(testRootDisposable, "first")
            val second = Disposer.newDisposable(testRootDisposable, "second")
            refresh.track(listOf("//wsl.localhost/IntellijElixirWSLDistribution/a", "//wsl.localhost/IntellijElixirWSLDistribution/b"), first)
            refresh.track(listOf("//wsl.localhost/IntellijElixirWSLDistribution/a"), second)

            Disposer.dispose(first)
            assertEquals(setOf("//wsl.localhost/IntellijElixirWSLDistribution/a"), refresh.followed())

            Disposer.dispose(second)
            assertEquals(emptySet<String>(), refresh.followed())
        } finally {
            scope.cancel()
        }
    }

    fun testAnOwnerAlreadyDisposedLeavesNothingRefreshed() {
        val scope = CoroutineScope(Job())
        try {
            val refresh = WslFlatWatchRefresh(scope)
            val owner = Disposer.newDisposable(testRootDisposable, "owner")
            Disposer.dispose(owner)

            refresh.track(listOf("//wsl.localhost/IntellijElixirWSLDistribution/a"), owner)

            assertEquals(emptySet<String>(), refresh.followed())
        } finally {
            scope.cancel()
        }
    }

    private fun path(path: Path): String = FileUtil.toSystemIndependentName(path.toString())

    private fun events(root: Path, action: () -> Unit): List<VFileEvent> {
        val events = mutableListOf<VFileEvent>()
        project.messageBus.connect(testRootDisposable).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(batch: List<VFileEvent>) {
                    events += batch.filter { it.path.startsWith(path(root)) }
                }
            },
        )
        action()
        return events
    }
}
