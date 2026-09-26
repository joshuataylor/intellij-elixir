package org.elixir_lang.mix.sync

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.mix.library.Kind

/**
 * Per-library diff to be applied during [applyWritePlan].
 *
 * [createWithKind] is true when the library does not yet exist in the table and must be created
 * with [Kind]. When false, the library exists and only the diff needs to be applied.
 *
 * [recreateWith] is the roots to recreate the library with if it was removed before this op is applied, such as by the
 * user; null when no plan wants the library, so it stays gone.
 */
internal data class LibraryWriteOp(
    val libraryName: String,
    val createWithKind: Boolean,
    val addClassUrls: List<String>,
    val removeClassUrls: List<String>,
    val addSourceUrls: List<String>,
    val removeSourceUrls: List<String>,
    val recreateWith: LibraryRoots?,
)

internal data class LibraryRoots(val classUrls: List<String>, val sourceUrls: List<String>)

/**
 * Per-module dependency wiring to be applied during [applyWritePlan].
 *
 * [addModuleDeps] contains module names whose dep-module is currently live; [addInvalidModuleDeps]
 * contains names whose dep-module is absent or disposed at plan-build time.  [addLibraryDeps] and
 * [addInvalidLibraryDeps] follow the same convention for project libraries.
 *
 * [removeStaleLibraryDeps] is project-level entries naming a Mix library this plan removes, or scoped to a token that
 * is no longer a content root and naming a library that will not exist once the plan is applied.  Of the removed
 * libraries' entries, only a dep's scoped to a current root stays, as the placeholder a re-fetch fills.
 */
internal data class ModuleWriteOp(
    val moduleName: String,
    val addModuleDeps: Set<String>,
    val addInvalidModuleDeps: Set<String>,
    val addLibraryDeps: Set<String>,
    val addInvalidLibraryDeps: Set<String>,
    val addExcludeFolderUrls: List<String>,
    val removeStaleLibraryDeps: Set<String> = emptySet(),
)

/**
 * Immutable description of all model mutations required for one sync cycle.
 *
 * Produced by [buildWritePlan] under [readAction]; consumed by [applyWritePlan] under
 * [com.intellij.openapi.application.edtWriteAction].  Every field is a plain snapshot (strings,
 * primitive collections); no live model objects are retained across the suspend boundary.
 */
internal data class WritePlan(
    /** Library names to remove (from DeleteAll / DeleteOne scanning + legacy cleanup). */
    val librariesToRemove: List<String>,
    /** Diff-based library creates/updates; one op per library that actually changed. */
    val libraryWriteOps: List<LibraryWriteOp>,
    /** Names of libraries to create as empty placeholders (declared deps not yet fetched). */
    val placeholderLibraries: Set<String>,
    /** Per-module order-entry and exclude-folder additions. */
    val moduleWriteOps: List<ModuleWriteOp>,
    /** Legacy unscoped library names to remove when scoped replacements are created. */
    val legacyLibrariesToRemove: List<String>,
) {
    val isEmpty: Boolean
        get() = librariesToRemove.isEmpty() &&
            libraryWriteOps.isEmpty() &&
            placeholderLibraries.isEmpty() &&
            moduleWriteOps.isEmpty() &&
            legacyLibrariesToRemove.isEmpty()
}

/**
 * Snapshots the current project / library-table state under a [readAction] and computes the
 * minimal mutation set required to apply [syncPlan].
 *
 * **Threading contract:** runs entirely inside [readAction]; performs no write-lock acquisition
 * and no model mutations.  All output is an immutable snapshot safe to pass to [applyWritePlan]
 * across a suspend boundary.
 *
 * The diff computation handles:
 * - Delete candidates (DeleteAll / DeleteOne) matched against the table snapshot.
 * - Per-library root diffs (add / remove class and source roots), skipping unchanged libraries.
 * - Legacy-cleanup candidates (unscoped libraries superseded by a new scoped name).
 * - Placeholder library names (declared deps not yet fetched; no physical directory).
 * - Module order-entry additions (module deps + library deps + exclude folders).
 */
internal suspend fun buildWritePlan(project: Project, syncPlan: SyncPlan): WritePlan =
    readAction {
        if (project.isDisposed) return@readAction WritePlan(
            librariesToRemove = emptyList(),
            libraryWriteOps = emptyList(),
            placeholderLibraries = emptySet(),
            moduleWriteOps = emptyList(),
            legacyLibrariesToRemove = emptyList(),
        )
        buildWritePlanInCurrentContext(project, syncPlan)
    }

// ---------------------------------------------------------------------------
// Internal helpers (all requirements: must be called inside readAction)
// ---------------------------------------------------------------------------

/** A library's state when the plan is built. [danglingClassUrls] and [danglingSourceUrls] per [danglingRootUrls]. */
private data class LibSnap(
    val isKind: Boolean,
    val classUrls: Set<String>,
    val sourceUrls: Set<String>,
    val danglingClassUrls: List<String>,
    val danglingSourceUrls: List<String>,
)

/**
 * Core snapshot + diff logic.  Must only be called from within [readAction] or write context.
 */
@RequiresReadLock
private fun buildWritePlanInCurrentContext(project: Project, syncPlan: SyncPlan): WritePlan {
    ThreadingAssertions.assertReadAccess()
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

    // -----------------------------------------------------------------------
    // Snapshot - library-table state
    // -----------------------------------------------------------------------
    val basePath = project.basePath?.let(FileUtil::toSystemIndependentName)
    val liveTokens = liveContentRootTokens(project, basePath)
    val libSnap: Map<String, LibSnap> = buildMap {
        for (lib in libraryTable.libraries) {
            ProgressManager.checkCanceled()
            val name = lib.name ?: continue
            val mixLibrary = (lib as? LibraryEx)?.takeIf { it.kind == Kind }
            put(
                name,
                LibSnap(
                    isKind = mixLibrary != null,
                    classUrls = lib.getUrls(OrderRootType.CLASSES).toSet(),
                    sourceUrls = lib.getUrls(OrderRootType.SOURCES).toSet(),
                    danglingClassUrls = mixLibrary
                        ?.let { danglingRootUrls(it, OrderRootType.CLASSES, liveTokens) }.orEmpty(),
                    danglingSourceUrls = mixLibrary
                        ?.let { danglingRootUrls(it, OrderRootType.SOURCES, liveTokens) }.orEmpty(),
                )
            )
        }
    }

    // -----------------------------------------------------------------------
    // Step 1 - Compute librariesToRemove from DeleteAll / DeleteOne
    // -----------------------------------------------------------------------
    val librariesToRemove = LinkedHashSet<String>()

    for (deleteAll in syncPlan.deleteAlls) {
        ProgressManager.checkCanceled()
        // depsUrl is always "<contentRootUrl>/deps"; strip the suffix to derive scoped-name suffix.
        val contentRootUrl = deleteAll.depsUrl.removeSuffix("/deps")
        // Both scope schemes: a project last synced by an older version names its libraries after
        // the absolute URL, and matching only today's token would leave every one of them behind.
        val scopedSuffixes = setOf(
            " [${contentRootToken(basePath, contentRootUrl)}]",
            " [$contentRootUrl]",
        )
        for ((name, snap) in libSnap) {
            ProgressManager.checkCanceled()
            // Not a consolidated library: its evidence is `_build`, which deleting deps leaves. An unscoped legacy
            // library needs no match here: the sweep below removes every one.
            if (snap.isKind && !isConsolidatedLibraryName(name) && scopedSuffixes.any { name.endsWith(it) }) {
                librariesToRemove += name
            }
        }
    }

    for (deleteOne in syncPlan.deleteOnes) {
        ProgressManager.checkCanceled()
        if (libSnap[deleteOne.libraryName]?.isKind == true) {
            librariesToRemove += deleteOne.libraryName
        }
        // Legacy cleanup: if the scoped name differs from the dep name, also remove the legacy
        // unscoped library (only if it is a Mix-Kind library to avoid touching user libraries).
        if (deleteOne.depName != deleteOne.libraryName && libSnap[deleteOne.depName]?.isKind == true) {
            librariesToRemove += deleteOne.depName
        }
        // And the absolute-URL-scoped name from before tokens went relative, so a delete still
        // finds its target in a project last synced by an older plugin version.
        deleteOne.previousLibraryName?.let { previousName ->
            if (libSnap[previousName]?.isKind == true) {
                librariesToRemove += previousName
            }
        }
    }

    // A scoped name whose token is not in this set names a root that left the project, or names a current root in a
    // scheme an older version wrote.
    val projectContentRootTokens = contentRootTokens(project, basePath)
    // Steps 2, 2b and 3 keep any a plan still wants.
    for ((name, snap) in libSnap) {
        ProgressManager.checkCanceled()
        if (snap.isKind && isSweptMixLibraryName(name, projectContentRootTokens, basePath)) {
            librariesToRemove += name
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 - Compute libraryWriteOps and legacyLibrariesToRemove
    //
    // A name this plan wants is diffed against the snapshot even when Step 1 scheduled it for
    // removal, and taken off the removal list: the platform's library table model drops a library
    // created under a name the same model removed, so remove-then-create would delete it.
    // -----------------------------------------------------------------------
    val keptLibraries = HashSet<String>()
    val libraryWriteOps = mutableListOf<LibraryWriteOp>()
    val legacyLibrariesToRemove = mutableListOf<String>()

    // A library that is not a Mix library is the user's, even under a name a plan wants.
    val userLibraryNames = libSnap.filterValues { !it.isKind }.keys
    for (plan in syncPlan.libraryPlans) {
        ProgressManager.checkCanceled()
        // Legacy cleanup, whether or not the planned name is free: when the scoped name differs from the unscoped dep
        // name, and for the absolute-URL-scoped name used before tokens went relative.
        if (plan.libraryName != plan.depName && libSnap[plan.depName]?.isKind == true) {
            legacyLibrariesToRemove += plan.depName
        }
        plan.previousLibraryName?.let { previousName ->
            if (libSnap[previousName]?.isKind == true) legacyLibrariesToRemove += previousName
        }

        val existing = libSnap[plan.libraryName]
        if (plan.libraryName in userLibraryNames) {
            MixDepsSyncService.LOG.debug {
                "Left the user's library '${plan.libraryName}' alone although a plan wants its name"
            }
            continue
        }
        if (plan.libraryName in librariesToRemove) keptLibraries += plan.libraryName
        val desiredClass = plan.classRootUrls.toSet()
        val desiredSource = plan.sourceRootUrls.toSet()

        if (existing != null) {
            // Diff against existing - only emit an op when something actually changes.
            val addClass = (desiredClass - existing.classUrls).toList()
            val removeClass = (existing.classUrls - desiredClass).toList()
            val addSource = (desiredSource - existing.sourceUrls).toList()
            val removeSource = (existing.sourceUrls - desiredSource).toList()
            if (addClass.isNotEmpty() || removeClass.isNotEmpty() ||
                addSource.isNotEmpty() || removeSource.isNotEmpty()
            ) {
                libraryWriteOps += LibraryWriteOp(
                    libraryName = plan.libraryName,
                    createWithKind = false,
                    addClassUrls = addClass,
                    removeClassUrls = removeClass,
                    addSourceUrls = addSource,
                    removeSourceUrls = removeSource,
                    recreateWith = LibraryRoots(plan.classRootUrls, plan.sourceRootUrls),
                )
            }
            // No diff -> no write op (library is already up to date).
        } else {
            libraryWriteOps += LibraryWriteOp(
                libraryName = plan.libraryName,
                createWithKind = true,
                addClassUrls = plan.classRootUrls,
                removeClassUrls = emptyList(),
                addSourceUrls = plan.sourceRootUrls,
                removeSourceUrls = emptyList(),
                recreateWith = null,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Step 2b - Compute consolidated library write ops (diff-based)
    //
    // Only class roots are diffed against the plan, and a write op is emitted only when something changed; of the
    // source roots, only dangling ones are dropped. An empty classRootUrls list means the library should not exist.
    // -----------------------------------------------------------------------
    for (consolidatedPlan in syncPlan.consolidatedPlans) {
        ProgressManager.checkCanceled()
        val classRootUrls = consolidatedPlan.classRootUrls
        val consolidatedLibName = consolidatedPlan.libraryName
        if (consolidatedLibName in userLibraryNames) {
            MixDepsSyncService.LOG.debug {
                "Left the user's library '$consolidatedLibName' alone although a plan wants its name"
            }
            continue
        }
        val existing = libSnap[consolidatedLibName]
        val desiredClass = classRootUrls.toSet()

        if (desiredClass.isEmpty()) {
            if (existing?.isKind == true) librariesToRemove += consolidatedLibName
            continue
        }
        if (consolidatedLibName in librariesToRemove) keptLibraries += consolidatedLibName

        if (existing != null) {
            val addClass = (desiredClass - existing.classUrls).toList()
            val removeClass = (existing.classUrls - desiredClass).toList()
            val removeSource = existing.danglingSourceUrls
            if (addClass.isNotEmpty() || removeClass.isNotEmpty() || removeSource.isNotEmpty()) {
                libraryWriteOps += LibraryWriteOp(
                    libraryName = consolidatedLibName,
                    createWithKind = false,
                    addClassUrls = addClass,
                    removeClassUrls = removeClass,
                    addSourceUrls = emptyList(),
                    removeSourceUrls = removeSource,
                    recreateWith = LibraryRoots(classRootUrls, (existing.sourceUrls - removeSource.toSet()).toList()),
                )
            }
            // No diff -> no write op
        } else {
            libraryWriteOps += LibraryWriteOp(
                libraryName = consolidatedLibName,
                createWithKind = true,
                addClassUrls = classRootUrls,
                removeClassUrls = emptyList(),
                addSourceUrls = emptyList(),
                removeSourceUrls = emptyList(),
                recreateWith = null,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Step 3 - Compute placeholderLibraries (missingLibraryDeps)
    //
    // A library is a "placeholder" if it is required by a module plan's libraryDeps but:
    //   - is NOT already scheduled for creation via libraryWriteOps, AND
    //   - does NOT currently exist.
    // One that exists but is scheduled for removal is kept and emptied instead, for the same
    // reason as in Step 2.
    // -----------------------------------------------------------------------
    val plannedLibraryNames = libraryWriteOps.mapTo(HashSet()) { it.libraryName }
    val placeholderLibraries = LinkedHashSet<String>()

    for (name in syncPlan.modulePlans.flatMap { it.libraryDeps }) {
        ProgressManager.checkCanceled()
        if (name in plannedLibraryNames || name in keptLibraries) continue
        val existing = libSnap[name]
        if (existing == null) {
            placeholderLibraries += name
        } else if (name in librariesToRemove || name in legacyLibrariesToRemove) {
            keptLibraries += name
            if (existing.classUrls.isNotEmpty() || existing.sourceUrls.isNotEmpty()) {
                libraryWriteOps += LibraryWriteOp(
                    libraryName = name,
                    createWithKind = false,
                    addClassUrls = emptyList(),
                    removeClassUrls = existing.classUrls.toList(),
                    addSourceUrls = emptyList(),
                    removeSourceUrls = existing.sourceUrls.toList(),
                    recreateWith = LibraryRoots(emptyList(), emptyList()),
                )
            }
        }
    }
    librariesToRemove -= keptLibraries
    legacyLibrariesToRemove -= keptLibraries

    // -----------------------------------------------------------------------
    // Step 3b - Drop the roots no plan replaces
    //
    // A Mix library whose `deps` or `_build` directory went keeps roots that point at nothing unless a write op this
    // plan already emits rewrites it, and the startup check requests a full sync for every dangling root. The library
    // stays, emptied of them.
    // -----------------------------------------------------------------------
    val writtenLibraryNames = libraryWriteOps.mapTo(HashSet()) { it.libraryName }
    val moduleWantedNames = syncPlan.modulePlans.flatMapTo(HashSet()) { it.libraryDeps }
    for ((name, snap) in libSnap) {
        ProgressManager.checkCanceled()
        if (!snap.isKind || snap.danglingClassUrls.isEmpty() && snap.danglingSourceUrls.isEmpty()) continue
        if (name in writtenLibraryNames || name in librariesToRemove || name in legacyLibrariesToRemove) continue
        libraryWriteOps += LibraryWriteOp(
            libraryName = name,
            createWithKind = false,
            addClassUrls = emptyList(),
            removeClassUrls = snap.danglingClassUrls,
            addSourceUrls = emptyList(),
            removeSourceUrls = snap.danglingSourceUrls,
            recreateWith = LibraryRoots(
                (snap.classUrls - snap.danglingClassUrls.toSet()).toList(),
                (snap.sourceUrls - snap.danglingSourceUrls.toSet()).toList(),
            ).takeIf { name in moduleWantedNames },
        )
    }

    // -----------------------------------------------------------------------
    // Step 4 - Compute moduleWriteOps
    //
    // For valid/invalid library determination (addLibraryDeps vs addInvalidLibraryDeps):
    // anticipate the post-operation library-table state so that newly-created libraries are
    // wired as valid order entries rather than invalid ones.
    //
    // Known limitation - narrow read->write race window:
    // Entries that already exist at snapshot time are OMITTED from the write op (see the
    // "continue" guards below). If a concurrent write action removes one of those entries
    // in the gap between this readAction completing and applyWritePlan's edtWriteAction
    // acquiring the write lock, the removed entry will not be restored during this cycle.
    //
    // This is an explicit design trade-off: carrying the full desired state (including
    // already-existing entries) would revert applyWritePlan from a diff applicator to a
    // full desired-state enforcer, re-introducing the write-lock pressure that the
    // refactor was designed to eliminate. The countermeasure is self-healing: the next
    // VFS event triggers a new drain that restores any entry removed in this window.
    // The practical likelihood of this race is negligible - it requires another write
    // action to modify module roots within the sub-millisecond async gap.
    // -----------------------------------------------------------------------
    // Set of library names that will exist after all write-plan operations complete:
    //   = current snapshot - to-remove + newly-created (write ops + placeholders)
    val futureLibraries: Set<String> = buildSet {
        addAll(libSnap.keys)
        removeAll(librariesToRemove)
        removeAll(legacyLibrariesToRemove)
        addAll(plannedLibraryNames)
        addAll(placeholderLibraries)
    }

    val moduleManager = ModuleManager.getInstance(project)
    // Removing a library leaves every order entry naming it dangling, whichever module holds it. Only this plugin
    // creates a Mix library, so its entries go with it, except a dep's scoped to a current root: that stays as the
    // placeholder a re-fetch fills.
    val removedMixLibraries = (librariesToRemove + legacyLibrariesToRemove)
        .filterTo(HashSet()) { name ->
            isConsolidatedLibraryName(name) || scopedLibraryNameToken(name) !in projectContentRootTokens
        }
    val moduleNames = buildSet {
        syncPlan.modulePlans.mapTo(this) { it.moduleName }
        syncPlan.libraryPlans.flatMap { it.excludeFolders }.mapTo(this) { it.moduleName }
        syncPlan.consolidatedPlans.mapNotNullTo(this) { it.ownerModuleName }
        if (removedMixLibraries.isNotEmpty()) {
            moduleManager.modules
                .filter { module ->
                    !module.isDisposed && ModuleRootManager.getInstance(module).orderEntries
                        .any { it is LibraryOrderEntry && it.libraryName in removedMixLibraries }
                }
                .mapTo(this) { it.name }
        }
    }
    val modulePlansByName = syncPlan.modulePlans.associateBy { it.moduleName }
    val excludeFoldersByModule = syncPlan.libraryPlans.flatMap { it.excludeFolders }
        .groupBy { it.moduleName }
    // Map: owner module name -> consolidated library names that should be wired as dependencies.
    val consolidatedLibsByModule: Map<String, List<String>> = syncPlan.consolidatedPlans
        .filter { it.ownerModuleName != null && it.classRootUrls.isNotEmpty() }
        .groupBy({ it.ownerModuleName!! }, { it.libraryName })

    val moduleWriteOps = mutableListOf<ModuleWriteOp>()

    for (moduleName in moduleNames) {
        ProgressManager.checkCanceled()
        val module = moduleManager.findModuleByName(moduleName)?.takeIf { !it.isDisposed } ?: continue
        val rootManager = ModuleRootManager.getInstance(module)

        // Snapshot: existing order entries + exclude folders
        val existingModuleDeps = rootManager.orderEntries
            .filterIsInstance<ModuleOrderEntry>()
            .mapTo(HashSet()) { it.moduleName }
        val existingLibraryDeps = rootManager.orderEntries
            .filterIsInstance<LibraryOrderEntry>()
            .mapTo(HashSet()) { it.libraryName }
        val existingExcludeFolderUrls = rootManager.contentEntries
            .flatMapTo(HashSet()) { ce ->
                ce.excludeFolderUrls.filter { it.isNotEmpty() }
            }

        val modulePlan = modulePlansByName[moduleName]
        val addModuleDeps = mutableSetOf<String>()
        val addInvalidModuleDeps = mutableSetOf<String>()
        val addLibraryDeps = mutableSetOf<String>()
        val addInvalidLibraryDeps = mutableSetOf<String>()

        if (modulePlan != null) {
            for (depName in modulePlan.moduleDeps) {
                ProgressManager.checkCanceled()
                if (depName in existingModuleDeps) continue
                val depModule = moduleManager.findModuleByName(depName)
                if (depModule != null && !depModule.isDisposed) {
                    addModuleDeps += depName
                } else {
                    addInvalidModuleDeps += depName
                }
            }

            for (libName in modulePlan.libraryDeps) {
                ProgressManager.checkCanceled()
                if (libName in userLibraryNames) continue
                if (libName in existingLibraryDeps) continue
                // Use the anticipated post-operation library state to decide valid vs invalid.
                // If the library will exist after all write-plan ops, wire it as a valid entry.
                if (libName in futureLibraries) {
                    addLibraryDeps += libName
                } else {
                    addInvalidLibraryDeps += libName
                }
            }
        }

        // Wire consolidated library dependencies for this module (from ConsolidatedLibraryPlan).
        for (consolidatedLibName in consolidatedLibsByModule[moduleName].orEmpty()) {
            ProgressManager.checkCanceled()
            if (consolidatedLibName in userLibraryNames) continue
            if (consolidatedLibName in existingLibraryDeps) continue
            if (consolidatedLibName in futureLibraries) {
                addLibraryDeps += consolidatedLibName
            } else {
                addInvalidLibraryDeps += consolidatedLibName
            }
        }

        // Exclude folder additions (only for folders inside a known content entry).
        val addExcludeFolderUrls = (excludeFoldersByModule[moduleName].orEmpty())
            .filter { plan ->
                rootManager.contentEntries.any { entry ->
                    VfsUtilCore.isEqualOrAncestor(entry.url, plan.folderUrl)
                }
            }
            .map { it.folderUrl }
            .filterNot { it in existingExcludeFolderUrls }

        // An entry scoped to a token that is no longer a content root, whose library will not exist once this plan is
        // applied, can never be wanted again: the root left, or an older version wrote the name in another scheme. One
        // whose library stays, such as an external `path:` dep's, is still wanted.
        val removeStaleLibraryDeps = LinkedHashSet<String>()

        for (entry in rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>()) {
            ProgressManager.checkCanceled()
            if (entry.libraryLevel != LibraryTablesRegistrar.PROJECT_LEVEL) continue
            val name = entry.libraryName ?: continue
            if (name in addLibraryDeps || name in addInvalidLibraryDeps) continue
            if (modulePlan != null && name in modulePlan.libraryDeps) continue
            if (consolidatedLibsByModule[moduleName].orEmpty().contains(name)) continue

            if (name in removedMixLibraries ||
                name !in futureLibraries && scopedLibraryNameToken(name)?.let { it !in projectContentRootTokens } == true
            ) {
                removeStaleLibraryDeps += name
            }
        }


        if (addModuleDeps.isNotEmpty() || addInvalidModuleDeps.isNotEmpty() ||
            addLibraryDeps.isNotEmpty() || addInvalidLibraryDeps.isNotEmpty() ||
            addExcludeFolderUrls.isNotEmpty() || removeStaleLibraryDeps.isNotEmpty()
        ) {
            moduleWriteOps += ModuleWriteOp(
                moduleName = moduleName,
                addModuleDeps = addModuleDeps,
                addInvalidModuleDeps = addInvalidModuleDeps,
                addLibraryDeps = addLibraryDeps,
                addInvalidLibraryDeps = addInvalidLibraryDeps,
                addExcludeFolderUrls = addExcludeFolderUrls,
                removeStaleLibraryDeps = removeStaleLibraryDeps,
            )
        }
    }

    return WritePlan(
        librariesToRemove = librariesToRemove.toList(),
        libraryWriteOps = libraryWriteOps,
        placeholderLibraries = placeholderLibraries,
        moduleWriteOps = moduleWriteOps,
        legacyLibrariesToRemove = legacyLibrariesToRemove,
    )
}
