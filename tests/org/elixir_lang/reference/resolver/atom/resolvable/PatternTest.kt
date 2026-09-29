package org.elixir_lang.reference.resolver.atom.resolvable

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.ElixirAtom

class PatternTest : PlatformTestCase() {
    fun testInterpolatedAtomResolvesToEveryMatchingName() {
        myFixture.configureByText(
            "pattern.ex",
            """
            defmodule :interpolated_target do
            end

            defmodule :other_target do
            end

            defmodule User do
              def target, do: :"#{:interpolated}_target"
            end
            """.trimIndent()
        )
        val atom = PsiTreeUtil
            .findChildrenOfType(myFixture.file, ElixirAtom::class.java)
            .single { it.text.startsWith(":\"") }

        val resolved = (atom.reference as PsiPolyVariantReference).multiResolve(false).map { it.element!!.text }

        assertEquals(
            setOf("defmodule :interpolated_target do\nend", "defmodule :other_target do\nend"),
            resolved.toSet()
        )
    }
}
