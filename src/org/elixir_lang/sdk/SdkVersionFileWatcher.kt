package org.elixir_lang.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.ThreadingAssertions
import org.elixir_lang.util.WslFlatWatchRefresh
import org.elixir_lang.util.loadForEvents
import org.jetbrains.annotations.VisibleForTesting
import java.io.File

private val LOG = logger<SdkVersionFileWatcher>()

/**
 * Re-reads an installation replaced at the same path, which the SDK model does not report because the home and the SDK
 * are unchanged. Every version file is watched here: the platform watches an Elixir home's `ebin` only while a module
 * uses the SDK, and `releases/<N>/OTP_VERSION` is under no root at all.
 */
internal object SdkVersionFileWatcher {
    fun elixirVersionFiles(homePath: String): List<String> {
        val ebin = "${normalize(homePath)}/lib/elixir/ebin"

        return listOf("$ebin/elixir.app", "$ebin/Elixir.System.beam")
    }

    /** Lists `releases/`, so never call it under a lock. */
    fun otpVersionFiles(homePath: String): List<String> {
        val releases = File(homePath, "releases")

        return (releases.listFiles { file -> file.isDirectory && file.name.all(Char::isDigit) } ?: emptyArray())
            .map { "${normalize(it.path)}/OTP_VERSION" }
    }

    @VisibleForTesting
    fun homesToRevalidate(events: List<VFileEvent>, homeByWatchedPath: Map<String, String>): Set<String> {
        if (homeByWatchedPath.isEmpty()) return emptySet()
        val byNormalizedPath = homeByWatchedPath.mapKeys { (path, _) -> normalize(path) }

        // A new release directory is matched through its watched parent `releases/`. Only a numeric one: `RELEASES` and
        // `start_erl.data` live there too and `release_handler` rewrites them.
        return events.mapNotNullTo(mutableSetOf()) { event ->
            pathOf(event)?.let { path ->
                byNormalizedPath[path]
                    ?: path.substringAfterLast('/')
                        .takeIf { name -> name.isNotEmpty() && name.all(Char::isDigit) }
                        ?.let { byNormalizedPath[path.substringBeforeLast('/')] }
            }
        }
    }

    /** @return the paths actually watched, so a caller can tell whether the registration happened. */
    fun watch(
        homePaths: Collection<String>,
        parentDisposable: Disposable,
        revalidate: (String) -> Unit,
    ): Set<String> {
        // Lists `releases/` and refreshes from disk for every home, possibly on `\\wsl.localhost`; on the EDT the
        // platform runs that refresh inline.
        ThreadingAssertions.assertBackgroundThread()
        // A synchronous refresh must not be invoked from a thread holding a read action.
        ThreadingAssertions.assertNoReadAccess()
        // The SDK table publishes its add and remove events from inside a write action, and a background write
        // action reaches here without tripping either assertion above.
        check(!ApplicationManager.getApplication().isWriteAccessAllowed) {
            "Watching version files reads the filesystem and must not run under a write lock"
        }
        val homeByWatchedPath = mutableMapOf<String, String>()
        for (homePath in homePaths) {
            // An upgrade adding `releases/28` beside `releases/27` rewrites no watched file; only `releases/` sees it.
            val watchedForHome = elixirVersionFiles(homePath) +
                otpVersionFiles(homePath) +
                "${normalize(homePath)}/releases"
            for (path in watchedForHome) {
                homeByWatchedPath[path] = normalize(homePath)
            }
        }
        if (homeByWatchedPath.isEmpty()) return emptySet()

        // Subscribed before loading: loading a file that changed since it was read is itself a change event.
        ApplicationManager.getApplication().messageBus.connect(parentDisposable)
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        for (homePath in homesToRevalidate(events, homeByWatchedPath)) {
                            LOG.debug("A version file under '$homePath' changed")
                            revalidate(homePath)
                        }
                    }
                },
            )

        val localFileSystem = LocalFileSystem.getInstance()
        val watched = mutableSetOf<String>()
        val loaded = mutableListOf<VirtualFile>()
        val watchRequests = homeByWatchedPath.keys.mapNotNull { path ->
            localFileSystem.loadForEvents(path)?.let(loaded::add)
            localFileSystem.addRootToWatch(path, false)?.also { watched.add(path) }
        }
        Disposer.register(parentDisposable) { localFileSystem.removeWatchedRoots(watchRequests) }
        service<WslFlatWatchRefresh>().follow(homeByWatchedPath.keys, parentDisposable)

        // Finding a file the VFS already has compares neither timestamp nor length, so an installation replaced while
        // nothing was watching goes unnoticed unless it is marked dirty first.
        if (loaded.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, false, false, *loaded.toTypedArray())

        return watched
    }

    /** Declared non-null, but an event that cannot report a path must be skipped rather than throw. */
    private fun pathOf(event: VFileEvent): String? =
        (event.path as String?)?.takeIf { it.isNotEmpty() }?.let(::normalize)

    /** Keyed like the store, so a home and the event paths under it match whatever case each carries. */
    private fun normalize(path: String): String = installationKey(path) ?: path
}
