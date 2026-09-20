package org.elixir_lang.beam

import com.intellij.psi.PsiErrorElement
import org.elixir_lang.PlatformTestCase
import org.junit.Assert

/**
 * Round-trip validity coverage: decompile every `.beam` in the resolved Elixir/Erlang SDKs and
 * confirm the generated source parses as valid Elixir (the decompiled PSI has no [PsiErrorElement]).
 * This is the same check [DecompilerTest.assertParseable] applies to its golden fixtures, scaled to
 * the whole stdlib of the SDK under test - so decompiler gaps surface in CI on a version bump.
 *
 * Needs the platform PSI/parser (decompiler + Elixir ParserDefinition), so it extends
 * PlatformTestCase and sweeps within a single fixture (rather than the lightweight per-beam
 * [SdkBeamParseTest], which only exercises the platform-free chunk parser). The sweep itself is
 * shared with [SdkMirrorCoverageTest] and [SdkStubSignatureTest] through [SdkStdlibSweep].
 */
class SdkDecompileParseableTest : PlatformTestCase() {

    fun testElixirSdkDecompilesToParseableElixir() {
        sweep(System.getenv("ELIXIR_LANG_ELIXIR_PATH"), "Elixir")
    }

    fun testErlangSdkDecompilesToParseableElixir() {
        sweep(System.getenv("ERLANG_SDK_HOME"), "Erlang")
    }

    private fun sweep(root: String?, label: String) {
        Assert.assertNotNull("$label SDK env var not set", root)
        val result = SdkStdlibSweep.forSdk(project, testRootDisposable, root!!, label.lowercase())
        Assert.assertTrue("No .beam files found under $root/lib", result.beamCount > 0)

        val failures = result.parseFailures
        val passed = result.beamCount - failures.size
        println("[decompile-parseable] $label: $passed/${result.beamCount} beams decompiled to parseable Elixir")
        Assert.assertTrue(
            "${failures.size}/${result.beamCount} $label beams did NOT decompile to parseable Elixir:\n" +
                failures.take(100).joinToString("\n") +
                (if (failures.size > 100) "\n... and ${failures.size - 100} more" else ""),
            failures.isEmpty()
        )
    }
}
