package org.elixir_lang.settings

import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.elixir_lang.facet.configurable.Project
import org.elixir_lang.facet.configurable.RichPlatformTopLevelElixirConfigurableFactory
import org.elixir_lang.facet.configurable.SmallIdeTopLevelElixirConfigurableFactory

class ExperimentalSettingsPageTest : BasePlatformTestCase() {
    fun testTheExperimentalSettingsAreAChildPageOfElixirInEveryIde() {
        val page = Configurable.APPLICATION_CONFIGURABLE.extensionList.singleOrNull { it.id == "language.elixir.experimental" }

        assertNotNull("registered in plugin.xml, which every IDE loads", page)
        assertEquals("language.elixir", page!!.parentId)
        assertEquals(ElixirExperimentalSettingsConfigurable::class.java.name, page.instanceClass)
        assertEquals("Experimental Settings", ElixirExperimentalSettingsConfigurable().displayName)
    }

    fun testTheRichIdeElixirPageLeavesItsContentToItsChildPages() {
        val configurable = RichPlatformTopLevelElixirConfigurableFactory().create(project)

        assertNull("Settings lists the child pages in place of a missing component", configurable.createComponent())
    }

    fun testTheSmallIdeElixirPageIsStillTheModuleSdkChooser() {
        val configurable = SmallIdeTopLevelElixirConfigurableFactory().create(project)

        assertInstanceOf(configurable, Project::class.java)
    }
}
