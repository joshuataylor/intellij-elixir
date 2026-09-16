package org.elixir_lang.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import org.elixir_lang.util.ElixirAppCoroutineService
import org.elixir_lang.sdk.elixir.Type as ElixirSdkType
import org.elixir_lang.sdk.erlang.Type as ErlangSdkType

private val LOG = logger<SdkVersionWatchService>()

/**
 * Keeps [SdkVersionFileWatcher] pointed at the installations the SDK table names, so an install replaced at the same
 * path is read again. The set changes whenever an SDK is added or removed, and each change replaces the previous watch
 * registration rather than adding to it.
 */
internal object SdkVersionWatchService {
    /**
     * The installations worth watching: the homes the open projects' modules use, and the Erlang SDKs those are
     * paired with, once each.
     *
     * Not every registered home. Watching one loads its version files into the VFS, and on a `\\wsl.localhost` path
     * that boots the distro serving it - a distro the user did not ask to start, for an SDK nothing has opened.
     */
    @RequiresReadLock
    fun homesToWatch(): Set<String> {
        ThreadingAssertions.assertReadAccess()

        return ProjectManager.getInstance().openProjects
            .filterNot(Project::isDisposed)
            .flatMap(SdkVersionsFiller::homePathsUsedBy)
            .mapNotNullTo(mutableSetOf(), ::installationKey)
    }

    /**
     * Every home a registered Elixir or Erlang SDK points at, once each, for pruning: it forgets the installations it
     * is not shown, so showing it only what is watched would forget every SDK no open project uses.
     */
    @RequiresReadLock
    fun registeredHomes(): Set<String> {
        ThreadingAssertions.assertReadAccess()
        val table = ProjectJdkTable.getInstance()

        // Keyed by the one definition the store, pruning and the watcher all share, so one installation is one entry
        // whichever SDK named it - a second normalisation that folded differently is how a live entry gets pruned.
        return (table.getSdksOfType(ElixirSdkType.instance) + table.getSdksOfType(ErlangSdkType.instance))
            .mapNotNullTo(mutableSetOf()) { sdk -> installationKey(sdk.homePath) }
    }

    /**
     * Watches what the open projects use now, and again whenever that set can have changed - a project opening or
     * closing, an SDK added or removed - until [parentDisposable] is disposed. A version file that changes is read
     * again with `clearWhenUnreadable`, because the files themselves said so.
     */
    fun install(parentDisposable: Disposable) {
        val scope = service<ElixirAppCoroutineService>().supervisedChildScope("SdkVersionWatchService")
        Disposer.register(parentDisposable) { scope.cancel() }
        val installation = Installation(scope, parentDisposable)
        // The previous installation's subscription and watch are held by its own scope and handle, so it would go on
        // driving rewatch unless torn down here.
        val previous = installed
        installed = installation
        previous?.watching?.getAndSet(null)?.let(Disposer::dispose)
        previous?.scope?.cancel()
        // Cleared only while this installation is still the live one: otherwise disposing the first of two installs
        // disables the second, and every later rewatch returns silently.
        Disposer.register(parentDisposable) { if (installed === installation) installed = null }

        scope.launch { rewatch() }

        val connection = ApplicationManager.getApplication().messageBus.connect(scope)
        // On the application bus, because a project-level connection is already disconnected by the time a project
        // reports that it closed.
        connection.subscribe(
            ProjectCloseListener.TOPIC,
            object : ProjectCloseListener {
                override fun projectClosed(project: Project) {
                    scope.launch { rewatch() }
                }
            },
        )
        connection
            .subscribe(
                ProjectJdkTable.JDK_TABLE_TOPIC,
                object : ProjectJdkTable.Listener {
                    // The table publishes these from inside the write action that changed it, and both reading an
                    // installation and watching one read the filesystem, so the work is handed to the scope rather
                    // than done here.
                    override fun jdkAdded(jdk: Sdk) {
                        val ours = jdk.sdkType === ElixirSdkType.instance || jdk.sdkType === ErlangSdkType.instance
                        val homePath = jdk.homePath?.takeIf { ours }
                        scope.launch {
                            // Before the watch: an installation nothing has read yet answers "not recorded" to every
                            // reader until something does, and registering an SDK is the moment it becomes one.
                            homePath?.let { SdkVersionsFiller.fillIfUnread(it) }
                            rewatch()
                        }
                    }

                    override fun jdkRemoved(jdk: Sdk) {
                        scope.launch { rewatch() }
                    }
                },
            )
    }

    /**
     * Watches what the open projects use now, replacing whatever was watched before. Called when that set can have
     * changed and the change is not an SDK the table reported: a project whose model has finished loading, or one
     * that has closed. Does nothing before [install].
     */
    suspend fun rewatch() {
        val installation = installed ?: return

        installation.rewatches.withLock {
            val homes = readAction { homesToWatch() }
            val lifetime = homes.takeIf { it.isNotEmpty() }?.let { Disposer.newDisposable("SdkVersionFileWatcher") }
            // `tryRegister`: the parent may already be disposed, and parenting on a disposed parent throws.
            if (lifetime != null && !Disposer.tryRegister(installation.parentDisposable, lifetime)) {
                Disposer.dispose(lifetime)
                return@withLock
            }
            installation.watching.getAndSet(lifetime)?.let(Disposer::dispose)
            if (lifetime == null) return@withLock

            LOG.debug("Watching the version files of ${homes.size} installation(s)")
            // Lists `releases/` and loads files into the VFS, so it belongs on the dispatcher for blocking work
            // rather than the one sized for CPU-bound coroutines.
            withContext(Dispatchers.IO) {
                SdkVersionFileWatcher.watch(homes, lifetime) { homePath ->
                    installation.scope.launch { SdkVersionsFiller.fill(homePath, clearWhenUnreadable = true) }
                }
            }
        }
    }

    @Volatile
    private var installed: Installation? = null

    /**
     * [watching] is replaced, never added to, so the handle is atomic: two rewatches in flight would otherwise leave
     * one registration undisposed, leaking its watch roots and its subscription for the application's lifetime.
     * [rewatches] keeps them from interleaving, since each disposes the previous registration and builds the next.
     */
    private class Installation(val scope: CoroutineScope, val parentDisposable: Disposable) {
        val watching = AtomicReference<Disposable?>()
        val rewatches = Mutex()
    }
}
