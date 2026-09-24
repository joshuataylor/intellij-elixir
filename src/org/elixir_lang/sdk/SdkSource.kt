package org.elixir_lang.sdk

import org.elixir_lang.jps.shared.sdk.SdkPaths
import org.elixir_lang.sdk.wsl.wslCompat

/** [SdkPaths.detectSource], probing a WSL home for kerl only while its distribution is installed. */
internal fun detectSource(sdkHome: String): String? = SdkPaths.detectSource(sdkHome, wslCompat.isReachable(sdkHome))
