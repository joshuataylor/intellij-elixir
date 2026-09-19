package org.elixir_lang.sdk.elixir

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.sdk.wsl.MockWslCompatService
import org.elixir_lang.sdk.wsl.WslCompatService
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable

/**
 * Tests for [ElixirVersionDetector]'s `elixir.app` parsing.
 *
 * Each test creates a temporary directory tree so no real Elixir installation is required.
 */
class ElixirVersionDetectorTest : PlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // MockWslCompatService returns paths unchanged (identity transform)
        ApplicationManager.getApplication().registerOrReplaceServiceInstance(
            WslCompatService::class.java,
            MockWslCompatService(),
            testRootDisposable,
        )
    }

    // ---------------------------------------------------------------
    // Happy-path parsing
    // ---------------------------------------------------------------

    fun testElixirVersion_simpleVersion() {
        val sdkHome = createSdkHomeWithApp("1.15.7")
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertEquals("1.15.7", version)
    }

    fun testElixirVersion_preReleaseVersion() {
        val sdkHome = createSdkHomeWithApp("1.20.0-rc.0")
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertEquals("1.20.0-rc.0", version)
    }

    fun testElixirVersion_devVersion() {
        val sdkHome = createSdkHomeWithApp("1.21.0-dev")
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertEquals("1.21.0-dev", version)
    }

    fun testElixirVersion_whitespaceTolerance() {
        // Extra whitespace around the vsn value and braces
        val sdkHome = createSdkHomeWithCustomApp(
            """
            {application, elixir, [
              { vsn ,  "1.16.2"  },
              {modules, []}
            ]}.
            """.trimIndent(),
        )
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertEquals("1.16.2", version)
    }

    // ---------------------------------------------------------------
    // Graceful failure paths
    // ---------------------------------------------------------------

    fun testElixirVersion_missingAppFile_returnsNull() {
        // SDK home exists but has no elixir.app
        val sdkHome = Files.createTempDirectory("elixir-sdk-test-").toString()
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertNull("Should return null when elixir.app is absent", version)
    }

    fun testElixirVersion_missingVsnField_returnsNull() {
        val sdkHome = createSdkHomeWithCustomApp(
            """
            {application, elixir, [
              {description, "elixir"},
              {modules, []}
            ]}.
            """.trimIndent(),
        )
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertNull("Should return null when vsn key is absent", version)
    }

    fun testElixirVersion_emptyAppFile_returnsNull() {
        val sdkHome = createSdkHomeWithCustomApp("")
        VfsRootAccess.allowRootAccess(testRootDisposable, sdkHome)

        val version = read(sdkHome)
        assertNull("Should return null for an empty .app file", version)
    }

    fun testElixirVersion_nonExistentSdkHome_returnsNull() {
        val version = read("/path/that/does/not/exist/at/all")
        assertNull("Should return null for a completely missing SDK home", version)
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun read(sdkHome: String): String? =
        ApplicationManager.getApplication()
            .executeOnPooledThread(Callable { ElixirVersionDetector.readElixirAppVersion(sdkHome) })
            .get()

    /**
     * Creates a temp SDK home with the given [version] in a standard `elixir.app` file.
     *
     * Layout: `<root>/lib/elixir/ebin/elixir.app`
     */
    private fun createSdkHomeWithApp(version: String): String =
        createSdkHomeWithCustomApp(
            """
            {application, elixir, [
              {description, "elixir"},
              {vsn, "$version"},
              {modules, []},
              {registered, []},
              {applications, [kernel, stdlib]}
            ]}.
            """.trimIndent(),
        )

    /**
     * Creates a temp SDK home with the given raw [appContent] as the `elixir.app` file content.
     *
     * Layout: `<root>/lib/elixir/ebin/elixir.app`
     */
    private fun createSdkHomeWithCustomApp(appContent: String): String {
        val root = Files.createTempDirectory("elixir-sdk-test-").toFile()
        root.deleteOnExit()
        val ebinDir = File(root, "lib/elixir/ebin")
        ebinDir.mkdirs()
        File(ebinDir, "elixir.app").writeText(appContent)
        return root.path
    }
}
