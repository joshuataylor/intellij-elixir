package org.elixir_lang.tool_manager.mise

import com.intellij.openapi.components.service
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.tool_manager.mise.MiseInstallPoll.Check
import org.elixir_lang.tool_manager.mise.MiseInstallPoll.Outcome
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MiseInstallPollTest : PlatformTestCase() {
    private fun immediately(count: Int): Sequence<Duration> = generateSequence { Duration.ZERO }.take(count)

    private fun poll(vararg checks: Check, delays: Sequence<Duration> = immediately(10)): Pair<Outcome, Int> {
        val calls = AtomicInteger()
        val outcome = runBlocking {
            val remaining = delays.iterator()
            MiseInstallPoll.pollUntilInstalled(
                { checks.getOrElse(calls.getAndIncrement()) { Check.NOT_YET } },
                { if (remaining.hasNext()) remaining.next() else null },
            )
        }
        return outcome to calls.get()
    }

    fun testAnInstallThatFinishesStopsThePoll() {
        assertEquals(Outcome.INSTALLED to 3, poll(Check.NOT_YET, Check.NOT_YET, Check.INSTALLED))
    }

    fun testAnInstallThatNeverFinishesTimesOut() {
        assertEquals(Outcome.TIMED_OUT to 4, poll(delays = immediately(4)))
    }

    fun testThreeFailuresInARowStopThePoll() {
        assertEquals(Outcome.FAILING to 4, poll(Check.NOT_YET, Check.FAILED, Check.FAILED, Check.FAILED))
    }

    fun testAnAnswerBetweenFailuresStartsTheCountAgain() {
        assertEquals(
            Outcome.INSTALLED to 6,
            poll(Check.FAILED, Check.FAILED, Check.NOT_YET, Check.FAILED, Check.FAILED, Check.INSTALLED),
        )
    }

    fun testTheScheduleSlowsDownAndGivesUpAfterTwoHours() {
        val expected = buildList {
            repeat(60) { add(5.seconds) }
            repeat(75) { add(20.seconds) }
            repeat(90) { add(60.seconds) }
        }
        val delays = MiseInstallPoll.schedule().toList()

        assertEquals(expected, delays)
        assertEquals(120.minutes, delays.fold(Duration.ZERO, Duration::plus))
    }

    private val poll get() = project.service<MiseInstallPoll>()

    private val installs by lazy { FileUtil.createTempDirectory("mise-installs", null, true) }

    /** An install directory present on disk, as one an install in progress, or interrupted, leaves. */
    private fun directory(name: String): String =
        FileUtil.toSystemIndependentName(File(installs, name).apply { mkdirs() }.path)

    private fun noDirectory(name: String): String = FileUtil.toSystemIndependentName(File(installs, name).path)

    override fun tearDown() {
        try {
            // The light project, and so this service, outlives the test.
            poll.follow(null)
            runBlocking { poll.awaitIdle() }
        } finally {
            super.tearDown()
        }
    }

    private fun pending(
        owner: CheckedDisposable = Disposer.newCheckedDisposable(testRootDisposable),
        installPaths: Set<String> = emptySet(),
        check: suspend () -> Check,
    ) = CompletableDeferred<Unit>().let { installed ->
        MiseInstallPoll.Pending(check, { installed.complete(Unit) }, owner, installPaths) to installed
    }

    fun testOnlyOnePollRunsAtATime() {
        val gate = CompletableDeferred<Check>()
        val (first, installed) = pending { gate.await() }
        poll.follow(first)

        assertTrue(poll.start(immediately(1)))
        assertFalse("a poll is already running", poll.start(immediately(1)))

        gate.complete(Check.INSTALLED)
        runBlocking { installed.await() }
        runBlocking { poll.awaitIdle() }
        assertTrue("the first poll has finished", poll.start(immediately(1)))
        runBlocking { poll.awaitIdle() }
    }

    fun testARunningPollAsksWhatTheLatestTriggerWaitsFor() {
        val gate = CompletableDeferred<Check>()
        val (first, firstInstalled) = pending { gate.await() }
        val (latest, latestInstalled) = pending { Check.INSTALLED }
        poll.follow(first)
        poll.start(immediately(2))

        poll.follow(latest)
        gate.complete(Check.NOT_YET)
        runBlocking { poll.awaitIdle() }

        assertTrue("a pin that became pending after the poll started is waited for", latestInstalled.isCompleted)
        assertFalse(firstInstalled.isCompleted)
    }

    fun testAPollWhoseTriggerIsDisposedStops() {
        val owner = Disposer.newCheckedDisposable(testRootDisposable)
        val gate = CompletableDeferred<Check>()
        val checks = AtomicInteger()
        val checking = CompletableDeferred<Unit>()
        val (only, installed) = pending(owner) { checks.incrementAndGet(); checking.complete(Unit); gate.await() }
        poll.follow(only)
        poll.start(immediately(5), unfollowedFor = Duration.ZERO)
        runBlocking { checking.await() }

        Disposer.dispose(owner)
        gate.complete(Check.NOT_YET)
        runBlocking { poll.awaitIdle() }

        assertEquals("turning the integration off disposes the trigger, and nothing follows it", 1, checks.get())
        assertFalse(installed.isCompleted)
    }

    fun testAnInstallStartingDuringAPollStartsItsScheduleAgain() {
        val checks = AtomicInteger()
        val secondCheck = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val (only, _) = pending {
            if (checks.incrementAndGet() == 2) {
                secondCheck.complete(Unit)
                gate.await()
            }
            Check.NOT_YET
        }
        poll.follow(only)
        poll.start(immediately(2))
        runBlocking { secondCheck.await() }

        // The last check of the first schedule is running when another install's directory appears.
        assertFalse(poll.start(immediately(3)))
        gate.complete(Unit)
        runBlocking { poll.awaitIdle() }

        assertEquals("an install that starts late gets a whole schedule of its own", 5, checks.get())
    }

    fun testADirectorySeenAgainByAScanDoesNotStartTheScheduleAgain() {
        val checks = AtomicInteger()
        val secondCheck = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val (only, _) = pending {
            if (checks.incrementAndGet() == 2) {
                secondCheck.complete(Unit)
                gate.await()
            }
            Check.NOT_YET
        }
        poll.follow(only)
        poll.start(immediately(2))
        runBlocking { secondCheck.await() }

        // Every scan finds a directory an interrupted install left behind, so it would never run out.
        assertFalse(poll.startOnce(directory("elixir/1.16.3"), immediately(3)))
        gate.complete(Unit)
        runBlocking { poll.awaitIdle() }

        assertEquals(2, checks.get())
    }

    fun testAPollOutlivesTheGapBetweenOneTriggerAndTheNext() {
        val owner = Disposer.newCheckedDisposable(testRootDisposable)
        val gate = CompletableDeferred<Check>()
        val checking = CompletableDeferred<Unit>()
        val (first, _) = pending(owner) { checking.complete(Unit); gate.await() }
        val (next, nextInstalled) = pending { Check.INSTALLED }
        poll.follow(first)
        // The second check finds no trigger to follow; the third comes after the next trigger has been installed.
        poll.start(sequenceOf(Duration.ZERO, Duration.ZERO, 2.seconds), unfollowedFor = 1.seconds)
        runBlocking { checking.await() }

        Disposer.dispose(owner)
        gate.complete(Check.NOT_YET)
        Thread.sleep(200)
        poll.follow(next)
        runBlocking { poll.awaitIdle() }

        assertTrue("a re-scan disposes one trigger before installing the next", nextInstalled.isCompleted)
    }

    fun testATriggerWithNothingPendingStopsThePoll() {
        val checking = CompletableDeferred<Unit>()
        val (only, installed) = pending { checking.complete(Unit); awaitCancellation() }
        poll.follow(only)
        poll.start(immediately(5))
        runBlocking { checking.await() }

        poll.follow(null)
        runBlocking { poll.awaitIdle() }

        assertFalse(installed.isCompleted)
    }

    fun testAnInstallDirectorySeenWhileAnotherPollRunsCanStartOneLater() {
        val gate = CompletableDeferred<Check>()
        val (only, _) = pending { gate.await() }
        poll.follow(only)
        poll.start(immediately(1))

        assertFalse(poll.startOnce(directory("erlang/27.3"), immediately(1)))
        gate.complete(Check.INSTALLED)
        runBlocking { poll.awaitIdle() }

        assertTrue(
            "two installs running at once: the second is only polled once the first has finished",
            poll.startOnce(directory("erlang/27.3"), immediately(1)),
        )
        runBlocking { poll.awaitIdle() }
    }

    fun testAPollStoppedBeforeItsEndLeavesItsDirectoryToAnother() {
        val checking = CompletableDeferred<Unit>()
        val (stopped, _) = pending { checking.complete(Unit); awaitCancellation() }
        poll.follow(stopped)
        assertTrue(poll.startOnce(directory("elixir/1.19.5"), immediately(5)))
        runBlocking { checking.await() }
        // A scan whose `mise ls` failed has nothing pending.
        poll.follow(null)
        runBlocking { poll.awaitIdle() }

        val (failing, _) = pending { Check.FAILED }
        poll.follow(failing)
        assertTrue("a cancelled poll has not had its go", poll.startOnce(directory("elixir/1.19.5"), immediately(5)))
        runBlocking { poll.awaitIdle() }

        val (next, _) = pending { Check.NOT_YET }
        poll.follow(next)
        assertTrue("nor has one mise kept failing", poll.startOnce(directory("elixir/1.19.5"), immediately(1)))
        runBlocking { poll.awaitIdle() }
    }

    fun testAPollThatRanItsCourseUsesUpTheDirectoriesItAskedAbout() {
        val gate = CompletableDeferred<Check>()
        val (first, _) = pending(installPaths = setOf(directory("elixir/1.18.4"))) { gate.await() }
        val (latest, _) = pending(installPaths = setOf(directory("elixir/1.18.5"), directory("erlang/27.2"))) { Check.NOT_YET }
        poll.follow(first)
        assertTrue(poll.startOnce(directory("elixir/1.18.4"), immediately(2)))

        // The pin changed while the poll ran, so it spent its schedule on the newer pins.
        poll.follow(latest)
        gate.complete(Check.NOT_YET)
        runBlocking { poll.awaitIdle() }

        assertFalse(poll.startOnce(directory("elixir/1.18.5"), immediately(1)))
        assertFalse("every pin the poll asked about had its go", poll.startOnce(directory("erlang/27.2"), immediately(1)))
        assertTrue("the directory that started it was barely asked about", poll.startOnce(directory("elixir/1.18.4"), immediately(1)))
        runBlocking { poll.awaitIdle() }
    }

    fun testAPinWithNoDirectoryIsNotUsedUp() {
        val leftBehind = directory("elixir/1.17.3")
        val notStarted = noDirectory("erlang/26.2")
        val (both, _) = pending(installPaths = setOf(leftBehind, notStarted)) { Check.NOT_YET }
        poll.follow(both)

        assertTrue(poll.startOnce(leftBehind, immediately(1)))
        runBlocking { poll.awaitIdle() }

        assertFalse(poll.startOnce(leftBehind, immediately(1)))
        File(notStarted).mkdirs()
        assertTrue(
            "a pin whose install had not started has not had its go; its install may start and be interrupted later",
            poll.startOnce(notStarted, immediately(1)),
        )
        runBlocking { poll.awaitIdle() }
    }

    fun testADirectoryCreatedAgainCanBePolledAgain() {
        val leftBehind = directory("elixir/1.15.8")
        val (only, _) = pending(installPaths = setOf(leftBehind)) { Check.NOT_YET }
        poll.follow(only)
        poll.startOnce(leftBehind, immediately(1))
        runBlocking { poll.awaitIdle() }
        assertFalse("precondition: its poll ran its whole schedule", poll.startOnce(leftBehind, immediately(1)))

        // Another install deletes it and starts again, whether or not any trigger saw it happen.
        File(leftBehind).deleteRecursively()
        Thread.sleep(50)
        File(leftBehind, "bin").mkdirs()

        assertTrue(poll.startOnce(leftBehind, immediately(1)))
        runBlocking { poll.awaitIdle() }
    }

    fun testAStartDuringTheCheckThatEndsAPollIsNotLost() {
        val checks = AtomicInteger()
        val thirdCheck = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val (only, installed) = pending {
            when (checks.incrementAndGet()) {
                1, 2 -> Check.FAILED
                3 -> {
                    thirdCheck.complete(Unit)
                    gate.await()
                    Check.FAILED
                }
                else -> Check.INSTALLED
            }
        }
        poll.follow(only)
        poll.start(immediately(5))
        runBlocking { thirdCheck.await() }

        // An install starts while the check that ends the poll, as failing, is running.
        assertFalse(poll.start(immediately(2)))
        gate.complete(Unit)

        runBlocking { withTimeout(10.seconds) { installed.await() } }
    }

    fun testAnInstallDirectoryAlreadyThereStartsOnePollASession() {
        val (only, _) = pending(installPaths = setOf(directory("elixir/1.20.5"))) { Check.NOT_YET }
        poll.follow(only)

        assertTrue(poll.startOnce(directory("elixir/1.20.5"), immediately(1)))
        runBlocking { poll.awaitIdle() }
        assertFalse(
            "a directory an interrupted install left behind would otherwise poll again after every scan",
            poll.startOnce(directory("elixir/1.20.5"), immediately(1)),
        )
        assertTrue(poll.startOnce(directory("erlang/28.1"), immediately(1)))
        runBlocking { poll.awaitIdle() }
    }
}
