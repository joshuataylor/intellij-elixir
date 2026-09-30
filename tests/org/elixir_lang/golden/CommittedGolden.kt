package org.elixir_lang.golden

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.diff.Diff
import java.io.FileOutputStream

/**
 * Compares output with a committed golden file. On a difference, a unified diff is also appended to the CI step
 * summary that [STEP_SUMMARY_PROPERTY] names, so a reviewer sees what moved without opening the test log.
 */
object CommittedGolden {
    const val STEP_SUMMARY_PROPERTY = "elixir.test.stepSummary"

    private const val MAX_DIFF_LINES = 200

    fun assertMatches(path: String, actual: String, regenerate: String) {
        try {
            UsefulTestCase.assertSameLinesWithFile(path, actual, false) {
                "$path differs. Review each moved line, then regenerate with $regenerate"
            }
        } catch (error: FileComparisonFailedError) {
            appendToStepSummary(
                "`$path` differs",
                path,
                error.expectedStringPresentation,
                path,
                error.actualStringPresentation,
                regenerate
            )?.let(error::addSuppressed)

            throw error
        }
    }

    /**
     * Appends the diff from [expected], labelled [expectedPath], to [actual], labelled [path], to the step summary if
     * the run has one. Returns why it could not be written, for the caller to attach to its own failure.
     */
    fun appendToStepSummary(
        heading: String,
        expectedPath: String,
        expected: String,
        path: String,
        actual: String,
        regenerate: String,
    ): Throwable? =
        System.getProperty(STEP_SUMMARY_PROPERTY)?.takeIf(String::isNotBlank)?.let { stepSummary ->
            runCatching {
                val text = summary(heading, expectedPath, expected, path, actual, regenerate)
                // One write, as forks append concurrently.
                FileOutputStream(stepSummary, true).use { it.write(text.toByteArray()) }
            }.exceptionOrNull()
        }

    /** Hunks carry no context lines: each golden line identifies itself, so the cap is spent on moved lines. */
    private fun summary(
        heading: String,
        expectedPath: String,
        expected: String,
        path: String,
        actual: String,
        regenerate: String,
    ): String {
        val before = Diff.splitLines(expected)
        val after = Diff.splitLines(actual)
        val changes = Diff.buildChanges(before, after)?.toList().orEmpty()
        val removed = changes.sumOf { it.deleted }
        val added = changes.sumOf { it.inserted }
        val hunks = changes.flatMap { change ->
            listOf("@@ -${range(change.line0, change.deleted)} +${range(change.line1, change.inserted)} @@") +
                before.slice(change.line0 until change.line0 + change.deleted).map { "-$it" } +
                after.slice(change.line1 until change.line1 + change.inserted).map { "+$it" }
        }

        return buildString {
            append("### $heading\n\n")
            append("${removed + added} lines moved: $removed removed, $added added. ")
            append("Review each moved line, then regenerate with `$regenerate`.\n\n")
            append("```diff\n--- a/$expectedPath\n+++ b/$path\n")
            hunks.take(MAX_DIFF_LINES).forEach { append(it).append('\n') }
            append("```\n")
            if (hunks.size > MAX_DIFF_LINES) {
                append("\nShowing the first $MAX_DIFF_LINES of ${hunks.size} diff lines.\n")
            }
        }
    }

    /** A unified diff's `start,count`, where an empty range starts at the line before it. */
    private fun range(start: Int, count: Int): String = "${if (count == 0) start else start + 1},$count"
}
