package org.elixir_lang.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.elixir_lang.util.ElixirAppCoroutineService
import org.elixir_lang.sdk.elixir.Type as ElixirSdkType
import org.elixir_lang.sdk.erlang.Type as ErlangSdkType

private val LOG = logger<SdkVersionWatchService>()

/** Keeps [SdkVersionFileWatcher] watching every installation [SdkVersionsStore] holds. */
internal object SdkVersionWatchService {
    /**
     * Only homes already read: watching loads version files into the VFS, which boots a stopped `\\wsl.localhost`
     * distro, and a home that was read had its distro running at some point in this session.
     */
    fun homesToWatch(): Set<String> = SdkVersionsStore.getInstance().homes()

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
        connection.subscribe(SdkVersionsListener.TOPIC, SdkVersionsListener { _, _ -> requestRewatch() })
        connection
            .subscribe(
                ProjectJdkTable.JDK_TABLE_TOPIC,
                object : ProjectJdkTable.Listener {
                    // Published from inside the table's write action, so the filesystem work is launched.
                    override fun jdkAdded(jdk: Sdk) {
                        val homePath = jdk.homePath?.takeIf { isOurs(jdk) } ?: return
                        scope.launch { SdkVersionsFiller.fillIfUnread(homePath) }
                    }

                    override fun jdkRemoved(jdk: Sdk) {
                        val homePath = jdk.homePath?.takeIf { isOurs(jdk) } ?: return
                        scope.launch { forgetUnlessRegistered(homePath) }
                    }
                },
            )
    }

    private fun isOurs(sdk: Sdk): Boolean =
        sdk.sdkType === ElixirSdkType.instance || sdk.sdkType === ErlangSdkType.instance

    /**
     * Two Elixir SDKs pairing one build with different Erlang SDKs share a home, so removing one leaves the
     * installation in use. Lexical throughout: the store records which installation each spelling belongs to.
     */
    internal suspend fun forgetUnlessRegistered(homePath: String) {
        val store = SdkVersionsStore.getInstance()
        val canonicalHome = store.canonicalHome(homePath) ?: return
        val stillRegistered = readAction {
            val table = ProjectJdkTable.getInstance()
            (table.getSdksOfType(ElixirSdkType.instance) + table.getSdksOfType(ErlangSdkType.instance))
                .any { store.canonicalHome(it.homePath) == canonicalHome }
        }
        if (!stillRegistered) store.forgetInstallation(canonicalHome, homePath)
    }

    private fun requestRewatch() {
        val installation = installed ?: return
        // Collapsed: filling a project's homes records one after another. The flag is cleared before the pass, so a
        // change landing while it runs still schedules the next one.
        if (!installation.rewatchPending.compareAndSet(false, true)) return

        installation.scope.launch {
            installation.rewatchPending.set(false)
            rewatch()
        }
    }

    /** @return whether the watch was rebuilt. */
    suspend fun rewatch(): Boolean {
        val installation = installed ?: return false

        return installation.rewatches.withLock {
            val homes = homesToWatch()
            // A value re-read changes the store without changing its homes, and every rebuild does I/O per home.
            if (homes == installation.lastWatched.get()) {
                return@withLock false
            }
            val lifetime = homes.takeIf { it.isNotEmpty() }?.let { Disposer.newDisposable("SdkVersionFileWatcher") }
            // `tryRegister`: the parent may already be disposed, and parenting on a disposed parent throws.
            if (lifetime != null && !Disposer.tryRegister(installation.parentDisposable, lifetime)) {
                Disposer.dispose(lifetime)
                return@withLock false
            }
            // Cleared first so a throw below does not leave the previous set recorded, which would make every later
            // rewatch of that set skip while nothing is watched.
            installation.lastWatched.set(null)
            installation.watching.getAndSet(lifetime)?.let(Disposer::dispose)
            if (lifetime == null) {
                installation.lastWatched.set(homes)
                return@withLock true
            }

            LOG.debug("Watching the version files of ${homes.size} installation(s)")
            withContext(Dispatchers.IO) {
                SdkVersionFileWatcher.watch(homes, lifetime) { homePath ->
                    installation.scope.launch { SdkVersionsFiller.fill(homePath, clearWhenUnreadable = true) }
                }
            }
            // Only after `watch` returns: it throws for a distro that stopped answering, and a recorded set is skipped.
            installation.lastWatched.set(homes)
            true
        }
    }

    @Volatile
    private var installed: Installation? = null

    /**
     * [rewatches] serialises rewatches: each disposes the previous [watching] registration and builds the next, and two
     * interleaved would leak one registration's watch roots and subscription for the application's lifetime.
     */
    private class Installation(val scope: CoroutineScope, val parentDisposable: Disposable) {
        val watching = AtomicReference<Disposable?>()
        val lastWatched = AtomicReference<Set<String>?>()
        val rewatches = Mutex()
        val rewatchPending = AtomicBoolean(false)
    }
}
