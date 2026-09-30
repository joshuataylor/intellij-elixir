package org.elixir_lang.psi

import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall

/** Which modules a `defimpl` is for. */
class ImplementationTest : PlatformTestCase() {
    private fun forNames(code: String): Collection<String>? {
        myFixture.configureByText("scratch.ex", code)

        val defimpl = PsiTreeUtil
            .findChildrenOfType(myFixture.file, Call::class.java)
            .single { Implementation.`is`(it) }

        return Implementation.forNames(SyntacticCall.of(defimpl))
    }

    fun testWithoutForInAModule() =
        assertEquals(listOf("Outer"), forNames("defmodule Outer do\n  defimpl P do\n  end\nend\n"))

    fun testWithoutForInANestedModule() = assertEquals(
        listOf("Outer.Inner"),
        forNames("defmodule Outer do\n  defmodule Inner do\n    defimpl P do\n    end\n  end\nend\n"),
    )

    fun testForModuleInAModule() =
        assertEquals(listOf("Outer"), forNames("defmodule Outer do\n  defimpl P, for: __MODULE__ do\n  end\nend\n"))

    fun testForAListWithModule() = assertEquals(
        listOf("Outer", "Y"),
        forNames("defmodule Outer do\n  defimpl P, for: [__MODULE__, Y] do\n  end\nend\n"),
    )

    /** At the top level there is no module to be for. */
    fun testWithoutForAtTopLevel() = assertNull(forNames("defimpl P do\nend\n"))

    fun testForModuleAtTopLevel() = assertNull(forNames("defimpl P, for: __MODULE__ do\nend\n"))

    /** An empty list is for no modules, which is not the same as a module that cannot be named. */
    fun testForAnEmptyList() = assertEquals(emptyList<String>(), forNames("defimpl P, for: [] do\nend\n"))

    fun testInAQuote() = assertEquals(
        listOf("?"),
        forNames("defmodule M do\n  defmacro m do\n    quote do\n      defimpl P do\n      end\n    end\n  end\nend\n"),
    )
}
