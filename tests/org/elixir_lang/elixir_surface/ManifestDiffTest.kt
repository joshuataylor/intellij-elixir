package org.elixir_lang.elixir_surface

import com.intellij.openapi.util.io.FileUtil
import org.elixir_lang.junit.UnitTestCase
import java.io.File

class ManifestDiffTest : UnitTestCase() {
    fun testNamesRemovedAndAddedLines() {
        val description = ManifestDiff.describe(expected = "a 1\nb 1\nc 1\n", actual = "a 1\nc 1\nd 1\n")

        assertTrue(description, description.lines().contains("- b 1"))
        assertTrue(description, description.lines().contains("+ d 1"))
        assertFalse(description, description.lines().any { it.endsWith("a 1") || it.endsWith("c 1") })
    }

    fun testChangedCountIsARemovalAndAnAddition() {
        val description = ManifestDiff.describe(expected = "a 1\n", actual = "a 2\n")

        assertTrue(description, description.lines().containsAll(listOf("- a 1", "+ a 2")))
    }

    fun testNamesADuplicatedLine() {
        assertTrue(ManifestDiff.describe("a 1\n", "a 1\na 1\n").lines().contains("+ a 1"))
    }

    fun testCarriageReturnsDoNotCount() {
        assertEquals(ManifestDiff.describe("a 1\n", "a 1\n"), ManifestDiff.describe("a 1\r\n", "a 1\n"))
    }

    fun testMissingManifestNamesTheLinesAndTheRegeneration() {
        val directory = FileUtil.createTempDirectory("manifest", null)
        val path = File(directory, "missing.txt").path

        val error = try {
            ManifestDiff.assertMatchesFile(path, "a 1\n", regenerate = "regenerate-command", overwrite = false)
            null
        } catch (e: AssertionError) {
            e
        }

        assertNotNull("a missing manifest must fail", error)
        assertTrue(error!!.message, error.message!!.lines().contains("+ a 1"))
        assertTrue(error.message, error.message!!.contains("regenerate-command"))
        assertFalse("only regeneration writes a manifest", File(path).exists())
    }
}
