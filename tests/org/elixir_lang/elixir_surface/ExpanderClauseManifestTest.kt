package org.elixir_lang.elixir_surface

import org.elixir_lang.elixir_surface.LegManifest.ebin
import org.elixir_lang.junit.UnitTestCase

/**
 * Fails on any leg whose Elixir SDK's expander no longer matches the manifest committed for its version, or that has
 * no manifest committed.
 */
class ExpanderClauseManifestTest : UnitTestCase() {
    fun testExpanderClauses() {
        LegManifest.assertMatchesLeg(ExpanderClauseManifestTest::class, "expander-clauses.txt", ExpanderManifest.expanderClauses(ebin()))
    }

    fun testSpecialForms() {
        LegManifest.assertMatchesLeg(ExpanderClauseManifestTest::class, "special-forms.txt", ExpanderManifest.specialForms(ebin()))
    }
}
