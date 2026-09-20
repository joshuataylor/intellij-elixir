package org.elixir_lang.beam

import org.elixir_lang.PlatformTestCase
import org.junit.Assert

/**
 * Mirror coverage: every exported definition in every `.beam` the resolved SDKs ship must come out of
 * `ModuleImpl.setMirror` with a mirror.
 *
 * [SdkDecompileParseableTest] sweeps the same beams but stops one step short of this. Mirror mapping
 * runs *after* the parse, matching each exported stub against the decompiled source by name and
 * arity, so a definition the decompiled source never produced a matching clause for parses fine and
 * passes that sweep silently. It surfaces only at runtime, as a "No decompiled source function with
 * name" warning and a navigation target that goes nowhere.
 *
 * A mirror-less *unexported* definition is ordinary and is not asserted here. The sweep itself is
 * shared with [SdkDecompileParseableTest] and [SdkStubSignatureTest] through [SdkStdlibSweep].
 */
class SdkMirrorCoverageTest : PlatformTestCase() {
    fun testElixirSdkExportedDefinitionsAllGetMirrors() {
        sweep(System.getenv("ELIXIR_LANG_ELIXIR_PATH"), "Elixir")
    }

    fun testErlangSdkExportedDefinitionsAllGetMirrors() {
        sweep(System.getenv("ERLANG_SDK_HOME"), "Erlang")
    }

    private fun sweep(root: String?, label: String) {
        Assert.assertNotNull("$label SDK env var not set", root)
        val result = SdkStdlibSweep.forSdk(project, testRootDisposable, root!!, label.lowercase())
        Assert.assertTrue("No .beam files found under $root/lib", result.beamCount > 0)

        val misses = result.mirrorMisses
        val exported = result.mirrorExported
        println("[mirror-coverage] $label: ${exported - misses.size}/$exported exported definitions got a mirror")
        Assert.assertTrue(
            "${misses.size} exported $label definitions decompiled without a mirror, so navigating to them " +
                "lands nowhere:\n" +
                misses.take(100).joinToString("\n") +
                (if (misses.size > 100) "\n... and ${misses.size - 100} more" else ""),
            misses.isEmpty()
        )
    }
}
