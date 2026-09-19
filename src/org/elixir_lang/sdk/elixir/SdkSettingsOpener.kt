package org.elixir_lang.sdk.elixir

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager

/** Which settings a notice sends the user to; IntelliJ IDEA has one Project Structure for both. */
enum class SettingsPage { MODULE_SDKS, SDKS }

interface SdkSettingsOpener {
    fun open(event: AnActionEvent, page: SettingsPage = SettingsPage.SDKS)

    fun targetName(): String

    companion object {
        fun getInstance(): SdkSettingsOpener =
            ApplicationManager.getApplication().getService(SdkSettingsOpener::class.java)
    }
}

internal class SettingsSdkSettingsOpener : SdkSettingsOpener {
    override fun open(event: AnActionEvent, page: SettingsPage) {
        val project = event.project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        val configurable = when (page) {
            SettingsPage.MODULE_SDKS -> org.elixir_lang.facet.configurable.Project::class.java
            SettingsPage.SDKS -> org.elixir_lang.facet.sdks.elixir.Configurable::class.java
        }
        ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable)
    }

    override fun targetName(): String = "Settings"
}

internal class ProjectStructureSdkSettingsOpener : SdkSettingsOpener {
    override fun open(event: AnActionEvent, page: SettingsPage) {
        val action = ActionManager.getInstance().getAction("ShowProjectStructureSettings")
        if (action != null) {
            ActionUtil.performAction(action, event)
        }
    }

    override fun targetName(): String = "Project Structure"
}
