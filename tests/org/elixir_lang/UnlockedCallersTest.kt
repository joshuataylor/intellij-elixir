package org.elixir_lang

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.elixir_lang.breadcrumbs.Provider
import org.elixir_lang.debugger.breakpointModuleNames
import org.elixir_lang.goto_decompiled.Item
import org.elixir_lang.psi.call.Call
import java.util.concurrent.Callable

/** Entries the platform can call from a thread that holds no read lock. */
class UnlockedCallersTest : PlatformTestCase() {
    fun testBreakpointModuleNames() {
        val file = myFixture.configureByText("unlocked.ex", SOURCE)

        assertEquals(
            setOf("Foo"),
            unlocked { breakpointModuleNames(project, file.virtualFile, SOURCE.indexOf("bar")) },
        )
    }

    fun testGotoRelatedSpeedSearchContainerName() {
        val file = myFixture.configureByText("unlocked.ex", SOURCE)
        val definer = PsiTreeUtil.getParentOfType(file.findElementAt(SOURCE.indexOf("def ")), Call::class.java)!!
        val expected = ReadAction.computeBlocking<String?, RuntimeException> { Item(definer).customContainerName }
        // `Provider.getItems` builds the item under a read lock; only the popup's speed search reads it without one.
        val item = ReadAction.computeBlocking<Item, RuntimeException> { Item(definer) }

        assertNotNull(expected)
        assertEquals(expected, unlocked { item.customContainerName })
    }

    fun testRecentLocationsBreadcrumbs() {
        val file = myFixture.configureByText("unlocked.ex", SOURCE)
        val definer = PsiTreeUtil.getParentOfType(file.findElementAt(SOURCE.indexOf("def ")), Call::class.java)!!
        val provider = Provider()

        assertEquals(true to "bar/0", unlocked { provider.acceptElement(definer) to provider.getElementInfo(definer) })
    }

    private fun <T> unlocked(call: () -> T): T = AppExecutorUtil.getAppExecutorService().submit(Callable { call() }).get()

    private companion object {
        const val SOURCE = "defmodule Foo do\n  def bar, do: 1\nend\n"
    }
}
