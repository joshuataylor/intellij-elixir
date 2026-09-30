// Verifies a prebuilt plugin zip against one IDE, for .github/workflows/shared-verify.yml. Standalone so a
// verification leg configures nothing of the plugin build: see build.gradle.kts.
rootProject.name = "verify"

dependencyResolutionManagement {
    versionCatalogs {
        // The plugin build's catalog, so both builds use the same IntelliJ Platform Gradle Plugin.
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
