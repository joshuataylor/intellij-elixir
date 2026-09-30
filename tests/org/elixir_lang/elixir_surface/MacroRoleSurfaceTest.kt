package org.elixir_lang.elixir_surface

import com.intellij.util.text.VersionComparatorUtil
import org.elixir_lang.declaration.MacroRole
import org.elixir_lang.declaration.MacroRole.Block
import org.elixir_lang.declaration.MacroRole.Modelling
import org.elixir_lang.junit.UnitTestCase
import org.elixir_lang.psi.call.name.Module.KERNEL_SPECIAL_FORMS
import java.io.File

/**
 * Reads every supported Elixir's committed `macros.txt`, not only the leg's own, so each leg checks them all and a new
 * Elixir's list fails here until its new macros are classified.
 */
class MacroRoleSurfaceTest : UnitTestCase() {
    private data class Entry(val version: String, val module: String, val name: String) {
        override fun toString(): String = "$version $module $name"
    }

    private val entries: List<Entry> by lazy {
        File(LegManifest.ROOT)
            .listFiles { directory -> File(directory, MANIFEST).isFile }
            .orEmpty()
            .sortedWith(compareBy(VersionComparatorUtil.COMPARATOR) { it.name })
            .flatMap { directory ->
                File(directory, MANIFEST).readLines().filterNot { it.startsWith("#") }.map { line ->
                    val (module, nameArity) = line.removeSuffix(" hidden").split(" ", limit = 2)

                    Entry(directory.name, module, nameArity.substringBeforeLast("/"))
                }
            }
    }

    fun testEveryMacroOfEverySupportedElixirIsClassified() {
        assertFalse("no $MANIFEST under ${LegManifest.ROOT}", entries.isEmpty())

        val unclassified = entries.filter { MacroRole.listed(it.name) == null }

        assertTrue(
            "Classify each in org.elixir_lang.declaration.MacroRole:\n${unclassified.joinToString("\n")}",
            unclassified.isEmpty()
        )
    }

    fun testOnlyTheSpecialFormsAreSpecialForms() {
        val misclassified = entries.filter { entry ->
            MacroRole.listed(entry.name)?.let { role ->
                (role.modelling == Modelling.SPECIAL_FORM) != (entry.module == KERNEL_SPECIAL_FORMS)
            } ?: false
        }

        assertTrue(misclassified.joinToString("\n"), misclassified.isEmpty())
    }

    fun testAnUnlistedMacroWithABlockDependsOnItsExpansion() {
        assertEquals(MacroRole(Modelling.OPAQUE, Block.EXPANSION_DEPENDENT), MacroRole.of("describe", hasDoBlock = true))
    }

    fun testAnUnlistedMacroWithoutABlockIsOpaque() {
        assertEquals(MacroRole(Modelling.OPAQUE, Block.NOT_A_BLOCK), MacroRole.of("describe", hasDoBlock = false))
    }

    companion object {
        private const val MANIFEST = "macros.txt"
    }
}
