package versioning

import java.io.IOException
import java.net.URI

/**
 * Resolves `useDynamicEapVersion` to the build of the newest active EAP or RC.
 * Same rule as `.github/scripts/ide-releases.js`, which resolves CI's `LATEST-EAP-SNAPSHOT`.
 */
object VersionFetcher {
    private val buildField = """"build"\s*:\s*"([0-9.]+)"""".toRegex()

    /**
     * The newer of the latest EAP and latest RC, only while it is above the latest release. The API keeps
     * answering "latest EAP/RC" with the previous cycle's build after it ships, so neither a fixed type
     * order nor the dates can tell whether a pre-release is active.
     */
    fun getLatestEapBuild(
        platformType: String = "IU",
        latest: (code: String, type: String) -> String? = ::fetchLatest,
    ): String {
        val (eap, rc, release) = listOf("eap", "rc", "release").map { latest(platformType, it) }
        val candidate = listOfNotNull(eap, rc).maxWithOrNull(::compareBuilds)
        if (candidate == null || (release != null && compareBuilds(candidate, release) <= 0)) {
            throw IllegalStateException(
                "No active EAP or RC for $platformType above release ${release ?: "<none>"}; " +
                    "set useDynamicEapVersion=false"
            )
        }
        println("Version: $candidate is the latest $platformType pre-release")
        return candidate
    }

    fun compareBuilds(a: String, b: String): Int {
        val x = a.split('.').map(String::toInt)
        val y = b.split('.').map(String::toInt)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun fetchLatest(code: String, type: String): String? {
        val uri = URI("https://data.services.jetbrains.com/products/releases?code=$code&type=$type&latest=true&fields=build")
        val json = try {
            uri.toURL().openStream().bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Could not read $uri", e)
        }
        return buildField.find(json)?.groupValues?.get(1)
    }
}
