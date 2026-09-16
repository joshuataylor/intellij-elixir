package org.elixir_lang.sdk

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import kotlinx.coroutines.launch
import org.elixir_lang.util.ElixirCoroutineService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the installation of an already-registered SDK a module is pointed at, which the SDK table does not report; this
 * includes a module SDK corrected when the JPS model lands.
 */
internal class SdkVersionModuleRootListener(private val project: Project) : ModuleRootListener {
    private val fillPending = AtomicBoolean(false)

    override fun rootsChanged(event: ModuleRootEvent) {
        // Collapsed: an import fires `rootsChanged` once per module and every pass walks every module. Called inside
        // the write action that changed the roots, so the reading is launched.
        if (!fillPending.compareAndSet(false, true)) return

        project.service<ElixirCoroutineService>().scope.launch {
            fillPending.set(false)
            SdkVersionsFiller.fillUsedBy(project)
        }
    }
}
