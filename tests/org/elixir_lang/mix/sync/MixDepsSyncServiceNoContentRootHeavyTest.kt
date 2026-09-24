package org.elixir_lang.mix.sync

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.common.runAll
import org.elixir_lang.junit.HeavyTestCase
import org.elixir_lang.mix.library.Kind as MixLibraryKind

/** A project with no content root in the VFS, which a light fixture cannot have without changing its shared module. */
class MixDepsSyncServiceNoContentRootHeavyTest : HeavyTestCase() {
    override fun tearDown() {
        runAll(
            { MixSyncTestHelpers.removeAllLibraries(project) },
            { super.tearDown() },
        )
    }

    /** A full sync has nothing to plan here, yet must still sweep what asked for it. */
    fun testOneFullSyncWithNothingToPlanStillSweeps() {
        WriteAction.runAndWait<Throwable> {
            val model = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            model.createLibrary("phoenix", MixLibraryKind)
            model.commit()
        }
        val needsResync = { MixSyncTestHelpers.runSuspendOnPooledThread { MixLibraryReconciler.needsResync(project) } }

        assertTrue("an unscoped name must trigger a full sync", needsResync())
        val service = project.service<MixDepsSyncService>()
        service.clearPendingForTesting()
        service.enqueue(SyncRequest.All)
        MixSyncTestHelpers.drainDirectly(service)
        assertFalse("one full sync must clear an unscoped name with nothing to plan", needsResync())
    }
}
