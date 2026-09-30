package org.elixir_lang.elixir_surface

import com.intellij.testFramework.UsefulTestCase
import org.elixir_lang.golden.CommittedGolden
import java.io.File

object ManifestDiff {
    /**
     * [CommittedGolden.assertMatches], except that a missing manifest fails without being written: the platform
     * would otherwise write whatever the SDK produced as the expected manifest.
     */
    fun assertMatchesFile(path: String, actual: String, regenerate: String, writeMissing: Boolean = UsefulTestCase.OVERWRITE_TESTDATA) {
        if (!File(path).isFile && !writeMissing) {
            throw AssertionError("No committed manifest at $path. Review the SDK's manifest, then regenerate: $regenerate\n$actual")
        }

        CommittedGolden.assertMatches(path, actual, regenerate)
    }
}
