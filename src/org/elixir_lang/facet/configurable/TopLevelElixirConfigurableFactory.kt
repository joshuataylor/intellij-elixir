package org.elixir_lang.facet.configurable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

interface TopLevelElixirConfigurableFactory {
    fun create(project: Project): Configurable

    companion object {
        fun getInstance(): TopLevelElixirConfigurableFactory =
            ApplicationManager.getApplication().getService(TopLevelElixirConfigurableFactory::class.java)
    }
}

class SmallIdeTopLevelElixirConfigurableFactory : TopLevelElixirConfigurableFactory {
    override fun create(project: Project): Configurable = Project(project)
}

/** Module SDKs are set in Project Structure here, so the page has nothing of its own: Settings lists its child pages. */
class RichPlatformTopLevelElixirConfigurableFactory : TopLevelElixirConfigurableFactory {
    override fun create(project: Project): Configurable = object : Configurable {
        override fun getDisplayName(): String = "Elixir"
        override fun createComponent(): JComponent? = null
        override fun isModified(): Boolean = false
        override fun apply() {}
    }
}
