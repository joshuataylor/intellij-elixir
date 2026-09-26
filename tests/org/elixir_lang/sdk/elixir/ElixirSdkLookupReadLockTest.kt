package org.elixir_lang.sdk.elixir

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.junit.logs.expectErrors
import java.util.concurrent.Callable

/**
 * Pins the read-lock contract on [ElixirSdkLookup], the SDK lookup the `mix format` path calls.
 *
 * Calling it from a pooled thread with no read action held once crashed with
 * `Read access is allowed from inside read-action only`; the lookup now asserts the contract itself
 * and its formatter caller wraps it in `ReadAction.nonBlocking(...).executeSynchronously()`. Both
 * halves are asserted here: unwrapped fails the assertion, wrapped resolves.
 *
 * Only the `Module` and `Project` overloads are asserted off-lock. The `PsiElement` overloads
 * reproduce the reported stack, but `ModuleUtilCore.findModuleForPsiElement` enforces the same lock
 * itself, so a test on them stays green with the plugin's assertion removed and would pin the
 * platform rather than the plugin.
 */
class ElixirSdkLookupReadLockTest : PlatformTestCase() {
    private fun elixirFile(): PsiFile =
        myFixture.configureByText("read_lock.ex", "defmodule ReadLock do\nend\n")

    private fun <T> onPooledThread(callable: Callable<T>): T =
        ApplicationManager.getApplication().executeOnPooledThread(callable).get()

    // The pool logs what the call throws and returns null, so the logged error is the evidence.
    private fun assertRequiresReadAccess(callable: Callable<*>) {
        var result: Any? = Unit

        expectErrors(
            "#com.intellij.openapi.application.impl.ApplicationImpl",
            Regex("^Read access is allowed from inside read-action only"),
        ) { result = onPooledThread(callable) }

        assertNull(result)
    }

    fun testResolveForModuleRequiresReadAccess() {
        val module = myFixture.module

        assertRequiresReadAccess(Callable { ElixirSdkLookup.resolve(module) })
    }

    fun testResolveForProjectRequiresReadAccess() {
        val project = myFixture.project

        assertRequiresReadAccess(Callable { ElixirSdkLookup.resolve(project) })
    }

    /**
     * The idiom `MixFormatFormattingService.createFormattingTask` uses. Dropping that wrapper is what
     * makes this test red, so it pins the caller and not only the assertion inside the lookup.
     */
    fun testResolveWithErlangInsideNonBlockingReadActionResolves() {
        val file = elixirFile()

        val resolution = onPooledThread {
            ReadAction.nonBlocking(Callable { ElixirSdkLookup.resolveWithErlang(file) }).executeSynchronously()
        }

        // No Elixir SDK is configured in this fixture, so the lookup runs to completion and reports that
        // rather than throwing.
        assertEquals(ElixirSdkResolution.MissingElixirSdk, resolution)
        assertNull(resolution.sdk)
    }
}
