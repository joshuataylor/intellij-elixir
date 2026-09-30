import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats

// Runs the IntelliJ Plugin Verifier on the zip named by -Parchive against one IDE (-PideCode, -PideVersion).
// The remaining properties are shared-verify.yml's dispatch inputs, in the verifier's own spellings.
//
//   ./gradlew -p .github/verify verifyPlugin -Parchive="$PWD/build/distributions/<zip>" -PideCode=RM -PideVersion=2026.1.5
//
// A relative -Parchive resolves against .github/verify.
//
// Reports land in .github/verify/build/reports/pluginVerifier/<code>-<build>/.

plugins {
    alias(libs.plugins.intellij.platform)
}

fun property(name: String) = providers.gradleProperty(name)
fun required(name: String) = property(name).orElse(providers.provider { error("-P$name is required") })
fun list(name: String, separator: Regex) =
    property(name).orElse("").map { value -> value.split(separator).map(String::trim).filter(String::isNotEmpty) }

val ideCode = required("ideCode").get()
val ideVersion = required("ideVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The target IDE is also the platform: the verifier's configuration needs one, and a second IDE would
        // be a second download.
        create(ideCode, ideVersion)
        pluginVerifier(
            property("verifierVersion").filter(String::isNotBlank).orElse("LATEST").map { if (it == "LATEST") "latest" else it }
        )
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            create(ideCode, ideVersion)
        }
        failureLevel.set(list("failureLevels", Regex("\\s+")).map { levels -> levels.map(FailureLevel::valueOf) })
        externalPrefixes.set(list("externalPrefixes", Regex(":")))
        // PLAIN whatever is asked for: the task reads its verdict from verification-verdict.txt.
        verificationReportsFormats.set(
            list("reportFormats", Regex(",")).map { formats ->
                (listOf(VerificationReportsFormats.PLAIN) + formats.map { VerificationReportsFormats.valueOf(it.uppercase()) })
                    .distinct()
            }
        )
        freeArgs.set(list("mutePluginProblems", Regex(",")).map { rules ->
            if (rules.isEmpty()) emptyList() else listOf("-mute", rules.joinToString(","))
        })
    }
}

tasks.named<VerifyPluginTask>("verifyPlugin") {
    archiveFile.set(layout.file(required("archive").map { file(it) }))
    // The CLI runs on 21; without this the task asks for the target IDE's Java level. Each IDE is still
    // verified against its own bundled JBR (useBundledRuntime).
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
