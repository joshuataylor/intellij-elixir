package org.elixir_lang.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import org.elixir_lang.sdk.elixir.Type as ElixirSdkType
import org.elixir_lang.sdk.erlang.Type as ErlangSdkType
import org.elixir_lang.sdk.erlang_dependent.SdkAdditionalData as ElixirSdkAdditionalData

internal object SdkFixtures {
    fun elixirHome(vsn: String): String {
        val home = FileUtil.createTempDirectory("elixir_home", null, true)
        val ebin = File(home, "lib/elixir/ebin")
        check(ebin.mkdirs()) { "Failed to create $ebin" }
        File(ebin, "elixir.app").writeText("{application,elixir,\n [{description,\"elixir\"},\n  {vsn,\"$vsn\"}]}.\n")
        return home.path
    }

    fun erlangHome(major: String, otpVersion: String): String {
        val home = FileUtil.createTempDirectory("erlang_home", null, true)
        val release = File(home, "releases/$major")
        check(release.mkdirs()) { "Failed to create $release" }
        File(release, "OTP_VERSION").writeText("$otpVersion\n")
        return home.path
    }

    fun elixirSdk(name: String, homePath: String): Sdk = ProjectJdkImpl(name, ElixirSdkType.instance, homePath, "")

    fun erlangSdk(name: String, homePath: String): Sdk = ProjectJdkImpl(name, ErlangSdkType.instance, homePath, "")

    fun register(sdk: Sdk, parentDisposable: Disposable): Sdk {
        WriteAction.run<Throwable> { ProjectJdkTable.getInstance().addJdk(sdk, parentDisposable) }
        return sdk
    }

    fun commit(sdk: Sdk, data: com.intellij.openapi.projectRoots.SdkAdditionalData?) {
        WriteAction.run<Throwable> {
            sdk.sdkModificator.apply {
                sdkAdditionalData = data
                commitChanges()
            }
        }
    }

    /** What the SDK entity's XML holds: a fresh modificator reloads the additional data from it. */
    fun persistedElixirData(sdk: Sdk): ElixirSdkAdditionalData? =
        sdk.sdkModificator.sdkAdditionalData as? ElixirSdkAdditionalData

    fun waitUntil(message: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError(message)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
    }
}
