package org.elixir_lang.beam.decompiler

import org.elixir_lang.PlatformTestCase

class SignatureParametersTest : PlatformTestCase() {
    fun testNames() {
        assertEquals(listOf("map1", "map2"), signatureParameters("merge(map1, map2)"))
    }

    fun testEmptyParentheses() {
        assertEquals(emptyList<String>(), signatureParameters("now()"))
    }

    fun testNoParentheses() {
        assertEquals(emptyList<String>(), signatureParameters("now"))
    }

    fun testCommasInsideContainersDoNotSplit() {
        assertEquals(
            listOf("data", "[head | tail]", "value \\\\ %{a: 1, b: [2, 3]}", "<<a, b>>", "{c, d}"),
            signatureParameters("put(data, [head | tail], value \\\\ %{a: 1, b: [2, 3]}, <<a, b>>, {c, d})")
        )
    }

    fun testCommasInsideStringsAndFnDoNotSplit() {
        assertEquals(
            listOf("separator \\\\ \", \"", "fun \\\\ fn a, b -> a end"),
            signatureParameters("join(separator \\\\ \", \", fun \\\\ fn a, b -> a end)")
        )
    }
}
