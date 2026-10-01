package org.elixir_lang.sdk.elixir

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import org.elixir_lang.settings.SettingsPageId

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
        val id = when (page) {
            SettingsPage.MODULE_SDKS -> SettingsPageId.ELIXIR.id
            SettingsPage.SDKS -> SettingsPageId.ELIXIR_SDKS.id
        }
        // By id: a lookup by class builds every page ahead of it, and some, such as the IDE's data-sharing consents, do
        // slow work when built.
        ShowSettingsUtilImpl.showSettingsDialog(project, id, null)
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
