package org.elixir_lang.mix.sync

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.PsiTestUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.mix.library.CONSOLIDATED_LIBRARY_BASE_NAME
import org.elixir_lang.mix.library.Kind as MixLibraryKind

/**
 * Tests for the startup escalation check.
 *
 * The steady-state answer is the one that matters for cost: a project whose libraries are fine must
 * not trigger a full sync, because that would reintroduce the deps/`_build` scan on every open.
 */
class MixLibraryReconcilerTest : PlatformTestCase() {

    /**
     * Each case asserts on the whole library table, so it must start empty: the restore after each light test removes
     * only what that test added, not libraries an earlier class in the fork left.
     */
    override fun resetSharedState() {
        MixSyncTestHelpers.removeAllLibraries(project)
    }

    private fun reconcilerNeedsResync(): Boolean =
        MixSyncTestHelpers.runSuspendOnPooledThread { MixLibraryReconciler.needsResync(project) }

    private fun createMixLibrary(name: String, classRootUrl: String?) {
        WriteAction.run<Throwable> {
            val model = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            val library = model.createLibrary(name, MixLibraryKind)
            classRootUrl?.let {
                library.modifiableModel.let { lm ->
                    lm.addRoot(it, OrderRootType.CLASSES)
                    lm.commit()
                }
            }
            model.commit()
        }
    }

    fun testNoLibrariesNeedsNoResync() {
        assertFalse("An empty library table must not trigger a full sync", reconcilerNeedsResync())
    }

    /** The common case, and the one the cost argument rests on. */
    fun testCurrentRootScopedLibraryWithLiveRootsNeedsNoResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_ok")
        val ebin = myFixture.tempDirFixture.findOrCreateDir("reconcile_ok/_build/dev/lib/phoenix/ebin")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary(scopedDepLibraryName(contentRootToken(project, root.url), "phoenix"), ebin.url)

        assertFalse("A library scoped to a current root with live roots must not resync", reconcilerNeedsResync())
    }

    /** A placeholder for a declared-but-unfetched dep is deliberate and has no roots to dangle. */
    fun testPlaceholderWithNoRootsNeedsNoResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_placeholder")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary(scopedDepLibraryName(contentRootToken(project, root.url), "unfetched"), null)

        assertFalse("An empty placeholder must not trigger a full sync", reconcilerNeedsResync())
    }

    /** deps/ removed while the IDE was closed: the library survives, its roots do not. */
    fun testDanglingRootNeedsResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_dangling")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary(
            scopedDepLibraryName(contentRootToken(project, root.url), "phoenix"),
            "${root.url}/_build/dev/lib/phoenix/ebin",
        )

        assertTrue("A library root the VFS cannot resolve must trigger a full sync", reconcilerNeedsResync())
    }

    /** Any drain's sweep removes it, but only a sync of every root recreates each root's scoped replacement. */
    fun testUnscopedDepNamesNeedResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_unscoped")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary("phoenix", null)

        assertTrue("A name without a scope token must trigger a full sync", reconcilerNeedsResync())
    }

    /** A consolidated library named before scoping migrates at open, as its replacement needs a sync of its root. */
    fun testAnUnscopedConsolidatedNameNeedsResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_consolidated")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary("reconcile_consolidated (consolidated)", null)

        assertTrue("A consolidated library named before scoping must trigger a full sync", reconcilerNeedsResync())
    }

    /** An external `path:` dep is scoped to a directory that is never a content root, and no sync removes it. */
    fun testAnExternalDepsRelativeTokenNeedsNoResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_external")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary(scopedDepLibraryName("../shared_parent", "shared"), null)

        assertFalse("An external dep's library must not force a full sync on every open", reconcilerNeedsResync())
    }

    /** A consolidated library whose root left is removed by a sync, so one is worth running. */
    fun testAConsolidatedLibraryOfARootThatLeftNeedsResync() {
        val root = myFixture.tempDirFixture.findOrCreateDir("reconcile_consolidated_left")
        PsiTestUtil.addContentRoot(myFixture.module, root)

        createMixLibrary(scopedDepLibraryName("apps/gone", CONSOLIDATED_LIBRARY_BASE_NAME), null)

        assertTrue("A consolidated library of a root that left must trigger a full sync", reconcilerNeedsResync())
    }

    /** A dep on another drive has no relative token, so the plugin still writes its absolute one. */
    fun testAnAbsoluteTokenWithNoRelativeFormNeedsNoResync() {
        assertFalse(
            "A library the current scheme would name the same must not force a full sync on every open",
            isSweptMixLibraryName("foo [file://D:/libs]", setOf("."), "C:/project"),
        )
    }

    /** Only a current root's old-scheme name is renamed by a sync; any other stays, so a sync would buy nothing. */
    fun testAnOlderSchemeNameOfARootThatLeftNeedsNoResync() {
        assertFalse(
            "An old-scheme name no sync renames must not force a full sync on every open",
            isSweptMixLibraryName("phoenix [file:///elsewhere/gone]", setOf("."), "/project"),
        )
    }

    /** The whole-project upgrade case: treating the old form as current would call this project healthy. */
    fun testAnOlderSchemeNameOfACurrentRootNeedsResync() {
        assertTrue(
            "A current root's old-scheme name must trigger a full sync",
            isSweptMixLibraryName("phoenix [file:///project/apps/a]", setOf(".", "apps/a"), "/project"),
        )
    }
}
