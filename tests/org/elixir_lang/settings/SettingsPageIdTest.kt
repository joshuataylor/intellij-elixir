package org.elixir_lang.settings

import com.intellij.openapi.options.Configurable
import org.elixir_lang.junit.LightTestCase

class SettingsPageIdTest : LightTestCase() {
    fun testEveryIdIsAPageInPluginXml() {
        val declared = (Configurable.APPLICATION_CONFIGURABLE.extensionList +
            Configurable.PROJECT_CONFIGURABLE.getExtensions(project)).map { it.id }.toSet()

        assertEmpty(SettingsPageId.entries.filterNot { it.id in declared })
    }
}
