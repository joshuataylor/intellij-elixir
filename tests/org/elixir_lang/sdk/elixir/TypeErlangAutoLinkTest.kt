package org.elixir_lang.sdk.elixir

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.beam.BeamBytes
import org.elixir_lang.mix.sync.MixSyncTestHelpers.runSuspendOnPooledThread
import org.elixir_lang.sdk.SdkFixtures
import org.elixir_lang.sdk.SdkFixtures.fakeHome
import org.elixir_lang.sdk.SdkHomeKey
import org.elixir_lang.sdk.erlang_dependent.SdkAdditionalData
import java.io.File
import java.util.concurrent.Callable
import org.elixir_lang.sdk.SdkVersionsStore
import org.elixir_lang.sdk.SdkHomePaths
import org.elixir_lang.sdk.erlang_dependent.ErlangSdkResolver
import org.elixir_lang.sdk.erlang.Type as ErlangSdkType

class TypeErlangAutoLinkTest : PlatformTestCase() {

    private val registeredSdks = mutableListOf<Sdk>()

    override fun tearDown() {
        try {
            val table = ProjectJdkTable.getInstance()
            WriteAction.run<Throwable> {
                registeredSdks.forEach { table.removeJdk(it) }
            }
            registeredSdks.clear()
            SdkVersionsStore.getInstance().clearForTests()
        } finally {
            super.tearDown()
        }
    }

    /**
     * The add flow as the settings dialog drives it: the wrong Erlang SDK is registered first, so only reading the
     * unread build's `Elixir.System.beam` can pick the right one.
     */
    fun testConfiguringANewSdkPairsTheErlangItsBuildWasCompiledAgainst() {
        val elixirHome = SdkFixtures.elixirHome("1.20.5")
        File(elixirHome, "lib/elixir/ebin/Elixir.System.beam").writeBytes(BeamBytes.elixirSystemBeam())
        val major = runSuspendOnPooledThread { ElixirBuildInfo.elixirOtpRelease(elixirHome) }!!.toInt()
        val lower = register(
            SdkFixtures.erlangSdk(
                "Pairing Erlang ${major - 1}",
                SdkFixtures.erlangHome("${major - 1}", "${major - 1}.0"),
            ),
        )
        val matching = register(
            SdkFixtures.erlangSdk("Pairing Erlang $major", SdkFixtures.erlangHome("$major", "$major.0")),
        )
        val elixirSdk = ProjectJdkImpl("Pairing Elixir", Type.instance, elixirHome, "")
        // As a fresh session holds it: nothing read yet, the Elixir home included.
        SdkVersionsStore.getInstance().clearForTests()

        runSuspendOnPooledThread { ElixirSdkPathConfigurator.configure(elixirSdk) }

        val paired = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            ReadAction.nonBlocking(Callable { (elixirSdk.sdkAdditionalData as? SdkAdditionalData)?.getErlangSdk() })
                .executeSynchronously()
        }).get()
        assertEquals(
            "the build was compiled against OTP $major, so ${lower.name}, registered first, must not win",
            matching.name,
            paired?.name,
        )
    }

    /** The settings dialog pairs a new Elixir SDK before its Apply saves an Erlang SDK added in the same session. */
    fun testConfiguringANewSdkPairsAnErlangAddedInTheSameDialog() {
        val elixirHome = SdkFixtures.elixirHome("1.20.5")
        File(elixirHome, "lib/elixir/ebin/Elixir.System.beam").writeBytes(BeamBytes.elixirSystemBeam())
        val major = runSuspendOnPooledThread { ElixirBuildInfo.elixirOtpRelease(elixirHome) }!!.toInt()
        register(
            SdkFixtures.erlangSdk(
                "Saved Erlang ${major - 1}",
                SdkFixtures.erlangHome("${major - 1}", "${major - 1}.0"),
            ),
        )
        val pendingHome = SdkFixtures.erlangHome("$major", "$major.0")
        val sdkModel = dialogModel(ProjectJdkImpl("Pending Erlang $major", ErlangSdkType.instance, pendingHome, ""))
        val elixirSdk = ProjectJdkImpl("Dialog Elixir", Type.instance, elixirHome, "")
        SdkVersionsStore.getInstance().clearForTests()

        runSuspendOnPooledThread { ElixirSdkPathConfigurator.configure(elixirSdk, sdkModel) }

        val data = elixirSdk.sdkAdditionalData as? SdkAdditionalData
        assertEquals(
            "the build was compiled against OTP $major, which only the Erlang SDK not yet saved provides",
            "Pending Erlang $major",
            data?.getErlangSdkName(),
        )
    }

    fun testFindRegisteredErlangSdk_considersAnErlangSdkPendingInTheDialog() {
        erlangSdks("28.1")
        val pending = ProjectJdkImpl("Erlang 27.3.4 pending", ErlangSdkType.instance, fakeHome("erlang/27.3.4"), "")
        SdkVersionsStore.getInstance().setOtpVersion(fakeHome("erlang/27.3.4"), "27.3.4")
        val sdkModel = dialogModel(pending)

        val result = runReadActionBlocking {
            ErlangSdkResolver.bestRegisteredFor(elixirSdkCompiledAgainst("26"), sdkModel)
        }

        assertEquals("added in the open dialog, and nearer than any saved one", pending.name, result?.name)
    }

    fun testFindRegisteredErlangSdk_skipsAnErlangSdkRemovedInTheDialog() {
        val candidates = erlangSdks("26.2.5", "28.1")
        val sdkModel = dialogModel()
        sdkModel.removeSdk(sdkModel.findSdk(candidates.getValue("26.2.5").name)!!)

        val result = runReadActionBlocking {
            ErlangSdkResolver.bestRegisteredFor(elixirSdkCompiledAgainst("26"), sdkModel)
        }

        assertEquals(
            "removed in the open dialog, so Apply will delete it",
            candidates.getValue("28.1").name,
            result?.name,
        )
    }

    /** The dialog's model: a copy of every saved SDK, and [pending] added but not yet saved. */
    private fun dialogModel(vararg pending: Sdk): ProjectSdksModel =
        ProjectSdksModel().apply {
            reset(project)
            pending.forEach(::addSdk)
            Disposer.register(testRootDisposable) { disposeUIResources() }
        }

    private fun register(sdk: Sdk): Sdk {
        WriteAction.run<Throwable> { ProjectJdkTable.getInstance().addJdk(sdk) }
        registeredSdks.add(sdk)
        SdkFixtures.waitForRegistrationFills()
        return sdk
    }

    fun testFindRegisteredErlangSdk_prefersTheOtpTheElixirBuildWasCompiledAgainst() {
        val candidates = erlangSdks("24.3.4.6", "25.3.2.21", "26.2.5.21")
        val elixirSdk = elixirSdkCompiledAgainst("25")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdk) }

        assertEquals("the exact major the build was compiled against", candidates["25.3.2.21"], result)
    }

    fun testFindRegisteredErlangSdk_prefersTheNextHigherOtpWhenTheExactMajorIsAbsent() {
        val candidates = erlangSdks("24.3.4.6", "27.3.4", "28.1")
        val elixirSdk = elixirSdkCompiledAgainst("25")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdk) }

        assertEquals(
            "a newer OTP runs an older build, so higher majors come before lower",
            candidates["27.3.4"],
            result,
        )
    }

    fun testFindRegisteredErlangSdk_fallsBackToTheHighestLowerOtp() {
        val candidates = erlangSdks("23.3.4.20", "24.3.4.6")
        val elixirSdk = elixirSdkCompiledAgainst("27")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdk) }

        assertEquals("with nothing at or above, the closest below", candidates["24.3.4.6"], result)
    }

    fun testFindRegisteredErlangSdk_prefersAHigherMajorAtAnyDistance() {
        // Were OTP ever to number by year, 2026 must still beat 26, however large the gap.
        val candidates = erlangSdks("26.2.5.21", "2026.9.1")
        val elixirSdk = elixirSdkCompiledAgainst("27")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdk) }

        assertEquals("a higher major beats a lower one at any distance", candidates["2026.9.1"], result)
    }

    fun testFindRegisteredErlangSdk_prefersTheHighestVersionWithinAMajor() {
        val candidates = erlangSdks("27.0", "27.3.4", "27.1.2")
        val elixirSdk = elixirSdkCompiledAgainst("27")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdk) }

        assertEquals("the newest build of that major", candidates["27.3.4"], result)
    }

    private fun erlangSdks(vararg otpVersions: String): Map<String, Sdk> =
        otpVersions.associateWith { otpVersion ->
            val homePath = fakeHome("erlang/$otpVersion")
            val sdk = ProjectJdkImpl("Erlang $otpVersion", ErlangSdkType.instance, homePath, "")
            WriteAction.run<Throwable> { ProjectJdkTable.getInstance().addJdk(sdk) }
            registeredSdks.add(sdk)
            SdkFixtures.waitForRegistrationFills()
            SdkVersionsStore.getInstance().setOtpVersion(homePath, otpVersion)
            sdk
        }

    private fun elixirSdkCompiledAgainst(otpMajor: String): Sdk {
        val homePath = fakeHome("elixir/otp-$otpMajor")
        SdkVersionsStore.getInstance().setElixirVersions(homePath, ElixirVersions("1.20.5", OtpMajor.Known(otpMajor)))

        return ProjectJdkImpl("Elixir for OTP $otpMajor", Type.instance, homePath, "")
    }

    @RequiresEdt
    fun testFindRegisteredErlangSdk_returnsRegisteredSdk() {
        val erlangSdk = ProjectJdkImpl("Test Erlang SDK", ErlangSdkType()).apply {
            WriteAction.run<Throwable> {
                sdkModificator.apply {
                    homePath = fakeHome("erlang/28.0")
                    commitChanges()
                }
            }
        }

        WriteAction.run<Throwable> {
            ProjectJdkTable.getInstance().addJdk(erlangSdk)
        }
        registeredSdks.add(erlangSdk)
        SdkFixtures.waitForRegistrationFills()

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(anElixirSdk()) }
        assertNotNull("Should find the registered Erlang SDK", result)
        assertEquals("Test Erlang SDK", result!!.name)
    }

    @RequiresEdt
    fun testFindRegisteredErlangSdk_returnsNullWhenNoneRegistered() {
        // Remove any existing Erlang SDKs
        val table = ProjectJdkTable.getInstance()
        val existing = table.allJdks.filter { it.sdkType is ErlangSdkType }
        WriteAction.run<Throwable> {
            existing.forEach { table.removeJdk(it) }
        }

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(anElixirSdk()) }
        assertNull("Should return null when no Erlang SDK is registered", result)
    }

    @RequiresEdt
    fun testFindRegisteredErlangSdk_skipsACandidateWhoseVersionWasNeverRead() {
        val unread = ProjectJdkImpl("Erlang Never Read", ErlangSdkType.instance, fakeHome("erlang/unread"), "")
        val known = ProjectJdkImpl("Erlang Known", ErlangSdkType.instance, fakeHome("erlang/25.3.2.21"), "")
        WriteAction.run<Throwable> {
            ProjectJdkTable.getInstance().addJdk(unread)
            ProjectJdkTable.getInstance().addJdk(known)
        }
        registeredSdks.add(unread)
        registeredSdks.add(known)
        SdkFixtures.waitForRegistrationFills()
        SdkVersionsStore.getInstance().setOtpVersion(fakeHome("erlang/25.3.2.21"), "25.3.2.21")

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(elixirSdkCompiledAgainst("25")) }

        assertEquals("an installation whose version has not been read sorts last", known, result)
    }

    /** The real `SdkEnvironment.visibleFor` cannot reject a candidate without a second Eel machine, so a stub does. */
    @RequiresEdt
    fun testFindRegisteredErlangSdk_putsEveryCandidateToTheEnvironmentFirst() {
        val otherMachine =
            ProjectJdkImpl("Erlang Other Machine", ErlangSdkType.instance, fakeHome("other-machine/erlang"), "")
        val sameMachine = ProjectJdkImpl("Erlang Same Machine", ErlangSdkType.instance, fakeHome("same-machine/erlang"), "")
        WriteAction.run<Throwable> {
            ProjectJdkTable.getInstance().addJdk(otherMachine)
            ProjectJdkTable.getInstance().addJdk(sameMachine)
        }
        registeredSdks.add(otherMachine)
        registeredSdks.add(sameMachine)
        SdkFixtures.waitForRegistrationFills()
        val elixirSdk = ProjectJdkImpl("Elixir Same Machine", Type.instance, fakeHome("same-machine/elixir"), "")

        val result = runReadActionBlocking {
            ErlangSdkResolver.bestRegisteredFor(elixirSdk) { _ ->
                { erlangSdk -> erlangSdk.homePath!!.contains("same-machine") }
            }
        }

        assertEquals(
            "an Erlang SDK on another machine cannot run the Elixir SDK, so it is never auto-paired",
            "Erlang Same Machine",
            result?.name,
        )
    }

    @RequiresEdt
    fun testTheElixirSdkEnvironmentIsResolvedOncePerLookup() {
        val candidates = listOf("27.3.4", "26.2.5", "28.0").map { version ->
            ProjectJdkImpl("Erlang Resolved Once $version", ErlangSdkType.instance, fakeHome("same/erlang/$version"), "")
        }
        WriteAction.run<Throwable> { candidates.forEach { ProjectJdkTable.getInstance().addJdk(it) } }
        candidates.forEach(registeredSdks::add)
        SdkFixtures.waitForRegistrationFills()
        val elixirSdk = ProjectJdkImpl("Elixir Resolved Once", Type.instance, fakeHome("same/elixir"), "")
        var resolutions = 0

        runReadActionBlocking {
            ErlangSdkResolver.bestRegisteredFor(elixirSdk) { _ ->
                resolutions++
                { true }
            }
        }

        assertEquals(
            "the Elixir SDK's environment is one Eel machine lookup, and this scan holds the read lock",
            1,
            resolutions,
        )
    }

    fun testPromptForMiseErlangSdk_returnsNullForNonMiseElixirSdk() {
        val elixirSdk = ProjectJdkImpl("Test Elixir SDK", Type.instance).apply {
            WriteAction.run<Throwable> {
                sdkModificator.apply {
                    homePath = "/usr/local/lib/elixir"
                    commitChanges()
                }
            }
        }

        val result = ElixirInternalErlangSdkSetup.promptForMiseErlangSdk(elixirSdk)
        assertNull("Should return null for non-mise Elixir SDK", result)
    }

    fun testPromptForMiseErlangSdk_returnsNullWhenNoHomePath() {
        val elixirSdk = ProjectJdkImpl("Test Elixir SDK", Type.instance)

        val result = ElixirInternalErlangSdkSetup.promptForMiseErlangSdk(elixirSdk)
        assertNull("Should return null when Elixir SDK has no homePath", result)
    }

    @RequiresEdt
    fun testFindRegisteredErlangSdk_ignoresNonErlangSdks() {
        // Register an Elixir SDK (not Erlang) -- should not be returned
        val elixirSdk = ProjectJdkImpl("Test Elixir SDK", Type.instance).apply {
            WriteAction.run<Throwable> {
                sdkModificator.apply {
                    homePath = fakeHome("elixir/1.15")
                    commitChanges()
                }
            }
        }

        WriteAction.run<Throwable> {
            ProjectJdkTable.getInstance().addJdk(elixirSdk)
        }
        registeredSdks.add(elixirSdk)
        SdkFixtures.waitForRegistrationFills()

        val result = runReadActionBlocking { ErlangSdkResolver.bestRegisteredFor(anElixirSdk()) }
        assertNull("Should not return non-Erlang SDK", result)
    }

    /** Unregistered, as an SDK a settings dialog has not saved yet is: it is answered by home path. */
    private fun anElixirSdk() = ProjectJdkImpl("Auto-link Elixir", Type.instance, fakeHome("elixir/auto-link"), "")

    fun testRegisterErlangSdk_returnsNullForInvalidPath() {
        // Use a path that exists but isn't a valid Erlang home
        val result = ElixirInternalErlangSdkSetup.registerErlangSdk(System.getProperty("java.io.tmpdir"))
        assertNull("Should return null for invalid Erlang home path", result)
    }

    fun testRegisterErlangSdk_registersInProjectJdkTable() {
        // Find a real valid mise Erlang home on this machine
        val miseHomes = mutableMapOf<SdkHomeKey, String>()
        SdkHomePaths.mergeMise(miseHomes, "erlang")

        val erlangSdkType = ErlangSdkType()
        val validHome = miseHomes.values.firstOrNull { erlangSdkType.isValidSdkHome(it) }
            ?: return // Skip if no valid mise Erlang on this machine

        val sdk = ElixirInternalErlangSdkSetup.registerErlangSdk(validHome)
        assertNotNull("registerErlangSdk should return non-null for valid home", sdk)
        registeredSdks.add(sdk!!)
        SdkFixtures.waitForRegistrationFills()

        val table = ProjectJdkTable.getInstance()
        val found = table.allJdks.any { it.name == sdk.name }
        assertTrue("Registered SDK should be in ProjectJdkTable", found)
    }
}
