package org.elixir_lang.mix.watcher

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.elixir_lang.mix.Dep
import java.util.*

object TransitiveResolution {
    /**
     * The transitive deps reachable from [rootVirtualFiles] in the order reached, each with the directory [Resolution]
     * resolved it to, or null when that directory is not there.
     *
     * Delegates PSI reads to [Resolution.resolution] which uses WARA ([com.intellij.openapi.application.readAction]) internally,
     * allowing write actions to preempt without blocking the EDT.
     */
    suspend fun transitiveDepRoots(
            psiManager: PsiManager,
            progressIndicator: ProgressIndicator,
            vararg rootVirtualFiles: VirtualFile
    ): Map<Dep, VirtualFile?> {
        val resolution = Resolution.resolution(psiManager, progressIndicator, *rootVirtualFiles)

        return transitiveResolution(resolution, *rootVirtualFiles)
            .associateWith { resolution.depToRootVirtualFile[it] }
    }

    // Non-suspend: walks pre-computed maps with no PSI access.
    private fun transitiveResolution(resolution: Resolution, vararg rootVirtualFiles: VirtualFile): Set<Dep> {
        val visitedDepSet = mutableSetOf<Dep>()
        val unvisitedDepQueue = ArrayDeque<Dep>()

        for (rootVirtualFile in rootVirtualFiles) {
            val rootVirtualFileDepSet = resolution.rootVirtualFileToDepSet[rootVirtualFile]!!
            unvisitedDepQueue.addAll(rootVirtualFileDepSet)
        }

        while (unvisitedDepQueue.isNotEmpty()) {
            val unvisitedDep =  unvisitedDepQueue.remove()

            resolution.depToRootVirtualFile[unvisitedDep]?.let { depRootVirtualFile ->
                for (depDep in resolution.rootVirtualFileToDepSet[depRootVirtualFile]!!) {
                    if (!visitedDepSet.contains(depDep)) {
                        unvisitedDepQueue.add(depDep)
                    }
                }
            }

            visitedDepSet.add(unvisitedDep)
        }

        return visitedDepSet
    }
}
