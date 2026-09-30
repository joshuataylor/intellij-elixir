package org.elixir_lang.reference.callable

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import org.elixir_lang.beam.BeamLibraryTestCase
import java.io.File

/** `:"Elixir.Foo"` is the module Elixir calls `Foo`, however each side writes it. */
class ElixirPrefixedAtomQualifierTest : BeamLibraryTestCase() {
    override val ebinDirectory: File = File("testData/org/elixir_lang/beam/quoted_atom_module/ebin").absoluteFile

    fun testAliasCallResolvesToModuleNamedWithElixirPrefixedAtom() {
        val resolved = resolvedAtCaret(
            """
            defmodule :"Elixir.Foo" do
              def f, do: :atom_named
            end

            defmodule User do
              def g, do: Foo.f<caret>()
            end
            """
        )

        assertTrue("Foo.f() resolved to ${resolved.map { it.text }}", resolved.any { it.text.contains(":atom_named") })
    }

    fun testElixirPrefixedAtomCallResolvesToAliasNamedModule() {
        val resolved = resolvedAtCaret(
            """
            defmodule Foo do
              def f, do: :alias_named
            end

            defmodule User do
              def g, do: :"Elixir.Foo".f<caret>()
            end
            """
        )

        assertTrue(
            ":\"Elixir.Foo\".f() resolved to ${resolved.map { it.text }}",
            resolved.any { it.text.contains(":alias_named") }
        )
    }

    fun testElixirPrefixedAtomCallResolvesToDecompiledModule() {
        val resolved = resolvedAtCaret(
            """
            defmodule User do
              def g, do: :"Elixir.QuotedAtomFoo".f<caret>()
            end
            """
        )

        assertNotEmpty(resolved)
        assertTrue(
            ":\"Elixir.QuotedAtomFoo\".f() resolved to ${resolved.map { "${it.containingFile.name}: ${it.text}" }}",
            resolved.none { it.containingFile == myFixture.file }
        )
    }

    private fun resolvedAtCaret(source: String): List<PsiElement> {
        myFixture.configureByText("qualifier.ex", source.trimIndent())
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as PsiPolyVariantReference

        return reference.multiResolve(false).mapNotNull { it.element }
    }
}
