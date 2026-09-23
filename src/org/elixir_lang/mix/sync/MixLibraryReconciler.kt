package org.elixir_lang.mix.sync

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import org.elixir_lang.mix.library.Kind

/**
 * Decides whether the Mix libraries a project was left with still describe what is on disk, and asks
 * for a full re-sync only when they do not.
 *
 * Nothing re-syncs an already-configured project at startup, and the service is otherwise driven
 * purely by VFS events - so a `deps/` directory removed while the IDE was closed leaves libraries
 * pointing at nothing, with order entries that stay *valid* (the library object still exists) and are
 * therefore invisible to stale-entry pruning.
 *
 * Answering it by simply syncing everything would undo the pipeline's main cost saving: a full sync
 * canonicalises `_build/<env>/lib/<dep>/ebin` per dep per build environment, which is real
 * filesystem I/O, and in the steady state the diff that follows emits no write ops at all. This check
 * reads only library state already held in memory, so the common case - nothing wrong - costs no
 * directory traversal and no symlink resolution.
 */
internal object MixLibraryReconciler {

    /**
     * Whether [project]'s Mix libraries disagree with the project's current content roots.
     *
     * Reports true when a Mix-Kind library has a root that is gone ([danglingRootUrls]), or a name a sync's sweep
     * removes ([isSweptMixLibraryName]): one full sync then clears it, so the check cannot request a sync on every open.
     *
     * Accuracy is bounded by the VFS: a deletion the refresh has not yet observed still reads as
     * valid, so this is a cheap trigger rather than a guarantee.
     */
    suspend fun needsResync(project: Project): Boolean = readAction {
        if (project.isDisposed) return@readAction false

        val basePath = project.basePath?.let(FileUtil::toSystemIndependentName)
        val currentTokens = contentRootTokens(project, basePath)
        val liveTokens = liveContentRootTokens(project, basePath)

        val mixLibraries = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries
            .filterIsInstance<LibraryEx>()
            // A nameless library is out of the sync's reach, so it cannot be cleared either way.
            .filter { it.kind == Kind && !it.isDisposed && it.name != null }

        val trigger = mixLibraries.firstOrNull { libraryEx ->
            val danglingRoot = danglingRootUrls(libraryEx, OrderRootType.CLASSES, liveTokens).isNotEmpty() ||
                danglingRootUrls(libraryEx, OrderRootType.SOURCES, liveTokens).isNotEmpty()

            danglingRoot || isSweptMixLibraryName(libraryEx.name!!, currentTokens, basePath)
        }

        trigger != null
    }
}
