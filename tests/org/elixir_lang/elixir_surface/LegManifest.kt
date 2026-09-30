package org.elixir_lang.elixir_surface

import com.intellij.testFramework.UsefulTestCase
import org.elixir_lang.golden.CommittedGolden
import java.io.File
import kotlin.reflect.KClass

/** Manifests committed per Elixir version under `testData/org/elixir_lang/elixir_surface/<version>/`. */
object LegManifest {
    fun ebin(): File = File(environment("ELIXIR_LANG_ELIXIR_PATH"), "lib/elixir/ebin")

    /** Fails unless [body] matches the manifest [name] committed for the leg's Elixir version, which [test] regenerates. */
    fun assertMatchesLeg(test: KClass<*>, name: String, body: String) {
        val elixirVersion = environment("ELIXIR_VERSION")

        assertMatchesFile(
            "testData/org/elixir_lang/elixir_surface/$elixirVersion/$name",
            "# ${regenerate(test)}\n$body",
            regenerateCommand(test, elixirVersion, environment("ERLANG_VERSION"))
        )
    }

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

    /**
     * [regenerate] alone resolves the local SDK, not the leg that failed. The version flags are quoted because
     * PowerShell splits an unquoted `-P` argument at its first dot.
     */
    fun regenerateCommand(test: KClass<*>, elixirVersion: String, otpVersion: String): String =
        "${regenerate(test)} \"-PelixirVersion=$elixirVersion\" \"-PotpVersion=$otpVersion\""

    private fun regenerate(test: KClass<*>): String =
        "./gradlew test --tests ${test.qualifiedName} -PoverwriteTestData=true"

    private fun environment(name: String): String =
        System.getenv(name).takeUnless { it.isNullOrEmpty() } ?: throw AssertionError("$name not set for the test JVM")
}
