package org.elixir_lang.tool_manager

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.HeavyPlatformTestCase
import org.elixir_lang.Facet
import org.elixir_lang.facet.Type
import org.elixir_lang.sdk.SdkFixtures

class ToolManagerSdkAssignmentTest : HeavyPlatformTestCase() {
    private val checker get() = ToolManagerSdkChecker(project, emptyList(), ToolManagerSettings())

    private fun sdk(name: String) = SdkFixtures.register(SdkFixtures.elixirSdk(name, "/fake/elixir/$name"), testRootDisposable)

    /** IntelliJ IDEA reads the module SDK before a facet a small IDE left, so the facet is not touched. */
    fun testAnElixirFacetTheModuleAlreadyHasIsLeftAlone() {
        val facet = WriteAction.computeAndWait<Facet, Throwable> {
            FacetUtil.addFacet(module, FacetType.findInstance(Type::class.java))
        }
        val before = sdk("before")
        val after = sdk("after")
        WriteAction.runAndWait<Throwable> { facet.sdk = before }

        WriteAction.runAndWait<Throwable> { checker.assignElixirSdk(module, after) }

        assertEquals(after, ModuleRootManager.getInstance(module).sdk)
        assertEquals(before, facet.sdk)
    }

    fun testAModuleWithoutAnElixirFacetIsGivenNone() {
        val after = sdk("after")

        WriteAction.runAndWait<Throwable> { checker.assignElixirSdk(module, after) }

        assertEquals(after, ModuleRootManager.getInstance(module).sdk)
        assertNull(FacetManager.getInstance(module).getFacetByType(Facet.ID))
    }
}
