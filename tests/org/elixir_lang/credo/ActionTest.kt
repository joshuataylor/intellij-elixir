package org.elixir_lang.credo

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.elixir_lang.junit.LightTestCase
import org.elixir_lang.settings.SettingsPageLookup

class ActionTest : LightTestCase() {
    fun testConfigureCredoOpensTheCredoPage() {
        SettingsPageLookup(testRootDisposable).assertOpens(Configurable::class.java) {
            Action(project).actionPerformed(
                TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project)),
                Notification("Elixir", "", NotificationType.WARNING)
            )
        }
    }
}
