package org.elixir_lang.mix.library

import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.PersistentLibraryKind

object Kind : PersistentLibraryKind<DummyLibraryProperties>("mix") {
    override fun createDefaultProperties(): DummyLibraryProperties = DummyLibraryProperties.INSTANCE
}

/**
 * What a content root's consolidated-protocols library is named after, scoped like a dep: `(consolidated) [apps/api]`.
 */
internal const val CONSOLIDATED_LIBRARY_BASE_NAME = "(consolidated)"
