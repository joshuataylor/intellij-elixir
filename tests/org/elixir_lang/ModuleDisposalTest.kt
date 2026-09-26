package org.elixir_lang

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.elixir_lang.junit.HeavyTestCase

/** Work queued before a module is removed or its project closes still holds the module when it runs. */
class ModuleDisposalTest : HeavyTestCase() {
    @RequiresEdt
    fun testADisposedModuleIsNotAnElixirModule() {
        val module = createModule("removed")
        WriteAction.run<Throwable> { ModuleManager.getInstance(project).disposeModule(module) }

        assertTrue(module.isDisposed)
        assertFalse(module.isElixirModule())
    }
}
