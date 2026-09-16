package org.elixir_lang.sdk

import com.intellij.util.messages.Topic

fun interface SdkVersionsListener {
    /**
     * [homePaths] are every spelling of the one installation [canonicalHome]. Carried rather than looked up: a listener
     * working off the publishing thread may ask after the entries are gone.
     */
    fun sdkVersionsChanged(homePaths: Set<String>, canonicalHome: String)

    companion object {
        @JvmField
        @Topic.AppLevel
        val TOPIC: Topic<SdkVersionsListener> =
            Topic.create("Elixir SDK versions changed", SdkVersionsListener::class.java)
    }
}
