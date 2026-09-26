package org.elixir_lang.junit.logs

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** [UnexpectedLogs.failOnUnexpectedLogs] for a JUnit 4 test class, around its `@Before` and `@After` methods too. */
class UnexpectedLogsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                UnexpectedLogs.failOnUnexpectedLogs { base.evaluate() }
            }
        }
}
