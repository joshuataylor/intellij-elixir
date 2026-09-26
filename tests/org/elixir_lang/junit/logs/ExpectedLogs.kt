package org.elixir_lang.junit.logs

import com.intellij.testFramework.LoggedErrorProcessor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs [block], which must make [logger] warn with a message matching [message], on any thread. Those warnings are
 * returned, for the test to validate, instead of logged; anything else logged still fails the test.
 */
fun expectWarnings(logger: Class<*>, message: Regex, block: () -> Unit): List<String> =
    expectWarnings("#${logger.name}", message, block)

/** For a logger named by a string rather than a class. */
fun expectWarnings(category: String, message: Regex, block: () -> Unit): List<String> =
    expect("a warning", category, message, block) { matches ->
        object : GuardedLoggedErrorProcessor() {
            override fun processWarn(category: String, message: String, t: Throwable?): Boolean =
                !matches(category, message) && super.processWarn(category, message, t)
        }
    }

/**
 * Runs [block], which must make [logger] log an error with a message matching [message], on any thread. Those errors
 * are returned, for the test to validate, instead of logged or rethrown; anything else logged still fails the test.
 */
fun expectErrors(logger: Class<*>, message: Regex, block: () -> Unit): List<String> =
    expectErrors("#${logger.name}", message, block)

/** For a logger named by a string rather than a class. */
fun expectErrors(category: String, message: Regex, block: () -> Unit): List<String> =
    expect("an error", category, message, block) { matches ->
        object : GuardedLoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?) =
                if (matches(category, message)) Action.NONE else super.processError(category, message, details, t)
        }
    }

private fun expect(
    what: String,
    category: String,
    message: Regex,
    block: () -> Unit,
    processor: (matches: (String, String) -> Boolean) -> LoggedErrorProcessor,
): List<String> {
    val matched = CopyOnWriteArrayList<String>()
    val expecting = processor { loggedCategory, loggedMessage ->
        (loggedCategory == category && message.containsMatchIn(loggedMessage)).also { if (it) matched += loggedMessage }
    }

    LoggedErrorProcessor.executeWith(expecting).use { block() }

    if (matched.isEmpty()) throw AssertionError("Expected $what from $category matching /$message/; none was logged")

    return matched
}
