package org.elixir_lang.declaration

import org.elixir_lang.call.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DefinerTest(private val row: Row) {
    data class Row(
        val keyword: String,
        val definer: Definer,
        val presentation: Presentation,
        val quotesArguments: Boolean,
        val compileTime: Boolean,
        val usableInGuards: Boolean,
        val visibility: Visibility,
        val runtimeFunction: Boolean,
        val public: Boolean,
        val remoteCallable: Boolean
    ) {
        override fun toString(): String = keyword
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Row> = listOf(
            Row("def", Definer.DEF, Presentation.FUNCTION, false, false, false, Visibility.PUBLIC, true, true, true),
            Row("defp", Definer.DEFP, Presentation.FUNCTION, false, false, false, Visibility.PRIVATE, true, false, false),
            Row("defmemo", Definer.DEFMEMO, Presentation.FUNCTION, false, false, false, Visibility.PUBLIC, true, true, true),
            Row("defmemop", Definer.DEFMEMOP, Presentation.FUNCTION, false, false, false, Visibility.PRIVATE, true, false, false),
            Row("defmacro", Definer.DEFMACRO, Presentation.MACRO, true, true, false, Visibility.PUBLIC, false, true, false),
            Row("defmacrop", Definer.DEFMACROP, Presentation.MACRO, true, true, false, Visibility.PRIVATE, false, false, false),
            Row("defguard", Definer.DEFGUARD, Presentation.GUARD, false, true, true, Visibility.PUBLIC, false, true, false),
            Row("defguardp", Definer.DEFGUARDP, Presentation.GUARD, false, true, true, Visibility.PRIVATE, false, false, false)
        )
    }

    @Test
    fun ofKeyword() {
        assertEquals(row.definer, Definer.of(row.keyword))
    }

    @Test
    fun capabilities() {
        val capabilities = row.definer.capabilities

        assertEquals(
            Capabilities(row.quotesArguments, row.compileTime, row.usableInGuards, row.visibility),
            capabilities
        )
    }

    @Test
    fun presentation() {
        assertEquals(row.presentation, row.definer.capabilities.presentation)
    }

    @Test
    fun derived() {
        val capabilities = row.definer.capabilities

        assertEquals("runtimeFunction", row.runtimeFunction, capabilities.runtimeFunction)
        assertEquals("public", row.public, capabilities.public)
        assertEquals("remoteCallable", row.remoteCallable, capabilities.remoteCallable)
    }

    @Test
    fun rowsCoverEveryDefiner() {
        assertEquals(Definer.entries.toSet(), rows().map { it.definer }.toSet())
    }

    @Test
    fun anythingElseIsNoDefiner() {
        assertNull(Definer.of("defdelegate"))
        assertNull(Definer.of("defmodule"))
        assertNull(Definer.of("foo"))
    }
}
