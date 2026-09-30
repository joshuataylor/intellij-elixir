package org.elixir_lang.facet.sdks.elixir

import com.intellij.openapi.projectRoots.SdkType
import org.elixir_lang.facet.sdks.Configurable
import org.elixir_lang.sdk.elixir.Type
import org.elixir_lang.settings.SettingsPageId

class Configurable: Configurable() {
    override fun getDisplayName() = "SDKs"
    override fun getId() = SettingsPageId.ELIXIR_SDKS.id
    override fun sdkType(): SdkType = Type.instance
}
