package org.elixir_lang.sdk.elixir

import org.elixir_lang.PlatformTestCase
import org.elixir_lang.sdk.SdkFixtures
import org.elixir_lang.sdk.SdkVersionsStore

class TypeDocumentationUrlTest : PlatformTestCase() {
    override fun tearDown() {
        try {
            SdkVersionsStore.getInstance().clearForTests()
        } finally {
            super.tearDown()
        }
    }

    fun testOfferedOnceTheElixirVersionIsRecorded() {
        val sdk = SdkFixtures.elixirSdk("Documentation Elixir", "/fake/elixir/documentation")
        SdkVersionsStore.getInstance().setElixirVersions(sdk.homePath!!, ElixirVersions("1.19.5", OtpMajor.Unread))

        assertNotNull(Type.instance.getDefaultDocumentationUrl(sdk))
    }

    fun testNotOfferedWithoutARecordedElixirVersion() {
        val sdk = SdkFixtures.elixirSdk("Documentation Elixir without version", "/fake/elixir/documentation-none")

        assertNull(Type.instance.getDefaultDocumentationUrl(sdk))
    }
}
