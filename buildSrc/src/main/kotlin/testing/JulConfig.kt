package testing

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/** Points the test JVMs' `java.util.logging` at [properties], which Gradle's test worker reads in place of the JDK's. */
fun Test.configureJul(properties: File) {
    jvmArgumentProviders.add(JulConfigArgument(properties))
}

class JulConfigArgument(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val properties: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> = listOf("-Djava.util.logging.config.file=${properties.absolutePath}")
}
