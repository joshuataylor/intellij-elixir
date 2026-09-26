package org.elixir_lang.junit.logs

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Fails a Jupiter test for the unexpected logs [UnexpectedLogs] recorded while it ran. Auto-detected, so no test declares it. */
class UnexpectedLogsExtension : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
        val logged = UnexpectedLogs.takeCurrent()

        // Jupiter attaches it to the test's own failure, if there is one.
        if (logged.isNotEmpty()) {
            throw UnexpectedLogsError(logged)
        }
    }
}
