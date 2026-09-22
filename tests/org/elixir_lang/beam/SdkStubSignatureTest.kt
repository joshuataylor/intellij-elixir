package org.elixir_lang.beam

import org.elixir_lang.PlatformTestCase
import org.junit.Assert

/**
 * The stub's parameters must be the ones the decompiled mirror shows, and the mirror itself must be the first
 * matching clause, for every compared definition - `ModuleImpl.setMirror` and the source path's parameter
 * hints agree on the first clause of each name and arity. A module whose own clause map failed to build is
 * skipped entirely, so `stubCompared` counts comparisons actually made, not every definition with a mirror.
 *
 * Also prints how many exported definitions with parameters have names not flagged as generated; the counts are
 * in this class's `system-out` in the JUnit XML. The sweep itself is shared with [SdkDecompileParseableTest] and
 * [SdkMirrorCoverageTest] through [SdkStdlibSweep].
 */
class SdkStubSignatureTest : PlatformTestCase() {
    fun testElixirSdkStubParametersMatchMirror() {
        sweep(System.getenv("ELIXIR_LANG_ELIXIR_PATH"), "Elixir")
    }

    fun testErlangSdkStubParametersMatchMirror() {
        sweep(System.getenv("ERLANG_SDK_HOME"), "Erlang")
    }

    private fun sweep(root: String?, label: String) {
        Assert.assertNotNull("$label SDK env var not set", root)
        val result = SdkStdlibSweep.forSdk(project, testRootDisposable, root!!, label.lowercase())
        Assert.assertTrue("No .beam files found under $root/lib", result.beamCount > 0)

        val mismatches = result.stubMismatches
        val exportedWithParameters = result.stubExportedWithParameters
        val real = exportedWithParameters - result.stubExportedGenerated
        println(
            "[stub-signature] $label: compared ${result.stubCompared} definitions with a mirror; " +
                "$real/$exportedWithParameters exported definitions with parameters have names " +
                "not flagged as generated " +
                "(${"%.1f".format(100.0 * real / exportedWithParameters.coerceAtLeast(1))}%); " +
                "${result.stubExportedGenerated} generated across ${result.stubBeamsWithExportedGenerated} beams"
        )
        Assert.assertTrue("$label: no definition had a mirror to compare against", result.stubCompared > 0)
        Assert.assertTrue(
            "${mismatches.size} $label stubs disagree with their decompiled mirror:\n" +
                mismatches.take(100).joinToString("\n") +
                (if (mismatches.size > 100) "\n... and ${mismatches.size - 100} more" else ""),
            mismatches.isEmpty()
        )
    }
}
