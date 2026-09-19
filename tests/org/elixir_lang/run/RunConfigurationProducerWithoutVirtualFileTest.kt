package org.elixir_lang.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.espec.configuration.MixESpecRunConfigurationProducer
import org.elixir_lang.exunit.configuration.MixExUnitRunConfigurationProducer
import org.elixir_lang.psi.call.Call

/**
 * A decompiled `.beam`'s `ElixirFile` has no virtual file, and the run configuration producers are asked about it
 * whenever the caret is in one.
 */
class RunConfigurationProducerWithoutVirtualFileTest : PlatformTestCase() {
    fun testESpecProducerIgnoresAFileWithoutAVirtualFile() {
        assertNoConfiguration(MixESpecRunConfigurationProducer::class.java, file("foo_spec.exs"))
    }

    fun testESpecProducerIgnoresAnElementInAFileWithoutAVirtualFile() {
        assertNoConfiguration(MixESpecRunConfigurationProducer::class.java, call(file("foo_spec.exs")))
    }

    fun testExUnitProducerIgnoresAFileWithoutAVirtualFile() {
        assertNoConfiguration(MixExUnitRunConfigurationProducer::class.java, file("foo_test.exs"))
    }

    fun testExUnitProducerIgnoresAnElementInAFileWithoutAVirtualFile() {
        assertNoConfiguration(MixExUnitRunConfigurationProducer::class.java, call(file("foo_test.exs")))
    }

    private fun file(name: String): PsiFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText(name, ElixirLanguage, "defmodule Foo do\n  def bar, do: :ok\nend\n", false, false)
            .also { assertNull("$name has a virtual file", it.virtualFile) }

    private fun call(file: PsiFile): PsiElement = PsiTreeUtil.findChildOfType(file, Call::class.java)!!

    private fun assertNoConfiguration(producerClass: Class<out RunConfigurationProducer<*>>, element: PsiElement) {
        val context = ConfigurationContext.createEmptyContextForLocation(PsiLocation(element))

        assertNull(RunConfigurationProducer.getInstance(producerClass).findOrCreateConfigurationFromContext(context))
    }
}
