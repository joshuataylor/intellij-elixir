package org.elixir_lang.mix

import org.elixir_lang.junit.LightTestCase
import org.elixir_lang.package_manager.DepsStatusResult

class PackageManagerTest : LightTestCase() {
    /** A project with no Elixir SDK yet is not a failed check: the missing SDK is reported where it can be set up. */
    fun testWithNoElixirSdkTheDepsAreNotChecked() {
        val mixExs = myFixture.addFileToProject("mix.exs", "defmodule App.MixProject do\nend\n").virtualFile

        assertEquals(DepsStatusResult.Unsupported, PackageManager().depsStatus(project, mixExs, null))
    }
}
