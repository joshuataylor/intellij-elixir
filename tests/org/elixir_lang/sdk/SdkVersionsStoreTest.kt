package org.elixir_lang.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.sdk.elixir.ElixirVersions
import org.elixir_lang.sdk.elixir.OtpMajor
import org.elixir_lang.sdk.erlang.Release
import org.elixir_lang.sdk.wsl.MockWslCompatService
import org.elixir_lang.sdk.wsl.WslCompatService
import org.elixir_lang.sdk.wsl.LEGACY_WSL_PREFIX
import org.elixir_lang.sdk.wsl.MODERN_WSL_PREFIX
import java.util.concurrent.CopyOnWriteArrayList

class SdkVersionsStoreTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // The real service rewrites WSL prefixes only on Windows; the mock rewrites them on every OS.
        ApplicationManager.getApplication().registerOrReplaceServiceInstance(
            WslCompatService::class.java,
            MockWslCompatService(),
            testRootDisposable,
        )
    }

    private val store get() = SdkVersionsStore.getInstance()

    override fun tearDown() {
        try {
            store.clearForTests()
        } finally {
            super.tearDown()
        }
    }

    fun testElixirVersionsAreReadBackByHomePath() {
        store.setElixirVersions("/fake/elixir/1.20.5", ElixirVersions("1.20.5", OtpMajor.Known("28")))

        assertEquals(ElixirVersions("1.20.5", OtpMajor.Known("28")), store.elixirVersions("/fake/elixir/1.20.5"))
    }

    fun testAnUnknownHomeHasNoVersions() {
        assertNull(store.elixirVersions("/fake/elixir/unknown"))
        assertNull(store.otpVersion("/fake/erlang/unknown"))
    }

    fun testANullHomeHasNoVersions() {
        assertNull(store.elixirVersions(null))
        assertNull(store.otpVersion(null))
    }

    fun testTheTwoWslPrefixesAreOneInstall() {
        store.setOtpVersion(LEGACY_WSL_PREFIX + """IntellijElixirWSLDistribution\opt\erlang""", "27.3.4")

        assertEquals(
            "a distro path spelled either way is the same installation",
            "27.3.4",
            store.otpVersion(MODERN_WSL_PREFIX + """IntellijElixirWSLDistribution\opt\erlang"""),
        )
    }

    fun testHomePathsAreComparedSystemIndependently() {
        store.setOtpVersion("C:\\fake\\erlang\\27.3.4", "27.3.4")

        assertEquals("27.3.4", store.otpVersion("C:/fake/erlang/27.3.4"))
    }

    fun testHomePathsAreComparedTheWayTheFilesystemDoes() {
        store.setOtpVersion("C:/Fake/Erlang/27.3.4", "27.3.4")

        val differingOnlyInCase = store.otpVersion("c:/fake/erlang/27.3.4")

        if (SystemInfoRt.isFileSystemCaseSensitive) {
            assertNull("here these are two different installations", differingOnlyInCase)
        } else {
            // FileUtil.pathsEqual ignores case here; a store that did not would miss the watch.
            assertEquals("here these are one installation", "27.3.4", differingOnlyInCase)
        }
    }

    fun testAnSdkTheDialogHasNotSavedIsAnsweredByItsHome() {
        val erlangSdk = SdkFixtures.erlangSdk("Unsaved Erlang", "/fake/erlang/unsaved")

        store.setOtpVersion("/fake/erlang/unsaved", "27.3.4")

        assertEquals(
            "an SDK that is not in the table is answered by its home",
            "27.3.4",
            store.otpVersion(erlangSdk.homePath),
        )
    }

    fun testAChangedValueIsPublished() {
        val published = published()
        store.setElixirVersions("/fake/elixir/published", ElixirVersions("1.19.5", OtpMajor.Known("27")))
        published.clear()

        store.setElixirVersions("/fake/elixir/published", ElixirVersions("1.19.6", OtpMajor.Known("27")))

        assertEquals(listOf("/fake/elixir/published"), published.toList())
    }

    fun testAnUnchangedValuePublishesNothing() {
        val published = published()
        store.setOtpVersion("/fake/erlang/unchanged", "27.3.4")
        published.clear()

        store.setOtpVersion("/fake/erlang/unchanged", "27.3.4")

        assertEmpty(published)
    }

    fun testForgettingAHomeRemovesItsVersions() {
        store.setOtpVersion("/fake/erlang/forgotten", "27.3.4")

        store.forgetInstallation("/fake/erlang/forgotten")

        assertNull(store.otpVersion("/fake/erlang/forgotten"))
    }

    fun testTwoWslHomesDifferingOnlyInCaseAreDifferentInstallations() {
        store.setOtpVersion("//wsl.localhost/IntellijElixirWSLDistribution/home/User/erlang", "27.3.4")
        store.setOtpVersion("//wsl.localhost/IntellijElixirWSLDistribution/home/user/erlang", "26.2.5")
        store.setOtpVersion("C:/Erlang/User", "27.3.4")
        store.setOtpVersion("C:/Erlang/user", "26.2.5")

        assertEquals(
            "a path inside a distro is served by a case-sensitive filesystem, whatever the host folds",
            "27.3.4",
            store.otpVersion("//wsl.localhost/IntellijElixirWSLDistribution/home/User/erlang"),
        )
        if (!SystemInfoRt.isFileSystemCaseSensitive) {
            assertEquals(
                "a host path differing only in case is one installation on the same machine",
                "26.2.5",
                store.otpVersion("C:/Erlang/User"),
            )
        }
    }

    fun testRecordingAnInstallationAnswersForBothSpellings() {
        val canonicalHome = "/fake/erlang/27.3.4"
        val configuredHome = "/fake/erlang/latest"

        store.record(canonicalHome, configuredHome, null, Release.of("27.3.4"))

        assertEquals("27.3.4", store.otpVersion(canonicalHome))
        assertEquals(
            "a reader holding the configured spelling never has to resolve it",
            "27.3.4",
            store.otpVersion(configuredHome),
        )
        assertEquals(
            "and both name the same installation, so reparsing and forgetting agree about them",
            store.canonicalHome(canonicalHome),
            store.canonicalHome(configuredHome),
        )
    }

    fun testRecordingTheSameValuesAgainIsNotAChange() {
        val published = published()
        store.record("/fake/erlang/again", "/fake/erlang/again", null, Release.of("27.3.4"))
        published.clear()

        assertFalse(
            "re-reading an installation that has not changed must not reparse every file using it",
            store.record("/fake/erlang/again", "/fake/erlang/again", null, Release.of("27.3.4")),
        )
        assertEmpty(published)
    }

    fun testRecordingAHomeThatCannotBeKeyedStoresNothing() {
        val published = published()

        assertFalse("a blank home names no installation", store.record("   ", "   ", null, Release.of("27.3.4")))
        assertEmpty(published)
    }

    fun testForgettingAnInstallationThatWasNeverHeldIsNotAChange() {
        val published = published()

        assertFalse(store.forgetInstallation("/fake/erlang/never", "/fake/erlang/never"))
        assertEmpty("nothing was held, so nothing changed and nothing is reparsed", published)
    }

    fun testTheInstallationAHomeBelongsToIsItselfAValue() {
        val configuredHome = "/fake/erlang/latest"
        store.record("/fake/erlang/27.3.4", configuredHome, null, Release.of("27.3.4"))
        val published = published()

        // What a WSL outage does: the same versions, but the home now resolves to itself. Reparsing keys off the
        // installation, so re-keying an entry is a change even when every version is the same.
        assertTrue(store.record(configuredHome, configuredHome, null, Release.of("27.3.4")))

        assertFalse(published.isEmpty())
    }

    fun testAHomeThatWasNeverReadBelongsToItself() {
        assertEquals(
            "with nothing recorded the home is its own installation, so a comparison still has two sides",
            installationKey("/fake/erlang/unheard-of"),
            store.canonicalHome("/fake/erlang/unheard-of"),
        )
    }

    fun testEverySpellingIsHeld() {
        store.record("/fake/erlang/27.3.4", "/fake/erlang/latest", null, Release.of("27.3.4"))

        assertEquals(
            "the watch covers what the store holds, and a change seen through either spelling must be read",
            setOfNotNull(installationKey("/fake/erlang/27.3.4"), installationKey("/fake/erlang/latest")),
            store.homes(),
        )
    }

    fun testAChangeToTheTextAloneIsPublished() {
        val published = published()
        store.setOtpVersion("/fake/erlang/repackaged", "25.3.2.7")
        published.clear()

        // Ordered equal, because a package revision is not a release part, yet the text is what the SDK shows.
        store.setOtpVersion("/fake/erlang/repackaged", "25.3.2.7-1")

        assertEquals(listOf(installationKey("/fake/erlang/repackaged")), published.toList())
        assertEquals("25.3.2.7-1", store.otpVersion("/fake/erlang/repackaged"))
    }

    fun testATrailingSeparatorNamesTheSameInstallation() {
        store.setOtpVersion("/fake/erlang/trailing/", "27.3.4")

        assertEquals(
            "a home the user typed with a trailing separator is the same directory",
            "27.3.4",
            store.otpVersion("/fake/erlang/trailing"),
        )
    }

    fun testABlankHomeNamesNoInstallation() {
        assertNull(installationKey(""))
        assertNull(installationKey("   "))
    }

    private fun published(): MutableList<String> {
        val published = CopyOnWriteArrayList<String>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(
                SdkVersionsListener.TOPIC,
                SdkVersionsListener { _, canonicalHome -> published.add(canonicalHome) },
            )
        return published
    }
}
