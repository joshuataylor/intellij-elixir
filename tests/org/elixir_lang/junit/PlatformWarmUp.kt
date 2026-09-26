package org.elixir_lang.junit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

/**
 * Starts the application and opens a light project with an editor before the first platform test in the JVM, which
 * otherwise carries those seconds and starves the EDT while they run. Nothing is started for a JVM whose tests all run
 * on a mock application.
 */
class PlatformWarmUp : TestExecutionListener {
    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (warmed || !testIdentifier.isContainer) return
        val testClass = (testIdentifier.source.orElse(null) as? ClassSource)?.getJavaClass() ?: return
        if (PLATFORM_BASES.none { it.isAssignableFrom(testClass) }) return
        warmed = true

        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val fixture = factory.createCodeInsightFixture(factory.createLightFixtureBuilder(null, "warm-up").fixture)
        // Logged rather than thrown: the launcher only warns about a listener that throws, which no report shows.
        try {
            EdtTestUtil.runInEdtAndWait<Throwable> { warmUp(fixture) }
        } catch (e: Throwable) {
            LOG.error("Warming up the IDE failed, so the first platform test starts it", e)
        }
    }

    private fun warmUp(fixture: CodeInsightTestFixture) {
        runAll(
            {
                fixture.setUp()
                fixture.configureByText("warm_up.ex", "defmodule WarmUp do\nend\n")
                fixture.doHighlighting()
            },
            fixture::tearDown,
            // The light project is shared by later tests, and lives where the first one to open it put it: this one is
            // outside any test, which on Linux is bare `/tmp`.
            LightPlatformTestCase::closeAndDeleteProject,
        )
    }

    private companion object {
        // Created on use: a logger created before the test environment is set up is the platform's default one, whose
        // `error` throws, and this class is loaded before that.
        val LOG: Logger
            get() = logger<PlatformWarmUp>()

        val PLATFORM_BASES = listOf(
            BasePlatformTestCase::class.java,
            LightPlatformTestCase::class.java,
            HeavyPlatformTestCase::class.java,
        )

        @Volatile
        var warmed = false
    }
}
