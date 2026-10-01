package org.elixir_lang.debugger

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.impl.getModuleName
import org.elixir_lang.Module

/** The module atom a line breakpoint is set in, which must be the atom Elixir compiles the enclosing module to. */
class BreakpointModuleTest : PlatformTestCase() {
    fun testElixirPrefixedAtom() = assertModuleAtom("defmodule :\"Elixir.Foo\" do\n  <caret>x = 1\nend\n", "Elixir.Foo")

    fun testElixirPrefixedAliasAroundANestedModule() = assertModuleAtom(
        "defmodule Elixir.Outer do\n  defmodule Bar do\n    <caret>x = 1\n  end\nend\n",
        "Elixir.Outer.Bar",
    )

    fun testElixirPrefixedAliasInAModule() = assertModuleAtom(
        "defmodule Outer do\n  defmodule Elixir.Inner do\n    <caret>x = 1\n  end\nend\n",
        "Elixir.Inner",
    )

    fun testAtomInAModule() = assertModuleAtom(
        "defmodule Outer do\n  defmodule :inner do\n    <caret>x = 1\n  end\nend\n",
        "inner",
    )

    fun testQuotedAtom() = assertModuleAtom("defmodule :\"foo-bar\" do\n  <caret>x = 1\nend\n", "foo-bar")

    fun testSpacesAroundTheDot() = assertModuleAtom("defmodule Foo . Bar do\n  <caret>x = 1\nend\n", "Elixir.Foo.Bar")

    private fun assertModuleAtom(source: String, expected: String) {
        myFixture.configureByText("breakpoint.ex", source)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        assertEquals(expected, element.getModuleName()?.let(Module::atom))
    }
}
