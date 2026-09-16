package org.elixir_lang.sdk

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.mix.sync.MixSyncTestHelpers.runSuspendOnPooledThread
import org.elixir_lang.sdk.SdkFixtures.elixirHome
import org.elixir_lang.sdk.SdkFixtures.erlangHome
import org.elixir_lang.sdk.erlang_dependent.SdkAdditionalData
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Only the installations an open project uses are watched: loading a path into the VFS to watch it boots the WSL
 * distro serving it, and a registered SDK nothing has opened is a distro the user did not ask to start. Pruning still
 * compares against every registered home, or it would forget the installations of SDKs no open project uses.
 * Registering the watch roots themselves is [SdkVersionFileWatcher]'s; this only decides what to watch.
 */
class SdkVersionWatchServiceTest : PlatformTestCase() {
    override fun tearDown() {
        try {
            ModuleRootModificationUtil.setModuleSdk(module, null)
            SdkVersionsStore.getInstance().clearForTests()
        } finally {
            super.tearDown()
        }
    }

    fun testTheHomesAModuleUsesAndTheErlangSdkPairedWithThem() {
        val erlangSdk = register(SdkFixtures.erlangSdk("Watched Erlang", erlangHome("27", "27.3.4")))
        val elixirSdk = register(SdkFixtures.elixirSdk("Watched Elixir", elixirHome("1.20.5")))
        SdkFixtures.commit(elixirSdk, SdkAdditionalData(erlangSdk, elixirSdk))
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)

        assertEquals(
            setOfNotNull(installationKey(erlangSdk.homePath), installationKey(elixirSdk.homePath)),
            SdkVersionWatchService.homesToWatch(),
        )
    }

    fun testAnSdkNoOpenProjectUsesIsNotWatched() {
        // Watching it would load its version files into the VFS, which on a `\\wsl.localhost` home boots the distro.
        register(SdkFixtures.elixirSdk("Registered Elsewhere", elixirHome("1.20.5")))

        assertEmpty(SdkVersionWatchService.homesToWatch())
    }

    fun testEveryRegisteredHomeIsStillOfferedToPruning() {
        val unused = register(SdkFixtures.erlangSdk("Registered Erlang", erlangHome("27", "27.3.4")))

        assertTrue(
            "pruning forgets what it is not shown, so it is shown every registered home, watched or not",
            installationKey(unused.homePath) in SdkVersionWatchService.registeredHomes(),
        )
    }

    fun testAnSdkWithNoHomeIsNotWatched() {
        val homeless = register(SdkFixtures.elixirSdk("Homeless Elixir", ""))
        ModuleRootModificationUtil.setModuleSdk(module, homeless)

        assertEmpty(SdkVersionWatchService.homesToWatch())
    }

    fun testTheSameHomeReachedTwiceIsWatchedOnce() {
        val home = erlangHome("27", "27.3.4")
        val erlangSdk = register(SdkFixtures.erlangSdk("Shared Erlang", home))
        val elixirSdk = register(SdkFixtures.elixirSdk("Sharing Elixir", home))
        SdkFixtures.commit(elixirSdk, SdkAdditionalData(erlangSdk, elixirSdk))
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)

        assertEquals(setOfNotNull(installationKey(home)), SdkVersionWatchService.homesToWatch())
    }

    fun testAModuleWithNoSdkWatchesNothing() {
        val elixirSdk = register(SdkFixtures.elixirSdk("Unassigned Elixir", elixirHome("1.20.5")))
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)
        assertEquals(
            "precondition: watched while the module uses it",
            setOfNotNull(installationKey(elixirSdk.homePath)),
            SdkVersionWatchService.homesToWatch(),
        )

        ModuleRootModificationUtil.setModuleSdk(module, null)

        assertEmpty(SdkVersionWatchService.homesToWatch())
    }

    fun testWatchingRefusesToRunUnderAReadAction() {
        val home = erlangHome("27", "27.3.4")

        val refusal: Throwable? = runSuspendOnPooledThread {
            runCatching {
                ReadAction.run<Throwable> { SdkVersionFileWatcher.watch(setOf(home), testRootDisposable) {} }
            }.exceptionOrNull()
        }

        assertNotNull("a synchronous VFS refresh must not be invoked while holding a read action", refusal)
    }

    fun testTheVersionFilesOfAHomeAreTheOnesWatched() {
        val home = erlangHome("27", "27.3.4")

        val watched = runSuspendOnPooledThread {
            SdkVersionFileWatcher.watch(setOf(home), testRootDisposable) {}
        }

        assertTrue(
            "the OTP_VERSION the version was read from must be among the watched paths; got $watched",
            watched.any { it.endsWith("/releases/27/OTP_VERSION") },
        )
    }

    fun testAnSdkAddedAfterInstallIsWatchedOffTheWriteAction() {
        val home = erlangHome("27", "27.3.4")
        val erlangSdk = SdkFixtures.erlangSdk("Watched After Add", home)
        val elixirSdk = register(SdkFixtures.elixirSdk("Watching Elixir", elixirHome("1.20.5")))
        SdkFixtures.commit(elixirSdk, SdkAdditionalData(erlangSdk, elixirSdk))
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)
        SdkVersionWatchService.install(testRootDisposable)

        // The table publishes its add inside its own write action. Watching there trips the refusal inside
        // SdkVersionFileWatcher.watch on the publishing thread, which surfaces as a logged error, not an exception.
        val (_, errors) = captureLoggedErrors { register(erlangSdk) }

        assertEmpty("adding an SDK must not watch inside the table's write action; got $errors", errors)
        val otpVersionFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("${FileUtil.toSystemIndependentName(home)}/releases/27/OTP_VERSION")
        assertNotNull("precondition: the version file of the added SDK is in the VFS", otpVersionFile)
        // Rewritten on each pass: the add hands the watch to a coroutine there is nothing here to await.
        SdkFixtures.waitUntil("an SDK added after install must end up watched") {
            WriteAction.run<Throwable> { VfsUtil.saveText(otpVersionFile!!, "27.3.5\n") }
            SdkVersionsStore.getInstance().otpVersion(home) == "27.3.5"
        }
    }

    @RequiresEdt
    fun testAVersionFileThatChangesIsReadAgain() {
        val home = erlangHome("27", "27.3.4")
        val erlangSdk = register(SdkFixtures.erlangSdk("Watched For Changes", home))
        val elixirSdk = register(SdkFixtures.elixirSdk("Watching Elixir", elixirHome("1.20.5")))
        SdkFixtures.commit(elixirSdk, SdkAdditionalData(erlangSdk, elixirSdk))
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)
        SdkVersionWatchService.install(testRootDisposable)
        // Driven rather than awaited: `install` starts the first watch on a coroutine, and a file rewritten before
        // that registration lands is a change nothing was watching for.
        runSuspendOnPooledThread { SdkVersionWatchService.rewatch() }

        val otpVersionFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("${FileUtil.toSystemIndependentName(home)}/releases/27/OTP_VERSION")
        assertNotNull("precondition: the file the watch is registered on is in the VFS", otpVersionFile)
        // Through the VFS, because that is what publishes the change; a write behind its back is only noticed by a
        // refresh nothing in production would perform.
        WriteAction.run<Throwable> { VfsUtil.saveText(otpVersionFile!!, "27.3.5\n") }

        SdkFixtures.waitUntil("a version file that changed must be read again") {
            SdkVersionsStore.getInstance().otpVersion(home) == "27.3.5"
        }
    }

    /**
     * What a mutation proves this guards: disabling [SdkVersionWatchService.rewatch] reddens it. Nulling the
     * live installation does **not** - so this is not a guard on `install`'s `installed === installation` check,
     * whatever the arrangement of two installs might suggest.
     */
    @RequiresEdt
    fun testAnInstallationStillWatchesAfterAnEarlierInstallIsDisposed() {
        val home = erlangHome("27", "27.3.4")
        val erlangSdk = register(SdkFixtures.erlangSdk("Reinstalled Erlang", home))
        val elixirSdk = register(SdkFixtures.elixirSdk("Reinstalling Elixir", elixirHome("1.20.5")))
        SdkFixtures.commit(elixirSdk, SdkAdditionalData(erlangSdk, elixirSdk))
        // The module is given its SDK only after the disposal below. Until then there is nothing to watch, so
        // neither install registers a watch and the one this asserts can only come from the rewatch that follows.
        val first = Disposer.newDisposable(testRootDisposable, "first install")
        SdkVersionWatchService.install(first)
        SdkVersionWatchService.install(testRootDisposable)

        Disposer.dispose(first)
        ModuleRootModificationUtil.setModuleSdk(module, elixirSdk)
        runSuspendOnPooledThread { SdkVersionWatchService.rewatch() }

        val otpVersionFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("${FileUtil.toSystemIndependentName(home)}/releases/27/OTP_VERSION")
        assertNotNull("precondition: the file the watch is registered on is in the VFS", otpVersionFile)
        // A value no sibling test writes, so a fill one of them left running cannot satisfy the wait.
        assertFalse(
            "precondition: the asserted version is not already held",
            SdkVersionsStore.getInstance().otpVersion(home) == "27.3.6",
        )
        WriteAction.run<Throwable> { VfsUtil.saveText(otpVersionFile!!, "27.3.6\n") }

        SdkFixtures.waitUntil("the installation that is still live must go on reading version files") {
            SdkVersionsStore.getInstance().otpVersion(home) == "27.3.6"
        }
    }

    /**
     * The case the watch exists for that no other test can reach: the installation is replaced while nothing is
     * watching, as an upgrade does with the IDE shut. The file is already in the VFS from the previous session, so
     * the load the watch performs finds a cached entry - and `findChild` compares neither timestamp nor length.
     *
     * [testAVersionFileThatChangesIsReadAgain] cannot stand in for this: it writes through the VFS, on a file the
     * watch is already registered on, so it exercises the in-session path only.
     */
    @RequiresEdt
    fun testAnInstallationReplacedWhileNothingWatchedIsReadAgain() {
        val home = erlangHome("27", "27.3.4")
        val otpVersionFile = File(home, "releases/27/OTP_VERSION")
        // Loaded first, as a previous session would have left it.
        val loaded = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(otpVersionFile.path))
        assertNotNull("precondition: the version file is in the VFS before anything watches it", loaded)

        // Written behind the VFS's back: through the VFS would publish the change this test is asking the watch
        // to discover for itself.
        otpVersionFile.writeText("27.3.9\n")

        val revalidated = CopyOnWriteArrayList<String>()
        runSuspendOnPooledThread { SdkVersionFileWatcher.watch(setOf(home), testRootDisposable) { revalidated.add(it) } }

        SdkFixtures.waitUntil("a home replaced while nothing watched it must be read again once it is watched") {
            revalidated.isNotEmpty()
        }
    }

    /** An in-place upgrade adds `releases/28` beside `releases/27` and rewrites nothing that was watched. */
    @RequiresEdt
    fun testAReleaseAddedBesideTheWatchedOneIsReadAgain() {
        val home = erlangHome("27", "27.3.4")
        val revalidated = CopyOnWriteArrayList<String>()
        runSuspendOnPooledThread { SdkVersionFileWatcher.watch(setOf(home), testRootDisposable) { revalidated.add(it) } }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        revalidated.clear()

        File(home, "releases/28").mkdirs()
        File(home, "releases/28/OTP_VERSION").writeText("28.1\n")
        // What the file watcher does with a created path the VFS does not have: marks the nearest parent it has dirty.
        val releases = LocalFileSystem.getInstance()
            .findFileByPathIfCached("${FileUtil.toSystemIndependentName(home)}/releases")
        assertNotNull("precondition: watching loaded the directory holding the releases", releases)
        VfsUtil.markDirtyAndRefresh(false, false, false, releases)

        SdkFixtures.waitUntil("a release added beside the watched one must read its home again") {
            revalidated.isNotEmpty()
        }
    }

    private fun register(sdk: Sdk): Sdk = SdkFixtures.register(sdk, testRootDisposable)
}
