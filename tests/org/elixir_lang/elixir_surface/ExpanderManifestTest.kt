package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.junit.UnitTestCase

class ExpanderManifestTest : UnitTestCase() {
    fun testGuardedSpecialFormClauseIsItsOwnLine() {
        val lines = ExpanderManifest.specialFormLines(
            listOf(
                clause(listOf(v("N"), v("_")), guard(op("==", v("N"), atom("x"))), atom("false")),
                clause(listOf(v("_"), v("_")), OtpErlangList(), atom("false")),
            )
        )

        assertEquals(2, lines.size)
        assertEquals(2, lines.toSet().size)
    }

    fun testRegenerateCommandNamesTheLeg() {
        val command = ExpanderManifest.regenerateCommand(elixirVersion = "1.21.0", otpVersion = "29.1.1")

        assertTrue(command, command.contains(" \"-PelixirVersion=1.21.0\" "))
        assertTrue(command, command.endsWith(" \"-PotpVersion=29.1.1\""))
        assertTrue(command, command.contains("-PoverwriteTestData=true"))
    }

    fun testDuplicateSpecialFormClausesAreKept() {
        val clause = clause(listOf(atom("&"), integer(1)), OtpErlangList(), atom("true"))

        assertEquals(listOf("&/1 true", "&/1 true"), ExpanderManifest.specialFormLines(listOf(clause, clause)))
    }

    fun testSpecialFormLine() {
        assertEquals(listOf("&/1 true"), ExpanderManifest.specialFormLines(listOf(clause(listOf(atom("&"), integer(1)), OtpErlangList(), atom("true")))))
    }

    private fun form(tag: String, vararg elements: OtpErlangObject): OtpErlangTuple =
        OtpErlangTuple(arrayOf(OtpErlangAtom(tag), OtpErlangLong(1), *elements))

    private fun v(name: String) = form("var", OtpErlangAtom(name))
    private fun atom(name: String) = form("atom", OtpErlangAtom(name))
    private fun integer(value: Long) = form("integer", OtpErlangLong(value))
    private fun op(operator: String, vararg operands: OtpErlangObject) = form("op", OtpErlangAtom(operator), *operands)
    private fun guard(vararg tests: OtpErlangObject) = OtpErlangList(arrayOf<OtpErlangObject>(OtpErlangList(tests)))

    private fun clause(patterns: List<OtpErlangObject>, guards: OtpErlangList, body: OtpErlangObject): OtpErlangTuple =
        form("clause", OtpErlangList(patterns.toTypedArray()), guards, OtpErlangList(arrayOf(body)))
}
