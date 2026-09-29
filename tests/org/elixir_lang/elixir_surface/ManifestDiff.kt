package org.elixir_lang.elixir_surface

import com.intellij.testFramework.UsefulTestCase
import java.io.File

object ManifestDiff {
    /** [UsefulTestCase.assertSameLinesWithFile], failing with [describe] and [regenerate] whether or not the file exists. */
    fun assertMatchesFile(path: String, actual: String, regenerate: String, overwrite: Boolean = UsefulTestCase.OVERWRITE_TESTDATA) {
        val file = File(path)
        val expected = if (file.isFile) file.readText() else ""
        val message = { describe(expected, actual) + "\nReview, then regenerate: $regenerate" }

        if (!file.isFile && !overwrite) throw AssertionError("No committed manifest at $path. " + message())

        UsefulTestCase.assertSameLinesWithFile(path, actual, false, message)
    }

    /** Each copy of a line [expected] has more of than [actual], as `- line`, then the reverse, as `+ line`. */
    fun describe(expected: String, actual: String): String {
        val expectedCounts = counts(expected)
        val actualCounts = counts(actual)

        return (listOf("The SDK's expander differs from the committed manifest (- committed only, + SDK only):") +
            surplus(expectedCounts, actualCounts).map { "- $it" } +
            surplus(actualCounts, expectedCounts).map { "+ $it" }).joinToString("\n")
    }

    private fun counts(text: String): Map<String, Int> =
        text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().toSortedMap()

    private fun surplus(these: Map<String, Int>, those: Map<String, Int>): List<String> =
        these.flatMap { (line, count) -> List((count - (those[line] ?: 0)).coerceAtLeast(0)) { line } }
}
