package org.elixir_lang.reference.resolver.atom.resolvable

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.beam.BeamLibraryTestCase
import org.elixir_lang.psi.ElixirAtom
import java.io.File

class DecompiledExactTest : BeamLibraryTestCase() {
    override val ebinDirectory: File = File("testData/org/elixir_lang/beam/quoted_atom_module/ebin").absoluteFile

    fun testQuotedAtomResolvesToDecompiledModuleWhoseNameNeedsQuotes() {
        myFixture.configureByText("decompiled.ex", "defmodule User do\n  def f, do: :\"foo-bar\"\nend\n")
        val atom = PsiTreeUtil.findChildrenOfType(myFixture.file, ElixirAtom::class.java).single()

        assertEquals(
            setOf(":\"foo-bar\""),
            (atom.reference as PsiPolyVariantReference)
                .multiResolve(false)
                .map { (it.element as PsiNamedElement).name }
                .toSet()
        )
    }
}
