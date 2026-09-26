package org.elixir_lang.mix.sync

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.runAll
import org.elixir_lang.junit.HeavyTestCase
import org.elixir_lang.mix.library.Kind as MixLibraryKind
import java.io.File
import java.util.concurrent.Callable

/**
 * Heavy tests for [MixDepsSyncService] covering an umbrella imported one module per app, where one
 * app declares another with `in_umbrella:`:
 * ```
 *   module_app_a  content-root: umbrella/apps/app_a/
 *   module_app_b  content-root: umbrella/apps/app_b/
 *
 *   umbrella/
 *     mix.exs                  (apps_path: "apps", declares no deps)
 *     deps/phoenix/lib/
 *     _build/dev/lib/phoenix/ebin/
 *     apps/app_a/mix.exs       (declares {:app_b, in_umbrella: true})
 *     apps/app_b/mix.exs       (declares {:phoenix, ">= 0.0.0"})
 * ```
 *
 * The umbrella root is deliberately not a content root of any module - that is the import shape
 * [#3990](https://github.com/intellij-elixir/intellij-elixir/issues/3990) reports, and it is what makes
 * `app_a`'s own content root the only place the resolver starts from.
 */
class MixDepsSyncServiceSiblingAppHeavyTest : HeavyTestCase() {

    private lateinit var umbrellaVf: VirtualFile
    private lateinit var appAMixExs: VirtualFile

    override fun setUp() {
        super.setUp()

        val umbrellaDir = createTempDir("sibling_umbrella")
        FileUtil.writeToFile(
            File(umbrellaDir, "mix.exs"),
            "defmodule SiblingUmbrella.MixProject do\n" +
                "  use Mix.Project\n\n" +
                "  def project do\n" +
                "    [apps_path: \"apps\"]\n" +
                "  end\nend\n"
        )
        File(umbrellaDir, "deps/phoenix/lib").mkdirs()
        File(umbrellaDir, "_build/dev/lib/phoenix/ebin").mkdirs()
        File(umbrellaDir, "apps/app_a").mkdirs()
        FileUtil.writeToFile(
            File(umbrellaDir, "apps/app_a/mix.exs"),
            "defmodule AppA.MixProject do\n" +
                "  use Mix.Project\n\n" +
                "  def project do\n" +
                "    [app: :app_a, version: \"0.1.0\", deps: deps()]\n" +
                "  end\n\n" +
                "  def deps do\n" +
                "    [{:app_b, in_umbrella: true}]\n" +
                "  end\nend\n"
        )
        File(umbrellaDir, "apps/app_b").mkdirs()
        FileUtil.writeToFile(
            File(umbrellaDir, "apps/app_b/mix.exs"),
            "defmodule AppB.MixProject do\n" +
                "  use Mix.Project\n\n" +
                "  def project do\n" +
                "    [app: :app_b, version: \"0.1.0\", deps: deps()]\n" +
                "  end\n\n" +
                "  def deps do\n" +
                "    [{:phoenix, \">= 0.0.0\"}]\n" +
                "  end\nend\n"
        )

        val umbrellaVfRaw = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(umbrellaDir)
            ?: error("sibling_umbrella not found in VFS after refresh")
        VfsUtil.markDirtyAndRefresh(false, true, true, umbrellaVfRaw)
        umbrellaVf = umbrellaVfRaw

        appAMixExs = umbrellaVf.findFileByRelativePath("apps/app_a/mix.exs")
            ?: error("sibling_umbrella/apps/app_a/mix.exs not found in VFS after refresh")

        // One module per app, and no module for the umbrella root - the app dirs are the only
        // content roots there are.
        PsiTestUtil.addContentRoot(
            createModule("module_app_a"),
            umbrellaVf.findFileByRelativePath("apps/app_a")!!
        )
        PsiTestUtil.addContentRoot(
            createModule("module_app_b"),
            umbrellaVf.findFileByRelativePath("apps/app_b")!!
        )
    }

    override fun tearDown() {
        runAll(
            { MixSyncTestHelpers.removeAllLibraries(project) },
            { super.tearDown() },
        )
    }

    private fun module(name: String): Module =
        ModuleManager.getInstance(project).findModuleByName(name) ?: error("$name not found")

    private fun libraryEntryNames(moduleName: String): List<String> =
        ReadAction.nonBlocking(Callable {
            ModuleRootManager.getInstance(module(moduleName)).orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .mapNotNull { it.libraryName }
        }).executeSynchronously()

    /** `app_a`'s module entries. */
    private fun appAModuleEntryNames(): List<String> =
        ReadAction.nonBlocking(Callable {
            ModuleRootManager.getInstance(module("module_app_a")).orderEntries
                .filterIsInstance<ModuleOrderEntry>()
                .map { it.moduleName }
        }).executeSynchronously()

    /** Only the apps are content roots, so the umbrella's dep libraries keep their roots, as an external dep's do. */
    fun testAnUmbrellaDepsDanglingRootIsLeftAlone() {
        val name = scopedDepLibraryName(contentRootToken(project, umbrellaVf.url), "gone_dep")
        val danglingRoot = "${umbrellaVf.url}/_build/dev/lib/gone_dep/ebin"
        WriteAction.runAndWait<Throwable> {
            val model = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            model.createLibrary(name, MixLibraryKind).modifiableModel.let { libraryModel ->
                libraryModel.addRoot(danglingRoot, OrderRootType.CLASSES)
                libraryModel.commit()
            }
            model.commit()
        }
        val needsResync = { MixSyncTestHelpers.runSuspendOnPooledThread { MixLibraryReconciler.needsResync(project) } }

        assertFalse("an umbrella dep's dangling root must not trigger a full sync", needsResync())
        val service = project.service<MixDepsSyncService>()
        service.clearPendingForTesting()
        service.enqueue(SyncRequest.All)
        MixSyncTestHelpers.drainDirectly(service)
        assertEquals(
            listOf(danglingRoot),
            LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(name)!!
                .getUrls(OrderRootType.CLASSES).toList(),
        )
    }

    /** An app visited without a plan of its own still owns its references to the umbrella's dep libraries. */
    fun testAnAppVisitedWithoutAPlanKeepsItsUmbrellaDepEntries() {
        val appA = umbrellaVf.findFileByRelativePath("apps/app_a")!!
        val phoenixName = scopedDepLibraryName(contentRootToken(project, umbrellaVf.url), "phoenix")
        val consolidatedName = consolidatedLibraryName(project, appA.url)
        WriteAction.runAndWait<Throwable> {
            val model = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            val phoenix = model.createLibrary(phoenixName, MixLibraryKind)
            val consolidated = model.createLibrary(consolidatedName, MixLibraryKind)
            model.commit()
            ModuleRootModificationUtil.addDependency(module("module_app_a"), phoenix)
            ModuleRootModificationUtil.addDependency(module("module_app_a"), consolidated)
        }
        // `app_a`'s `_build` went, so its consolidated library goes, which sends its module through the stale pass.
        val syncPlan = SyncPlan(
            consolidatedPlans = listOf(
                ConsolidatedLibraryPlan(appA.url, contentRootToken(project, appA.url), emptyList(), "module_app_a"),
            ),
        )

        val writePlan = MixSyncTestHelpers.runSuspendOnPooledThread { buildWritePlan(project, syncPlan) }

        assertTrue("the precondition: the consolidated library goes", consolidatedName in writePlan.librariesToRemove)
        assertEquals(
            "the umbrella's dep entry must stay",
            emptyList<String>(),
            writePlan.moduleWriteOps.flatMap { it.removeStaleLibraryDeps }.filter { it == phoenixName },
        )
    }

    private fun drainForAppA() {
        val service = project.service<MixDepsSyncService>()
        service.clearPendingForTesting()
        service.enqueue(SyncRequest.MixFile(appAMixExs))
        MixSyncTestHelpers.drainDirectly(service)
    }

    /**
     * `phoenix` is declared only by `app_b`, and `app_a` reaches it by declaring `app_b`. Without
     * walking the sibling's own `mix.exs`, `app_a`'s module gets no entry for it and every alias
     * `app_a` writes against a `phoenix` module is unresolved - the report on #3990.
     *
     * `app_b`'s own entry is asserted alongside it as the precondition: a run that wired nothing at
     * all would otherwise satisfy nothing here.
     */
    fun testSiblingsDepIsWiredToTheDeclaringAppsModule() {
        val libName = scopedDepLibraryName(contentRootToken(project, umbrellaVf.url), "phoenix")

        drainForAppA()

        assertTrue(
            "Precondition: app_b declares {:phoenix, ...} directly, so its own module must be " +
                "wired to '$libName'. Order entries: ${libraryEntryNames("module_app_b")}",
            libraryEntryNames("module_app_b").contains(libName),
        )
        assertTrue(
            "app_a reaches phoenix through {:app_b, in_umbrella: true}, and a module order entry " +
                "carries no unexported library, so app_a needs '$libName' in its own right. " +
                "Order entries: ${libraryEntryNames("module_app_a")}",
            libraryEntryNames("module_app_a").contains(libName),
        )
    }

    /**
     * The sibling is a module of this project, so it stays a module order entry rather than
     * becoming a library of its own - the library entry above is in addition to it, not instead.
     */
    fun testSiblingRemainsAModuleOrderEntry() {
        drainForAppA()

        assertTrue(
            "app_b is a module of the project. Module dep entries: ${appAModuleEntryNames()}",
            appAModuleEntryNames().contains("app_b"),
        )
        assertFalse(
            "app_b must not also be wired as a library. Order entries: ${libraryEntryNames("module_app_a")}",
            libraryEntryNames("module_app_a").any { it.contains("app_b") },
        )
    }
}
