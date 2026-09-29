package org.elixir_lang.elixir_surface

import org.elixir_lang.elixir_surface.ExpanderManifest.REGENERATE
import org.elixir_lang.junit.UnitTestCase
import java.io.File

/**
 * Fails on any leg whose Elixir SDK's expander no longer matches the manifest committed for its version, or that has
 * no manifest committed.
 */
class ExpanderClauseManifestTest : UnitTestCase() {
    fun testExpanderClauses() {
        assertManifest("expander-clauses.txt", ExpanderManifest.expanderClauses(ebin()))
    }

    fun testSpecialForms() {
        assertManifest("special-forms.txt", ExpanderManifest.specialForms(ebin()))
    }

    private fun assertManifest(name: String, body: String) {
        val elixirVersion = environment("ELIXIR_VERSION")
        val path = "testData/org/elixir_lang/elixir_surface/$elixirVersion/$name"

        ManifestDiff.assertMatchesFile(
            path,
            "# $REGENERATE\n$body",
            ExpanderManifest.regenerateCommand(elixirVersion, environment("ERLANG_VERSION"))
        )
    }

    private fun environment(name: String): String =
        System.getenv(name).takeUnless { it.isNullOrEmpty() } ?: throw AssertionError("$name not set for the test JVM")

    private fun ebin(): File = File(environment("ELIXIR_LANG_ELIXIR_PATH"), "lib/elixir/ebin")
}
