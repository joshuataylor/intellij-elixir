package org.elixir_lang.elixir_surface

import org.elixir_lang.elixir_surface.LegManifest.ebin
import org.elixir_lang.junit.UnitTestCase

/**
 * Fails on any leg whose Elixir SDK documents a `Kernel` or `Kernel.SpecialForms` macro the list committed for its
 * version does not have, or no longer documents one it has, or that has no list committed.
 */
class MacroSurfaceManifestTest : UnitTestCase() {
    fun testMacros() {
        LegManifest.assertMatchesLeg(MacroSurfaceManifestTest::class, "macros.txt", MacroSurface.macros(ebin()))
    }
}
