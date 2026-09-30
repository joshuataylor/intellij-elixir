package org.elixir_lang.reference.callable

import com.intellij.psi.PsiPolyVariantReference
import org.elixir_lang.PlatformTestCase
import java.io.File

/** Whether a call resolves to what an `import`'s options bring in from `m.ex`, the module the import oracle uses. */
class ImportOptionsTest : PlatformTestCase() {
    fun testNoOptionsBringsInEverything() {
        assertResolves("import M", "g(1)")
        assertResolves("import M", "f(1, 2)")
        assertResolves("import M", "mac(1)")
        assertResolves("import M", "is_small(1)")
        assertResolves("import M", "sigil_x(\"a\", [])")
    }

    fun testOnlyFunctionsBringsInFunctions() {
        assertResolves("import M, only: :functions", "g(1)")
        assertDoesNotResolve("import M, only: :functions", "mac(1)")
    }

    fun testOnlyFunctionsBringsInADefaultedFunction() {
        assertResolves("import M, only: :functions", "f(1)")
        assertResolves("import M, only: :functions", "f(1, 2)")
    }

    fun testOnlyMacrosBringsInMacros() {
        assertResolves("import M, only: :macros", "mac(1)")
        assertDoesNotResolve("import M, only: :macros", "g(1)")
    }

    fun testOnlyMacrosBringsInGuards() {
        assertResolves("import M, only: :macros", "is_small(1)")
        assertDoesNotResolve("import M, only: :functions", "is_small(1)")
    }

    fun testOnlySigilsBringsInSigils() {
        assertResolves("import M, only: :sigils", "sigil_x(\"a\", [])")
        assertDoesNotResolve("import M, only: :sigils", "g(1)")
    }

    fun testOnlyOneArityOfADefaultedFunctionBringsInTheDefinition() {
        assertResolves("import M, only: [f: 2]", "f(1, 2)")
    }

    fun testExceptOneArityOfADefaultedFunctionLeavesOutTheDefinition() {
        assertDoesNotResolve("import M, except: [f: 1]", "f(1)")
    }

    fun testExceptOfAnAttributeInAFunctionKeepsOtherNames() {
        assertTrue(
            "`g(1)` under `import M, except: @excluded` in a function resolves to no definition",
            resolvesToDefinition(
                """
                defmodule U do
                  @excluded [f: 1]

                  def u do
                    import M, except: @excluded
                    <caret>g(1)
                  end
                end
                """.trimIndent()
            )
        )
    }

    fun testUnquotedExceptInAQuoteIsNotRead() {
        assertTrue(
            "`f(1)` under `import M, except: unquote(excluded)` in a quote resolves to no definition",
            resolvesToDefinition(
                """
                defmodule U do
                  defmacro __using__(excluded) do
                    quote do
                      import M, except: unquote(excluded)

                      def u do
                        <caret>f(1)
                      end
                    end
                  end
                end
                """.trimIndent()
            )
        )
    }

    fun testFirstRepeatedOnlyWins() {
        assertResolves("import M, only: [g: 1], only: [f: 1]", "g(1)")
        assertDoesNotResolve("import M, only: [g: 1], only: [f: 1]", "f(1)")
    }

    fun testFirstRepeatedExceptWins() {
        assertResolves("import M, except: [g: 1], except: [f: 1]", "f(1)")
        assertDoesNotResolve("import M, except: [g: 1], except: [f: 1]", "g(1)")
    }

    fun testNoOptionsLeavesOutPrivateDefinitions() {
        assertDoesNotResolve("import M", "private(1)")
        assertDoesNotResolve("import M", "private_mac(1)")
    }

    fun testOnlyFunctionsLeavesOutPrivateFunctions() {
        assertDoesNotResolve("import M, only: :functions", "private(1)")
    }

    fun testOnlyMacrosLeavesOutPrivateMacros() {
        assertDoesNotResolve("import M, only: :macros", "private_mac(1)")
    }

    fun testExceptLeavesOutPrivateDefinitions() {
        assertDoesNotResolve("import M, except: [f: 1]", "private(1)")
    }

    fun testExceptOneArityOfADelegatedFunctionIsNotReachedThroughAnotherArity() {
        val delegations = """
            defmodule Target do
              def snoc(q), do: q
              def snoc(q, x), do: [x | q]
            end

            defmodule D do
              defdelegate snoc(q), to: Target
              defdelegate snoc(q, x), to: Target
            end
        """.trimIndent()

        assertEmpty(validTargets(delegations, "import D, except: [snoc: 1]", "snoc(1)"))
        assertContainsElements(
            validTargets(delegations, "import D, except: [snoc: 1]", "snoc(1, 2)"),
            "def snoc(q, x), do: [x | q]"
        )
    }

    fun testADelegationOfAnotherArityDoesNotHideTheTarget() {
        val delegations = """
            defmodule Target do
              def get(m, k, d \\ nil), do: {m, k, d}
            end

            defmodule D do
              defdelegate get(m, k, d), to: Target
              defdelegate get(m, k), to: Target
            end
        """.trimIndent()

        assertContainsElements(
            validTargets(delegations, "import D", "get(1, 2)"),
            """def get(m, k, d \\ nil), do: {m, k, d}"""
        )
    }

    /** The first line of each valid result for [use] under [import], after the modules [delegations] declares. */
    private fun validTargets(delegations: String, import: String, use: String): List<String> {
        val user = """
            defmodule U do
              $import

              def u do
                <caret>$use
              end
            end
        """.trimIndent()
        myFixture.configureByText("u.ex", "$delegations\n\n$user")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as PsiPolyVariantReference

        return reference.multiResolve(false).filter { it.isValidResult }.mapNotNull { it.element?.text?.lines()?.first() }
    }

    fun testUnderscoredNamesAreNotImported() {
        assertDoesNotResolve("import M", "_hidden(1)")
        assertDoesNotResolve("import M, except: [f: 1]", "_hidden(1)")
    }

    fun testUnderscoredNamesAreImportedWhenOnlyNamesThem() {
        assertResolves("import M, only: [_hidden: 1]", "_hidden(1)")
    }

    fun testOnlyWithExceptBringsInNothing() {
        assertDoesNotResolve("import M, only: [g: 1], except: [f: 1]", "g(1)")
    }

    fun testBracketedOptionsAreRead() {
        assertResolves("import M, [only: [g: 1]]", "g(1)")
        assertDoesNotResolve("import M, [only: [g: 1]]", "f(1)")
    }

    private fun assertResolves(import: String, use: String) {
        assertTrue("`$use` under `$import` resolves to no definition", resolvesToDefinition(import, use))
    }

    private fun assertDoesNotResolve(import: String, use: String) {
        assertFalse("`$use` under `$import` resolves to a definition", resolvesToDefinition(import, use))
    }

    private fun resolvesToDefinition(import: String, use: String): Boolean =
        resolvesToDefinition(
            """
            defmodule U do
              $import

              def u do
                <caret>$use
              end
            end
            """.trimIndent()
        )

    private fun resolvesToDefinition(text: String): Boolean {
        myFixture.configureByText("u.ex", text)
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as PsiPolyVariantReference

        // The `import` line is a result of its own, so only a definition in `m.ex` counts.
        return reference.multiResolve(false).any { it.isValidResult && it.element?.containingFile?.name == "m.ex" }
    }

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("m.ex", File("testData/org/elixir_lang/psi/import/oracle/m.ex").readText())
    }
}
