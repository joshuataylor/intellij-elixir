package org.elixir_lang.mix

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.ElixirAccessExpression
import org.elixir_lang.psi.ElixirDoBlock
import java.util.concurrent.TimeUnit

class DepsTest : PlatformTestCase() {
    /**
     * No source parses to an access expression without exactly one child, so the test gives a second child to the one ending
     * a `deps` helper.
     */
    fun testDepsHelperEndingInAccessExpressionWithoutExactlyOneChildHasNoDeps() {
        val psiFile = myFixture.configureByText(
            "mix.exs",
            """
            defmodule Sample.MixProject do
              def project do
                [deps: deps()]
              end

              defp deps do
                [ecto_dep()]
              end

              defp ecto_dep do
                1
              end
            end
            """.trimIndent()
        )
        val accessExpression = PsiTreeUtil.findChildrenOfType(psiFile, ElixirAccessExpression::class.java).single {
            PsiTreeUtil.getParentOfType(it, ElixirDoBlock::class.java)?.parent?.text?.startsWith("defp ecto_dep") == true
        }

        WriteCommandAction.runWriteCommandAction(project) {
            accessExpression.node.addChild(accessExpression.firstChild.copy().node)
        }

        assertEquals(2, accessExpression.children.size)

        val gatherer = DepGatherer()
        val indicator = EmptyProgressIndicator()
        val cancellation = AppExecutorUtil.getAppScheduledExecutorService().schedule(indicator::cancel, 10, TimeUnit.SECONDS)

        try {
            ProgressManager.getInstance().runProcess({ psiFile.accept(gatherer) }, indicator)
        } finally {
            cancellation.cancel(false)
        }

        assertFalse("gathering deps did not return within 10 seconds", indicator.isCanceled)
        assertEmpty(gatherer.depSet)
    }
}
