package org.elixir_lang.psi.stub

import com.intellij.psi.stubs.StubElement
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.stub.call.Stubbic
import org.elixir_lang.psi.stub.type.File

/** What stub building stores for each call, which the indices and every stub-backed element read back. */
class StoredStubTest : PlatformTestCase() {
    fun testDefimplWithFor() = assertStored(
        "defimpl P, for: X do\nend\n",
        "IMPLEMENTATION P.X Kernel.defimpl/3 do [P.X] P",
    )

    fun testDefimplWithForList() = assertStored(
        "defimpl P, for: [X, Y] do\nend\n",
        "IMPLEMENTATION ? Kernel.defimpl/3 do [P.X, P.Y] P",
    )

    fun testNestedModules() = assertStored(
        "defmodule Outer do\n  defmodule Inner do\n  end\n  defmodule __MODULE__.Named do\n  end\nend\n",
        "MODULE Outer Kernel.defmodule/2 do [Outer] -",
        "MODULE Inner Kernel.defmodule/2 do [Outer.Inner] -",
        "MODULE __MODULE__.Named Kernel.defmodule/2 do [Outer.__MODULE__.Named] -",
    )

    fun testDefprotocol() = assertStored(
        "defprotocol P do\n  def f(t)\nend\n",
        "PROTOCOL P Kernel.defprotocol/2 do [P] -",
        "PUBLIC_FUNCTION f Kernel.def/1 - [f] -",
    )

    fun testQualifiedDefmoduleAndDef() = assertStored(
        "Kernel.defmodule Foo do\n  Kernel.def k, do: 1\nend\n",
        "MODULE Foo Kernel.defmodule/2 do [Foo] -",
        "PUBLIC_FUNCTION k Kernel.def/2 do [k] -",
    )

    fun testModuleCreate() = assertStored(
        "Module.create(Bar, quote(do: nil), Macro.Env.location(__ENV__))\n",
        "- Bar Module.create/3 - [Bar] -",
    )

    fun testDefinitions() = assertStored(
        "defmodule D do\n" +
            "  defdelegate f(a), to: M\n" +
            "  def g(a), do: a\n" +
            "  defp h, do: 1\n" +
            "  defmacro m(x), do: x\n" +
            "  defmacrop mp(x), do: x\n" +
            "  defguard gd(x) when is_integer(x)\n" +
            "  defguardp gdp(x) when is_integer(x)\n" +
            "  def multi(a, b \\\\ 1) do\n    a + b\n  end\n" +
            "end\n",
        "MODULE D Kernel.defmodule/2 do [D] -",
        "- f Kernel.f/1 - [f] -",
        "PUBLIC_FUNCTION g Kernel.def/2 do [g] -",
        "PRIVATE_FUNCTION h Kernel.defp/2 do [h] -",
        "PUBLIC_MACRO m Kernel.defmacro/2 do [m] -",
        "PRIVATE_MACRO mp Kernel.defmacrop/2 do [mp] -",
        "PUBLIC_GUARD gd Kernel.defguard/1 - [gd] -",
        "PRIVATE_GUARD gdp Kernel.defguardp/1 - [gdp] -",
        "PUBLIC_FUNCTION multi Kernel.def/2 do [multi] -",
    )

    fun testSpecificationAndCallbacks() = assertStored(
        "defmodule S do\n" +
            "  @spec f(integer) :: integer\n" +
            "  @callback c(atom) :: atom\n" +
            "  @macrocallback mc(atom) :: Macro.t\n" +
            "  @a 1\n" +
            "end\n",
        "MODULE S Kernel.defmodule/2 do [S] -",
        "MODULE_ATTRIBUTE f null.null/1 - [f] -",
        "MODULE_ATTRIBUTE c null.null/1 - [c] -",
        "MODULE_ATTRIBUTE mc null.null/1 - [mc] -",
    )

    fun testQuotedDeclarations() = assertStored(
        "defmodule U do\n" +
            "  defmacro __using__(_) do\n" +
            "    quote do\n" +
            "      @a 1\n" +
            "      x = 1\n" +
            "      Module.register_attribute(__MODULE__, :b, [])\n" +
            "      Module.put_attribute(__MODULE__, :c, 1)\n" +
            "    end\n" +
            "  end\n" +
            "end\n",
        "MODULE U Kernel.defmodule/2 do [U] -",
        "PUBLIC_MACRO __using__ Kernel.defmacro/2 do [__using__] -",
        "MODULE_ATTRIBUTE @a null.null/1 - [@a] -",
        "VARIABLE x Kernel.x/0 - [x] -",
        "MODULE_ATTRIBUTE @b Module.register_attribute/3 - [@b] -",
        "MODULE_ATTRIBUTE @c Module.put_attribute/3 - [@c] -",
    )

    private fun assertStored(source: String, vararg expected: String) {
        val file = myFixture.configureByText("stored.ex", source)
        val root = File.INSTANCE.builder.buildStubTree(file)

        assertEquals(expected.joinToString("\n"), stubbics(root).joinToString("\n") { render(it) })
    }

    private fun stubbics(stub: StubElement<*>): List<Stubbic> =
        stub.childrenStubs.flatMap { child -> listOfNotNull(child as? Stubbic) + stubbics(child) }

    private fun render(stubbic: Stubbic): String =
        listOf(
            stubbic.definition?.name ?: "-",
            stubbic.name,
            "${stubbic.resolvedModuleName()}.${stubbic.resolvedFunctionName()}/${stubbic.resolvedFinalArity()}",
            if (stubbic.hasDoBlockOrKeyword()) "do" else "-",
            stubbic.canonicalNameSet().sorted().joinToString(", ", "[", "]"),
            stubbic.implementedProtocolName ?: "-",
        ).joinToString(" ")
}
