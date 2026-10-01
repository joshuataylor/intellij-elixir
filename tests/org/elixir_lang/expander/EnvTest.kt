package org.elixir_lang.expander

import org.elixir_lang.NameArity
import org.elixir_lang.beam.ReadResult
import org.elixir_lang.elixir_surface.LegManifest
import org.elixir_lang.junit.logs.UnexpectedLogsRule
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class EnvTest {
    @get:Rule
    val unexpectedLogs = UnexpectedLogsRule()

    @Test
    fun `the empty env on the leg imports Kernel's exports and requires the leg's defaults`() {
        val level = legLevel()
        val kernel = legKernel
        val minor = "${level.elixir.major}.${level.elixir.minor}"
        val leg = LEGS[minor]
            ?: throw AssertionError("no expected Kernel counts for Elixir $minor: add its row to LEGS")

        assertEquals("Kernel functions on $minor", leg.functions, kernel.functions.size)
        assertEquals("Kernel macros on $minor", leg.macros, kernel.macros.size)
        assertEquals(
            Env(
                aliases = emptyList(),
                requires = leg.requires,
                functions = listOf(Env.Imports(KERNEL, kernel.functions)),
                macros = listOf(Env.Imports(KERNEL, kernel.macros)),
                macroAliases = emptyList(),
                context = Env.Context.NONE,
                contextModules = emptyList(),
                module = null,
                function = null,
            ),
            Env.empty(level, kernel)
        )
    }

    @Test
    fun `the default requires lose Kernel Typespec at 1_17_0-rc_0`() {
        val kernel = KernelImports(emptyList(), emptyList())

        assertEquals(WITH_TYPESPEC, Env.empty(ElixirLanguageLevel.of("1.16.3"), kernel).requires)
        assertEquals(WITHOUT_TYPESPEC, Env.empty(ElixirLanguageLevel.of("1.17.0-rc.0"), kernel).requires)
    }

    @Test
    fun `Kernel's imports are sorted by name and then arity`() {
        val kernel = legKernel

        assertEquals(kernel.functions.sortedWith(NAME_THEN_ARITY), kernel.functions)
        assertEquals(kernel.macros.sortedWith(NAME_THEN_ARITY), kernel.macros)
    }

    @Test
    fun `a macro's arity leaves out the caller argument of its MACRO- export`() {
        val kernel = legKernel

        assertTrue("if/2 in ${kernel.macros}", NameArity("if", 2) in kernel.macros)
        assertFalse("if/3 in ${kernel.macros}", NameArity("if", 3) in kernel.macros)
        assertTrue(kernel.macros.none { it.name.startsWith("MACRO-") })
        assertTrue(kernel.functions.none { it.name.startsWith("MACRO-") })
    }

    @Test
    fun `the functions __info__ leaves out are not imported`() {
        val functions = legKernel.functions

        assertTrue("is_atom/1 in $functions", NameArity("is_atom", 1) in functions)
        for (omitted in listOf(NameArity("__info__", 1), NameArity("module_info", 0), NameArity("module_info", 1))) {
            assertFalse("$omitted in $functions", omitted in functions)
        }
    }

    @Test
    fun `a Kernel beam with a corrupt export table is unreadable, and not reported as the plugin's error`() {
        val beam = File(LegManifest.ebin(), "Elixir.Kernel.beam")
        val bytes = beam.readBytes()
        val expT = String(bytes, Charsets.ISO_8859_1).indexOf("ExpT")
        assertTrue("no ExpT chunk in $beam", expT >= 0)
        // `ExpT`, its size, then its entry count, which is made larger than the chunk can hold.
        val count = expT + 8
        for (i in 0..3) bytes[count + i] = 0x7F

        val read = KernelImports.read(bytes, beam.path)

        assertTrue("$read", read is ReadResult.Unreadable)
    }

    private class Leg(val functions: Int, val macros: Int, val requires: List<String>)

    private companion object {
        const val KERNEL = "Elixir.Kernel"
        val WITH_TYPESPEC = listOf("Elixir.Application", KERNEL, "Elixir.Kernel.Typespec")
        val WITHOUT_TYPESPEC = listOf("Elixir.Application", KERNEL)

        /** `Kernel.__info__(:functions)` and `(:macros)` counts, and `__ENV__.requires`, read on each leg's Elixir. */
        val LEGS = mapOf(
            "1.11" to Leg(81, 67, WITH_TYPESPEC),
            "1.12" to Leg(81, 70, WITH_TYPESPEC),
            "1.13" to Leg(82, 70, WITH_TYPESPEC),
            "1.14" to Leg(84, 74, WITH_TYPESPEC),
            "1.15" to Leg(84, 74, WITH_TYPESPEC),
            "1.16" to Leg(84, 74, WITH_TYPESPEC),
            "1.17" to Leg(85, 76, WITHOUT_TYPESPEC),
            "1.18" to Leg(85, 76, WITHOUT_TYPESPEC),
            "1.19" to Leg(85, 76, WITHOUT_TYPESPEC),
            "1.20" to Leg(85, 76, WITHOUT_TYPESPEC),
        )

        val NAME_THEN_ARITY = compareBy<NameArity>({ it.name }, { it.arity })
    }
}
