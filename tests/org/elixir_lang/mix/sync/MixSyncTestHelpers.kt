package org.elixir_lang.mix.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.runBlocking
import org.elixir_lang.mix.library.CONSOLIDATED_LIBRARY_BASE_NAME
import java.util.concurrent.atomic.AtomicBoolean

/** The name a sync gives the consolidated library of the content root at [contentRootUrl]. */
internal fun consolidatedLibraryName(project: Project, contentRootUrl: String): String =
    scopedDepLibraryName(contentRootToken(project, contentRootUrl), CONSOLIDATED_LIBRARY_BASE_NAME)

/**
 * Shared test helpers for [MixDepsSyncService] light and heavy test classes.
 *
 * Centralised here so that both [MixDepsSyncServiceTest] (light, [com.intellij.testFramework.fixtures.BasePlatformTestCase])
 * and [MixDepsSyncServiceHeavyTestBase] (heavy, [com.intellij.testFramework.HeavyPlatformTestCase]) use identical
 * implementations without duplicating ~50 lines of infrastructure across two different base classes.
 */
internal object MixSyncTestHelpers {

    /**
     * Runs a suspending [block] on a pooled thread while the EDT (test thread) continues pumping
     * its event queue.
     *
     * Required for calling [buildWritePlan] / [applyWritePlan] / [MixDepsSyncService.drain] directly
     * from tests: all three functions dispatch to the EDT internally (via
     * [com.intellij.openapi.application.readAction] / [com.intellij.openapi.application.edtWriteAction])
     * and would deadlock if invoked via plain [runBlocking] from the EDT test thread.
     */
    fun <T> runSuspendOnPooledThread(timeoutMillis: Long = 10_000L, block: suspend () -> T): T {
        var result: T? = null
        var error: Throwable? = null
        val done = AtomicBoolean(false)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                result = runBlocking { block() }
            } catch (e: Throwable) {
                error = e
            }
            done.set(true)
        }
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!done.get()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("runSuspendOnPooledThread timed out after $timeoutMillis ms")
            }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            // Pumping without pause keeps the EDT permanently inside a prioritized activity, which
            // makes CoreProgressManager park the pooled thread for 1ms at every checkCanceled().
            if (!done.get()) {
                Thread.sleep(1)
            }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Calls [MixDepsSyncService.drain] directly on a pooled thread (bypassing the 250 ms debounce),
     * while pumping the IDE event queue from the EDT test thread so that
     * [com.intellij.openapi.application.edtWriteAction] blocks dispatched by the drain coroutine
     * can execute.
     */
    fun drainDirectly(service: MixDepsSyncService) = runSuspendOnPooledThread { service.drain() }

    /**
     * Removes every project-level library, whoever created it, unlike a sync, which removes only Mix-Kind ones, and
     * every module's entries naming a project-level library, so none is left pointing at a library that is gone.
     */
    @RequiresEdt
    fun removeAllLibraries(project: Project) {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        val toRemove = libraryTable.libraries.toList()
        var changed = toRemove.isNotEmpty()
        for (module in ModuleManager.getInstance(project).modules) {
            val hasEntry = ReadAction.computeBlocking<Boolean, Throwable> {
                ModuleRootManager.getInstance(module).orderEntries.any(::isProjectLibraryEntry)
            }
            if (!hasEntry) continue
            changed = true
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.orderEntries.filter(::isProjectLibraryEntry).forEach(model::removeOrderEntry)
            }
        }
        if (toRemove.isNotEmpty()) {
            WriteAction.run<Throwable> { toRemove.forEach { libraryTable.removeLibrary(it) } }
        }
        // The removal queues a rescan, which must neither start on a closed project nor overlap the next snapshot.
        if (changed) IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun isProjectLibraryEntry(entry: OrderEntry): Boolean =
        entry is LibraryOrderEntry && entry.libraryLevel == LibraryTablesRegistrar.PROJECT_LEVEL
}
