package org.elixir_lang.elixir_surface

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.UsefulTestCase
import org.elixir_lang.golden.CommittedGolden
import org.elixir_lang.junit.UnitTestCase
import java.io.File

class LegManifestTest : UnitTestCase() {
    private lateinit var directory: File
    private var savedSummary: String? = null

    override fun setUp() {
        super.setUp()
        directory = FileUtil.createTempDirectory("manifest", null)
        savedSummary = System.getProperty(CommittedGolden.STEP_SUMMARY_PROPERTY)
    }

    override fun tearDown() {
        try {
            savedSummary
                ?.let { System.setProperty(CommittedGolden.STEP_SUMMARY_PROPERTY, it) }
                ?: System.clearProperty(CommittedGolden.STEP_SUMMARY_PROPERTY)
            directory.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testRegenerateCommandNamesTheTestAndTheLeg() {
        val command = LegManifest.regenerateCommand(LegManifestTest::class, elixirVersion = "1.21.0", otpVersion = "29.1.1")

        assertTrue(command, command.startsWith("./gradlew test --tests org.elixir_lang.elixir_surface.LegManifestTest "))
        assertTrue(command, command.contains(" \"-PelixirVersion=1.21.0\" "))
        assertTrue(command, command.endsWith(" \"-PotpVersion=29.1.1\""))
        assertTrue(command, command.contains("-PoverwriteTestData=true"))
    }

    fun testMismatchIsReportedToTheStepSummary() {
        // Under -PoverwriteTestData=true the platform rewrites the manifest before comparing, so nothing can mismatch.
        if (UsefulTestCase.OVERWRITE_TESTDATA) return

        val manifest = File(directory, "manifest.txt").apply { writeText("a 1\nb 1\n") }
        val summary = File(directory, "summary.md")
        System.setProperty(CommittedGolden.STEP_SUMMARY_PROPERTY, summary.path)

        val error = try {
            LegManifest.assertMatchesFile(manifest.path, "a 1\nc 1\n", regenerate = "regenerate-command")
            null
        } catch (e: FileComparisonFailedError) {
            e
        }

        assertNotNull("a differing manifest must fail", error)
        assertTrue(error!!.message, error.message!!.contains("regenerate-command"))
        val lines = summary.readLines()
        assertTrue(summary.readText(), lines.containsAll(listOf("-b 1", "+c 1")))
    }

    fun testMissingManifestNamesTheLinesAndTheRegeneration() {
        val path = File(directory, "missing.txt").path

        val error = try {
            LegManifest.assertMatchesFile(path, "a 1\n", regenerate = "regenerate-command", writeMissing = false)
            null
        } catch (e: AssertionError) {
            e
        }

        assertNotNull("a missing manifest must fail", error)
        assertTrue(error!!.message, error.message!!.lines().contains("a 1"))
        assertTrue(error.message, error.message!!.contains("regenerate-command"))
        assertFalse("only regeneration writes a manifest", File(path).exists())
    }
}
