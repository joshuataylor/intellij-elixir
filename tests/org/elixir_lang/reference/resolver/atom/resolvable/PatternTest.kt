package org.elixir_lang.reference.resolver.atom.resolvable

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.ElixirAtom

class PatternTest : PlatformTestCase() {
    fun testInterpolatedAtomResolvesToEveryMatchingName() {
        assertEquals(
            setOf("defmodule :interpolated_target do\nend", "defmodule :other_target do\nend"),
            resolve(
                """
                defmodule :interpolated_target do
                end

                defmodule :other_target do
                end

                defmodule User do
                  def target, do: :"#{:interpolated}_target"
                end
                """
            )
        )
    }

    fun testInterpolationOnlyResolvesToEveryModule() {
        assertEquals(
            setOf("defmodule :one do\nend", "defmodule :two do\nend", "defmodule User do\n  def f(x), do: :\"#{x}\"\nend"),
            resolve(
                """
                defmodule :one do
                end

                defmodule :two do
                end

                defmodule User do
                  def f(x), do: :"#{x}"
                end
                """
            )
        )
    }

    fun testLeadingLiteralParenthesisIsNotAGroup() {
        assertEquals(
            setOf("defmodule :\"a(b\" do\nend"),
            resolve(
                """
                defmodule :a_b do
                end

                defmodule :"a(b" do
                end

                defmodule User do
                  def f(x), do: :"a(#{x}"
                end
                """
            )
        )
    }

    fun testLeadingLiteralQuestionMarkIsNotAQuantifier() {
        assertEquals(
            setOf("defmodule :a? do\nend"),
            resolve(
                """
                defmodule :a? do
                end

                defmodule :b do
                end

                defmodule User do
                  def f(x), do: :"a?#{x}"
                end
                """
            )
        )
    }

    fun testLeadingLiteralDotIsNotAWildcard() {
        assertEquals(
            setOf("defmodule :\"a.b\" do\nend"),
            resolve(
                """
                defmodule :aXb do
                end

                defmodule :"a.b" do
                end

                defmodule User do
                  def f(x), do: :"a.#{x}"
                end
                """
            )
        )
    }

    fun testLiteralBetweenInterpolationsParenthesisIsNotAGroup() {
        assertEquals(
            setOf("defmodule :\"a(b\" do\nend"),
            resolve(
                """
                defmodule :a_b do
                end

                defmodule :"a(b" do
                end

                defmodule User do
                  def f(a, b), do: :"#{a}(#{b}"
                end
                """
            )
        )
    }

    fun testTrailingLiteralQuestionMarkIsNotAQuantifier() {
        assertEquals(
            setOf("defmodule :a? do\nend"),
            resolve(
                """
                defmodule :a? do
                end

                defmodule :a do
                end

                defmodule User do
                  def f(x), do: :"#{x}?"
                end
                """
            )
        )
    }

    fun testMatchMustReachTheEndOfTheName() {
        assertEquals(
            setOf("defmodule :x_target do\nend"),
            resolve(
                """
                defmodule :x_target do
                end

                defmodule :x_target_extra do
                end

                defmodule User do
                  def f, do: :"#{:x}_target"
                end
                """
            )
        )
    }

    fun testMatchMustStartAtTheStartOfTheName() {
        assertEquals(
            setOf("defmodule :a_c do\nend"),
            resolve(
                """
                defmodule :a_c do
                end

                defmodule :"b:a_c" do
                end

                defmodule User do
                  def f(x), do: :"a_#{x}"
                end
                """
            )
        )
    }

    fun testInterpolatedAtomResolvesToModuleNamedWithAQuotedAtom() {
        assertEquals(
            setOf("defmodule :\"a.b\" do\nend"),
            resolve(
                """
                defmodule :"a.b" do
                end

                defmodule User do
                  def f(x), do: :"#{x}.b"
                end
                """
            )
        )
    }

    fun testInterpolatedAtomResolvesToAtomNamedModuleNestedInAnotherModule() {
        assertEquals(
            setOf("defmodule :inner_target do\n  end"),
            resolve(
                """
                defmodule Outer do
                  defmodule :inner_target do
                  end
                end

                defmodule User do
                  def f(x), do: :"#{x}_target"
                end
                """
            )
        )
    }

    fun testInterpolatedElixirPrefixedAtomResolvesToAliasNamedModule() {
        assertEquals(
            setOf("defmodule Foo do\nend"),
            resolve(
                """
                defmodule Foo do
                end

                defmodule Bar do
                end

                defmodule User do
                  def f(x), do: :"Elixir.Fo#{x}"
                end
                """
            )
        )
    }

    fun testInterpolationOnlyResolvesToNoModuleWhoseNameHasNoValue() {
        val name = "a".repeat(256)

        assertEquals(
            setOf("defprotocol P do\nend", "defmodule User do\n  def f(x), do: :\"#{x}\"\nend"),
            resolve(
                """
                defmodule :$name do
                end

                defprotocol P do
                end

                defimpl P do
                end

                defmodule User do
                  def f(x), do: :"#{x}"
                end
                """
            )
        )
    }

    private fun resolve(source: String): Set<String> {
        myFixture.configureByText("pattern.ex", source.trimIndent())
        val atom = PsiTreeUtil
            .findChildrenOfType(myFixture.file, ElixirAtom::class.java)
            .single { it.text.contains("#{") }

        return (atom.reference as PsiPolyVariantReference).multiResolve(false).map { it.element!!.text }.toSet()
    }
}
