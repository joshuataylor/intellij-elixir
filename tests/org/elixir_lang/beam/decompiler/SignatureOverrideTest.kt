package org.elixir_lang.beam.decompiler

import org.elixir_lang.beam.MacroNameArity
import org.elixir_lang.junit.UnitTestCase

class SignatureOverrideTest : UnitTestCase() {
    fun testStructArityOneIncludesDefinitionMacro() {
        val decompiled = StringBuilder()

        SignatureOverride.append(decompiled, MacroNameArity("def", "__struct__", 1))

        assertEquals(
            "  def __struct__(kv) do\n    # body not decompiled\n  end\n",
            decompiled.toString()
        )
    }
}
