package org.elixir_lang.tool_manager.mise

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.elixir_lang.junit.HeavyTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The VFS turns what the file watcher reports into events only for files it has loaded, so these run a real refresh
 * over real directories rather than handing [MiseRefreshTrigger.shouldTrigger] an event.
 */
class MiseWatchTest : HeavyTestCase() {
    private val lfs get() = LocalFileSystem.getInstance()
    private val watchRequests: MutableSet<LocalFileSystem.WatchRequest> = ConcurrentHashMap.newKeySet()
    private lateinit var root: Path
    private lateinit var installs: Path

    override fun setUp() {
        super.setUp()
        root = createTempDirectory().toPath()
        installs = Files.createDirectories(root.resolve("installs"))
    }

    override fun tearDown() {
        try {
            lfs.removeWatchedRoots(watchRequests)
        } finally {
            super.tearDown()
        }
    }

    fun testAVersionDirectoryCreatedInAWatchedToolDirStartsAnInstall() {
        val elixir = Files.createDirectories(installs.resolve("elixir"))
        Files.createDirectories(elixir.resolve("1.20.5-otp-27"))
        MiseRefreshTrigger.watchAndLoad(lfs, path(elixir), watchRequests)

        val events = events { Files.createDirectories(elixir.resolve("1.20.5-otp-28/bin")) }

        assertTrue(events.toString(), events.any { MiseRefreshTrigger.startsAnInstall(it, setOf(path(elixir))) })
    }

    fun testDeletingAnInstallPathIsReported() {
        val elixir = Files.createDirectories(installs.resolve("elixir"))
        val version = Files.createDirectories(elixir.resolve("1.20.5-otp-28"))
        Files.writeString(version.resolve("VERSION"), "1.20.5")
        MiseRefreshTrigger.watchAndLoad(lfs, path(elixir), watchRequests)

        val events = events { version.toFile().deleteRecursively() }

        assertTrue(events.toString(), events.any { MiseRefreshTrigger.uninstalls(it, setOf(path(version))) })
    }

    fun testDeletingAToolDirIsReported() {
        val elixir = Files.createDirectories(installs.resolve("elixir"))
        val version = Files.createDirectories(elixir.resolve("1.20.5-otp-28"))
        MiseRefreshTrigger.watchAndLoad(lfs, path(elixir), watchRequests)

        val events = events { elixir.toFile().deleteRecursively() }

        assertTrue(events.toString(), events.any { MiseRefreshTrigger.uninstalls(it, setOf(path(version))) })
    }

    fun testAVersionDirectoryCreatedInAToolDirThatAppearedLaterIsReported() {
        MiseRefreshTrigger.watchAndLoad(lfs, path(installs), watchRequests)
        val elixir = installs.resolve("elixir")
        MiseRefreshTrigger.watchAndLoad(lfs, path(elixir), watchRequests)
        val created = events { Files.createDirectories(elixir) }
        assertTrue("precondition: $created", created.any { it is VFileCreateEvent && it.path == path(elixir) })
        MiseRefreshTrigger.watchAndLoad(lfs, path(elixir), watchRequests)

        val events = events { Files.createDirectories(elixir.resolve("1.20.5-otp-28")) }

        assertTrue(events.toString(), events.any { MiseRefreshTrigger.startsAnInstall(it, setOf(path(elixir))) })
    }

    fun testAFileCreatedInTrustedConfigsIsReported() {
        val trustedConfigs = Files.createDirectories(root.resolve("state/trusted-configs"))
        Files.writeString(trustedConfigs.resolve("project-a"), "")
        MiseRefreshTrigger.watchPaths(lfs, listOf(path(trustedConfigs)), watchRequests)

        val events = events { Files.writeString(trustedConfigs.resolve("project-b"), "") }

        assertTrue(events.toString(), events.any { it is VFileCreateEvent && it.path == path(trustedConfigs.resolve("project-b")) })
    }

    fun testAConfigFileOutsideTheProjectReportsItsChange() {
        val config = Files.writeString(Files.createDirectories(root.resolve("config/mise")).resolve("config.toml"), "")
        MiseRefreshTrigger.watchPaths(lfs, listOf(path(config)), watchRequests)

        val events = events {
            Files.writeString(config, "[tools]\nelixir = \"1.20.5-otp-28\"")
            Files.setLastModifiedTime(config, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000))
        }

        assertTrue(events.toString(), events.any { it is VFileContentChangeEvent && it.path == path(config) })
    }

    fun testATrustedConfigsDirectoryCreatedByTheFirstTrustIsFollowed() {
        val state = Files.createDirectories(root.resolve("state"))
        MiseRefreshTrigger.watchPaths(lfs, listOf(path(state), path(state.resolve("trusted-configs"))), watchRequests)
        val trustedConfigs = state.resolve("trusted-configs")
        val created = events { Files.writeString(Files.createDirectories(trustedConfigs).resolve("project-a"), "") }
        assertTrue("precondition: $created", created.any { it is VFileCreateEvent && it.path == path(trustedConfigs) })

        assertTrue(
            "the trust that created the directory wrote its file before the directory was loaded",
            MiseRefreshTrigger.directoryCreated(lfs, path(trustedConfigs), watchRequests),
        )
        val events = events { Files.writeString(trustedConfigs.resolve("project-b"), "") }

        assertTrue(events.toString(), events.any { it is VFileCreateEvent && it.path == path(trustedConfigs.resolve("project-b")) })
    }

    private fun path(path: Path): String = FileUtil.toSystemIndependentName(path.toString())

    /**
     * What the file watcher does with a changed path: it marks the path dirty if the VFS has it, and otherwise the
     * nearest ancestor it has, then refreshes.
     */
    private fun events(change: () -> Unit): List<VFileEvent> {
        val events = mutableListOf<VFileEvent>()
        project.messageBus.connect(testRootDisposable).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(batch: List<VFileEvent>) {
                    events += batch.filter { it.path.startsWith(path(root)) }
                }
            },
        )
        val before = Files.walk(root).use { paths -> paths.toList().toSet() }
        change()
        val after = Files.walk(root).use { paths -> paths.toList().toSet() }
        // A path still there is reported itself; a deleted one through the nearest ancestor still there.
        val changed = after.filter { it !in before || Files.isRegularFile(it) } +
            (before - after).mapNotNull { deleted -> generateSequence(deleted) { it.parent }.firstOrNull { it in after } }
        for (path in changed) {
            generateSequence(path) { it.parent }
                .firstNotNullOfOrNull { lfs.findFileByPathIfCached(path(it)) }
                ?.let { VfsUtil.markDirtyAndRefresh(false, false, false, it) }
        }
        return events
    }
}
