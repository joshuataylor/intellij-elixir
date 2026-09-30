package org.elixir_lang.reference.resolver.atom.resolvable

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.ElixirAtom

class ExactTest : PlatformTestCase() {
    fun testQuotedAtomResolvesToModuleNamedWithIt() {
        assertEquals(
            setOf("defmodule :\"a.b\" do\nend"),
            resolve(
                """
                defmodule :"a.b" do
                end

                defmodule User do
                  def f, do: :"a.b"
                end
                """
            )
        )
    }

    fun testPlainAtomResolvesToModuleNamedWithIt() {
        assertEquals(
            setOf("defmodule :plain do\nend"),
            resolve(
                """
                defmodule :plain do
                end

                defmodule User do
                  def f, do: :plain
                end
                """
            )
        )
    }

    fun testPlainAtomResolvesToModuleNamedWithItQuoted() {
        assertEquals(
            setOf("defmodule :\"plain\" do\nend"),
            resolve(
                """
                defmodule :"plain" do
                end

                defmodule User do
                  def f, do: :plain
                end
                """
            )
        )
    }

    fun testAtomResolvesToAtomNamedModuleNestedInAnotherModule() {
        assertEquals(
            setOf("defmodule :inner_target do\n  end"),
            resolve(
                """
                defmodule Outer do
                  defmodule :inner_target do
                  end
                end

                defmodule User do
                  def f, do: :inner_target
                end
                """
            )
        )
    }

    fun testElixirPrefixedAtomResolvesToAliasNamedModule() {
        assertEquals(
            setOf("defmodule Foo do\nend"),
            resolve(
                """
                defmodule Foo do
                end

                defmodule User do
                  def f, do: :"Elixir.Foo"
                end
                """
            )
        )
    }

    fun testQuotedAtomResolvesToModuleNamedWithItCharListQuoted() {
        assertEquals(
            setOf("defmodule :'a.b' do\nend"),
            resolve(
                """
                defmodule :'a.b' do
                end

                defmodule User do
                  def f, do: :"a.b"
                end
                """
            )
        )
    }

    fun testElixirPrefixedAtomResolvesToModuleNotFunction() {
        assertEquals(
            setOf("defmodule :\"Elixir.foo\" do\nend"),
            resolve(
                """
                defmodule :"Elixir.foo" do
                end

                defmodule User do
                  def foo, do: :"Elixir.foo"
                end
                """
            )
        )
    }

    fun testUnquotedAtomLongerThanAnAtomCanBeResolvesToNothing() {
        val name = "a".repeat(256)

        assertEquals(
            emptySet<String>(),
            resolve(
                """
                defmodule :$name do
                end

                defmodule User do
                  def f, do: :$name
                end
                """
            )
        )
    }

    fun testEmptyAtomResolvesToModuleNamedWithIt() {
        assertEquals(
            setOf("defmodule :\"\" do\nend"),
            resolve(
                """
                defmodule :"" do
                end

                defmodule User do
                  def f, do: :""
                end
                """
            )
        )
    }

    fun testAtomLongerThanAnAtomCanBeResolvesToNothing() {
        val name = "a".repeat(256)

        assertEquals(
            emptySet<String>(),
            resolve(
                """
                defmodule :"$name" do
                end

                defmodule User do
                  def f, do: :"$name"
                end
                """
            )
        )
    }

    private fun resolve(source: String): Set<String> {
        myFixture.configureByText("exact.ex", source.trimIndent())
        val atom = PsiTreeUtil
            .findChildrenOfType(myFixture.file, ElixirAtom::class.java)
            .last()

        return (atom.reference as PsiPolyVariantReference).multiResolve(false).map { it.element!!.text }.toSet()
    }
}
