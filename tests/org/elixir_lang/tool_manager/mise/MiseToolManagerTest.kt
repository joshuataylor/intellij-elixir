package org.elixir_lang.tool_manager.mise

import com.intellij.testFramework.UsefulTestCase

class MiseToolManagerTest : UsefulTestCase() {
    /** The description is HTML the widget shows as is, so the config path it names is escaped here. */
    fun testTheUntrustedConfigHintEscapesItsPathAndKeepsItsMarkup() {
        val description = MiseToolManager.untrustedConfigDescription("/home/u/a<b>/mise.toml")

        assertTrue(description, description.contains("/home/u/a&lt;b&gt;/mise.toml"))
        assertTrue(description, description.contains("<code>mise trust</code>"))
    }
}
