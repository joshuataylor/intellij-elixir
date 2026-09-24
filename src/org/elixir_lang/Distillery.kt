package org.elixir_lang

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.FileNotFoundException
import org.elixir_lang.sdk.wsl.wslCompat

object Distillery {
    /**
     * Keep in-sync with `org.elixir_lang.jps.Builder.erlCommandLine`
     */
    fun commandLine(pty: Boolean, environment: Map<String, String>, workingDirectory: String?, exePath: String?):
            GeneralCommandLine {
        val commandLine = org.elixir_lang.run.baseCommandLine(pty, environment, workingDirectory)
        commandLine.exePath = exePath ?: throw FileNotFoundException("Distillery release CLI path is not set")
        wslCompat.requireReachable(exePath, "Distillery release CLI")

        return commandLine
    }
}
