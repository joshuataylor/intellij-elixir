package org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.v1.definitions.definition

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.beam.BeamReader
import org.elixir_lang.beam.MacroNameArity
import org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.V1
import org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.v1.definitions.Definition
import org.elixir_lang.beam.decompiler.Options
import java.io.File

class DefaultArgumentClauseTest : PlatformTestCase() {
    fun testTheDecompiledClauseIsNamedAfterTheClauseItCalls() {
        assertEquals(listOf("term"), inspect1().renderedClauses(Options())!!.single().parameters)
    }

    fun testTheDebugInfoClauseKeepsTheNamesTheBeamStores() {
        assertEquals(listOf("x0"), inspect1().clauses!!.single().parameters)
    }

    private fun inspect1(): Definition {
        val beam = File("testData/org/elixir_lang/beam/decompiler/Docs/Elixir.Kernel.beam")

        return BeamReader.read(beam.readBytes(), beam.path) { reader ->
            (reader.debugInfo as V1).definitions!![MacroNameArity("def", "inspect", 1)]
        }!!
    }
}
