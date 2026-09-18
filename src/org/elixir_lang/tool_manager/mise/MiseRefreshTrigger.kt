package org.elixir_lang.tool_manager.mise

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import org.elixir_lang.mise.Mise
import org.elixir_lang.mise.MiseResult
import org.elixir_lang.tool_manager.ToolManagerRefreshTrigger
import org.elixir_lang.tool_manager.ToolManagerResult
import org.elixir_lang.util.loadForEvents
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<MiseRefreshTrigger>()

/**
 * File-name patterns to watch for in content root directories.
 *
 * These cover all standard mise config file names.  If a file matching any of these
 * patterns is created in a content root directory, a re-scan is triggered even if the
 * file was not previously known to mise.
 */
private val MISE_CONFIG_PATTERNS = listOf(
    Regex("""^mise\.toml$"""),
    Regex("""^mise\..+\.toml$"""),   // mise.local.toml, mise.test.toml, etc.
    Regex("""^\.tool-versions$"""),
)

/**
 * [ToolManagerRefreshTrigger] implementation for mise.
 *
 * On [install]:
 * 1. Calls `mise config ls --json` for each content root to discover the exact set of
 *    config files that mise is currently reading (including user-global config files such
 *    as `~/.config/mise/config.toml`).
 * 2. Registers [LocalFileSystem] watch roots for every discovered file so the VFS tracks
 *    changes to files that may lie outside the project directory.
 * 3. Subscribes a [BulkFileListener] that fires `onChangeDetected` when:
 *    - Any watched exact file is modified or deleted.
 *    - A file matching [MISE_CONFIG_PATTERNS] is created inside a content root directory
 *      (handles the case where the user adds a new config file not yet known to mise).
 *    - A pin the last scan reported installed is removed, or one it reported not installed finishes installing.
 */
object MiseRefreshTrigger : ToolManagerRefreshTrigger {

    override fun install(
        project: Project,
        contentRoots: List<Path>,
        results: Map<Path, ToolManagerResult?>,
        onChangeDetected: () -> Unit,
    ): Disposable {
        val lifetime = Disposer.newCheckedDisposable("MiseRefreshTrigger")

        // --- 1. The pins the last scan reported, and the directories mise installs them in ---
        // mise creates a version's directory as the install starts and reports it installed only at the end, so a
        // directory appearing beside a pin that is not installed starts a poll of mise rather than a re-scan.
        val installWatches = installWatches(results)
        val installedPaths = installWatches.filter { it.installed }.mapTo(mutableSetOf()) { it.installPath }
        val pending = installWatches.filterNot { it.installed }
        val pendingToolDirs = pending.mapTo(mutableSetOf()) { it.toolDir }
        val pendingPaths = pending.mapTo(mutableSetOf()) { it.installPath }
        val poll = project.service<MiseInstallPoll>()
        // Before anything that runs mise, so a running poll is handed its next trigger as soon as it can be.
        poll.follow(
            pending.takeIf { it.isNotEmpty() }?.let {
                MiseInstallPoll.Pending(
                    check = { checkPending(it) },
                    onInstalled = onChangeDetected,
                    owner = lifetime,
                    installPaths = pendingPaths,
                )
            }
        )
        val startPoll = { poll.start() }

        // --- 2. Discover exact config files from mise for each content root ---
        // Mise.configFiles() already converts WSL Linux paths to Windows UNC paths (see Mise.kt),
        // so Path objects here are always host-native absolute paths.
        // Normalise to forward slashes so strings match VFileEvent.path values.
        val exactPaths: Set<String> = contentRoots
            .flatMap { root ->
                Mise.configFiles(root)
                    ?.map { FileUtil.toSystemIndependentName(it.toString()) }
                    ?.also { LOG.trace("install: $root → ${it.size} config file(s)") }
                    ?: emptyList()
            }
            .toSet()

        LOG.debug("install: watching ${exactPaths.size} exact file(s) across ${contentRoots.size} content root(s)")

        // --- 3. Discover the mise trusted-configs directory ---
        // Watching this directory lets us detect when the user runs `mise trust`, which writes a
        // file here but does not touch any of the watched config files themselves.
        val trustedConfigsDirString: String = contentRoots.firstOrNull()
            ?.let { Mise.stateDir(it) }
            ?.resolve("trusted-configs")
            ?.let { FileUtil.toSystemIndependentName(it.toString()) }
            ?.also { LOG.debug("install: also watching trusted-configs dir: $it") }
            ?: ""

        // --- 4. Register LocalFileSystem watch roots ---
        // Watch each exact file individually (handles files outside the project, e.g. ~/.config/mise/config.toml).
        // Also watch each content root directory (non-recursive) so new files can be detected.
        // Watch the trusted-configs directory so `mise trust` runs trigger a re-scan.
        // Both exactPaths and content root strings are in system-independent (forward-slash) form,
        // which LocalFileSystem normalises internally.
        // The trusted-configs and tool directories may not exist until mise first writes to them, so the directory
        // holding each is watched too, and each is loaded when it appears.
        val loadedWhenCreated: Set<String> = installWatches.mapTo(linkedSetOf()) { it.toolDir } +
            listOfNotNull(trustedConfigsDirString.takeIf { it.isNotEmpty() })
        val watchPaths: Set<String> = exactPaths +
                contentRoots.map { FileUtil.toSystemIndependentName(it.toString()) } +
                loadedWhenCreated.map { it.substringBeforeLast('/') } +
                loadedWhenCreated
        val watchRequests: MutableSet<LocalFileSystem.WatchRequest> = ConcurrentHashMap.newKeySet()
        val lfs = LocalFileSystem.getInstance()
        watchPaths(lfs, watchPaths, watchRequests)

        // Unregister all watch requests when the trigger is disposed.
        Disposer.register(lifetime) {
            lfs.removeWatchedRoots(watchRequests)
            LOG.trace("install: removed ${watchRequests.size} watch request(s)")
        }

        // An install already running, or one interrupted, has created its directory before this trigger could see it.
        for (path in pendingPaths) if (Files.isDirectory(Path.of(path))) poll.startOnce(path)

        // Normalise content root strings to forward slashes to match the paths VFS events report.
        val contentRootStrings: Set<String> = contentRoots.map { FileUtil.toSystemIndependentName(it.toString()) }.toSet()

        // --- 5. Subscribe BulkFileListener for VFS change events ---
        ApplicationManager.getApplication().messageBus
            .connect(lifetime)
            .subscribe(
                com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        // Loading a directory lists it, which must not happen inside the write action events arrive in.
                        for (event in events) {
                            if (event is VFileCreateEvent && event.isDirectory && event.path in loadedWhenCreated) {
                                AppExecutorUtil.getAppExecutorService().execute {
                                    if (lifetime.isDisposed) return@execute
                                    val hasFile = directoryCreated(lfs, event.path, watchRequests)
                                    // Disposal may have removed the watches before this one was added.
                                    if (lifetime.isDisposed) {
                                        lfs.removeWatchedRoots(watchRequests)
                                    } else if (event.path in pendingToolDirs) {
                                        startPoll()
                                    } else if (hasFile) {
                                        onChangeDetected()
                                    }
                                }
                            }
                        }
                        for (event in events) {
                            if (startsAnInstall(event, pendingToolDirs)) {
                                LOG.debug("install: '${event.path}' started installing, polling mise until it has")
                                startPoll()
                            } else if (uninstalls(event, installedPaths) || shouldTrigger(
                                    event,
                                    exactPaths,
                                    contentRootStrings,
                                    trustedConfigsDirString,
                                )
                            ) {
                                LOG.debug("install: change detected via ${event::class.simpleName} on '${event.path}', triggering re-scan")
                                onChangeDetected()
                                return  // one call per batch is sufficient; scan is debounced
                            }
                        }
                    }
                }
            )

        return lifetime
    }

    /** Blocks on `mise ls` once per root, so it runs only inside the poll, off every lock. */
    private fun checkPending(pending: List<InstallWatch>): MiseInstallPoll.Check {
        val checks = pending.groupBy({ it.root }, { it.tool }).map { (root, tools) ->
            checkOf(Mise.resolveVersions(root), tools.toSet())
        }
        return when {
            MiseInstallPoll.Check.INSTALLED in checks -> MiseInstallPoll.Check.INSTALLED
            MiseInstallPoll.Check.FAILED in checks -> MiseInstallPoll.Check.FAILED
            else -> MiseInstallPoll.Check.NOT_YET
        }
    }

    @VisibleForTesting
    internal fun watchPaths(
        lfs: LocalFileSystem,
        paths: Collection<String>,
        watchRequests: MutableSet<LocalFileSystem.WatchRequest>,
    ) {
        for (path in paths) watchAndLoad(lfs, path, watchRequests)
    }

    @VisibleForTesting
    internal fun watchAndLoad(
        lfs: LocalFileSystem,
        dir: String,
        watchRequests: MutableSet<LocalFileSystem.WatchRequest>,
    ): VirtualFile? {
        lfs.addRootToWatch(dir, /* watchRecursively = */ false)?.let(watchRequests::add)
        return lfs.loadForEvents(dir)
    }

    /**
     * Whether whatever created [dir], such as a first `mise trust`, had already written a file into it by the time it
     * was loaded, since that file raises no event of its own.
     */
    @VisibleForTesting
    internal fun directoryCreated(
        lfs: LocalFileSystem,
        dir: String,
        watchRequests: MutableSet<LocalFileSystem.WatchRequest>,
    ): Boolean = watchAndLoad(lfs, dir, watchRequests)?.children?.any { !it.isDirectory } == true

    internal enum class Tool { ELIXIR, ERLANG }

    internal data class InstallWatch(val tool: Tool, val root: Path, val installPath: String, val installed: Boolean) {
        val toolDir: String get() = installPath.substringBeforeLast('/')
    }

    @VisibleForTesting
    internal fun checkOf(result: MiseResult?, tools: Set<Tool>): MiseInstallPoll.Check {
        val versions = (result as? MiseResult.Success)?.versions ?: return MiseInstallPoll.Check.FAILED
        val installed = tools.any { tool ->
            when (tool) {
                Tool.ELIXIR -> versions.elixir
                Tool.ERLANG -> versions.erlang
            }?.installed == true
        }

        return if (installed) MiseInstallPoll.Check.INSTALLED else MiseInstallPoll.Check.NOT_YET
    }

    @VisibleForTesting
    internal fun installWatches(results: Map<Path, ToolManagerResult?>): List<InstallWatch> =
        results.flatMap { (root, result) ->
            val versions = (result as? ToolManagerResult.Success)?.versions ?: return@flatMap emptyList()
            listOfNotNull(
                versions.elixir?.let { InstallWatch(Tool.ELIXIR, root, FileUtil.toSystemIndependentName(it.installPath), it.installed) },
                versions.erlang?.let { InstallWatch(Tool.ERLANG, root, FileUtil.toSystemIndependentName(it.installPath), it.installed) },
            )
        }

    @VisibleForTesting
    internal fun startsAnInstall(event: VFileEvent, pendingToolDirs: Set<String>): Boolean =
        event is VFileCreateEvent && event.isDirectory && event.path.substringBeforeLast('/') in pendingToolDirs

    @VisibleForTesting
    internal fun uninstalls(event: VFileEvent, installedPaths: Set<String>): Boolean =
        event is VFileDeleteEvent && installedPaths.any { it == event.path || it.startsWith("${event.path}/") }

    @VisibleForTesting
    internal fun shouldTrigger(
        event: VFileEvent,
        exactPaths: Set<String>,
        contentRootStrings: Set<String>,
        trustedConfigsDirString: String,
    ): Boolean = when (event) {
        // Exact watched file was modified; or a trust-state file in the trusted-configs dir was
        // updated (user re-ran `mise trust` on an already-trusted config).
        is VFileContentChangeEvent ->
            event.path in exactPaths ||
                    (trustedConfigsDirString.isNotEmpty() && event.path.startsWith("$trustedConfigsDirString/"))

        // Exact watched file was deleted - re-scan so mise can report absence.
        // A trust-state file was deleted (user untrusted the config) - re-scan to surface the error.
        is VFileDeleteEvent ->
            event.path in exactPaths ||
                    (trustedConfigsDirString.isNotEmpty() && event.path.startsWith("$trustedConfigsDirString/"))

        // A new file was created: either a config file in a content root, or a trust-state file
        // written by `mise trust` in the global trusted-configs directory.
        is VFileCreateEvent -> {
            val parentPath = event.parent.path
            when {
                trustedConfigsDirString.isNotEmpty() && parentPath == trustedConfigsDirString -> true
                parentPath in contentRootStrings -> MISE_CONFIG_PATTERNS.any { it.matches(event.childName) }
                else -> false
            }
        }

        else -> false
    }
}
