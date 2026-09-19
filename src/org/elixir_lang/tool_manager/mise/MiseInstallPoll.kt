package org.elixir_lang.tool_manager.mise

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.CheckedDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val LOG = logger<MiseInstallPoll>()

/**
 * Asks mise, until it says so, whether a pinned version whose install has started is installed.
 *
 * mise fills a version's directory one file at a time and reports the version installed only once it has finished, so
 * no file event marks the end. One poll per project, so a re-scan for another reason during a long install neither
 * restarts it nor runs a second beside it; it asks about whatever the newest scan found pending instead.
 */
@Service(Service.Level.PROJECT)
internal class MiseInstallPoll(private val scope: CoroutineScope) {
    enum class Check { INSTALLED, NOT_YET, FAILED, STOP }

    enum class Outcome { INSTALLED, TIMED_OUT, FAILING, STOPPED }

    /** What the newest trigger waits for, and what to call once it is installed; followed until [owner] is disposed. */
    class Pending(
        val check: suspend () -> Check,
        val onInstalled: () -> Unit,
        val owner: CheckedDisposable,
        val installPaths: Set<String> = emptySet(),
    )

    /** One poll: its own schedule and whether it has finished with it, so a poll ending cannot touch the next. */
    private class Run(var remaining: Iterator<Duration>, val unfollowedFor: Duration) {
        var ending = false
        lateinit var job: Job

        /** Whether an install gave this poll a schedule it has not started on, and would lose if it ended now. */
        var restarted = false

        val accepting: Boolean get() = job.isActive && !ending
    }

    private var run: Run? = null
    private var current: Pending? = null
    /**
     * Install directories whose poll ran its whole schedule, keyed to their last-modified time then. Creation time
     * cannot tell a reinstall apart: NTFS gives a name created again soon after its deletion its old creation time.
     */
    private val exhausted = mutableMapOf<String, FileTime>()

    /** Called by every trigger install, so a running poll asks about the newest scan's pins, or stops without any. */
    fun follow(pending: Pending?) = synchronized(this) {
        current = pending
        if (pending == null) run?.job?.cancel()
    }

    /**
     * For an install directory already present: an install in progress, or one interrupted and left behind, which mise
     * reports not installed for good. Once a poll for it has run its whole schedule, it is refused until the
     * directory's last-modified time changes.
     */
    fun startOnce(installPath: String, delays: Sequence<Duration> = schedule()): Boolean {
        val modified = lastModified(installPath)
        return synchronized(this) {
            (modified == null || exhausted[installPath] != modified) && launch(delays.iterator(), UNFOLLOWED_FOR)
        }
    }

    /**
     * For an install that has just started. One starting while a poll runs gives that poll its schedule afresh, so the
     * install is asked about for as long as if it had started the poll; returns whether a new poll started.
     */
    fun start(delays: Sequence<Duration> = schedule(), unfollowedFor: Duration = UNFOLLOWED_FOR): Boolean =
        synchronized(this) {
            val running = run?.takeIf { it.accepting }
            if (running != null) {
                running.remaining = delays.iterator()
                running.restarted = true
                false
            } else {
                launch(delays.iterator(), unfollowedFor)
            }
        }

    private fun launch(delays: Iterator<Duration>, unfollowedFor: Duration): Boolean =
        synchronized(this) {
            if (run?.accepting == true || current == null) return false
            val poll = Run(delays, unfollowedFor)
            run = poll
            poll.job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                // Turning a tool manager on or off disposes the trigger at once and installs the next only after a
                // whole scan, so only a long spell without a trigger to follow means mise is off.
                var unfollowedSince: TimeMark? = null
                var leftBehind: Map<String, FileTime> = emptyMap()
                val outcome = pollUntilInstalled({
                    val pending = synchronized(this@MiseInstallPoll) { current }?.takeUnless { it.owner.isDisposed }
                    if (pending != null) {
                        unfollowedSince = null
                        pending.check().also {
                            leftBehind = pending.installPaths.mapNotNull { path -> lastModified(path)?.let { path to it } }.toMap()
                        }
                    } else {
                        val since = unfollowedSince ?: TimeSource.Monotonic.markNow().also { unfollowedSince = it }
                        if (since.elapsedNow() >= unfollowedFor) Check.STOP else Check.NOT_YET
                    }
                }, {
                    synchronized(this@MiseInstallPoll) {
                        if (poll.remaining.hasNext()) {
                            poll.restarted = false
                            poll.remaining.next()
                        } else {
                            // Only pins whose install had left a directory at the last check have had their go.
                            exhausted += leftBehind
                            poll.ending = true
                            null
                        }
                    }
                })
                synchronized(this@MiseInstallPoll) {
                    poll.ending = true
                    // An install that started during the check that failed the poll is still to be asked about.
                    if (poll.restarted && outcome == Outcome.FAILING) launch(poll.remaining, poll.unfollowedFor)
                }
                LOG.debug("mise install poll ended: $outcome")
                if (outcome == Outcome.INSTALLED) synchronized(this@MiseInstallPoll) { current }?.onInstalled?.invoke()
            }
            poll.job.start()
            true
        }

    private fun lastModified(installPath: String): FileTime? =
        try {
            Files.readAttributes(Path.of(installPath), BasicFileAttributes::class.java)
                .takeIf { it.isDirectory }
                ?.lastModifiedTime()
        } catch (_: IOException) {
            null
        } catch (_: InvalidPathException) {
            null
        }

    @TestOnly
    suspend fun awaitIdle() {
        synchronized(this) { run?.job }?.join()
    }

    companion object {
        private const val FAILURES_IN_A_ROW = 3
        private val UNFOLLOWED_FOR = 2.minutes

        suspend fun pollUntilInstalled(check: suspend () -> Check, nextDelay: () -> Duration?): Outcome {
            var failures = 0
            while (true) {
                delay(nextDelay() ?: return Outcome.TIMED_OUT)
                when (check()) {
                    Check.INSTALLED -> return Outcome.INSTALLED
                    Check.NOT_YET -> failures = 0
                    Check.FAILED -> if (++failures == FAILURES_IN_A_ROW) return Outcome.FAILING
                    Check.STOP -> return Outcome.STOPPED
                }
            }
        }

        /** Two hours in all, because mise may build Erlang from source. */
        fun schedule(): Sequence<Duration> =
            generateSequence { 5.seconds }.take(60) +
                generateSequence { 20.seconds }.take(75) +
                generateSequence { 60.seconds }.take(90)
    }
}
