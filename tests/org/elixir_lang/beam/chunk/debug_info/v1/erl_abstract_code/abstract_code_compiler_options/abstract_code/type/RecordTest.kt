package org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.type

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.junit.UnitTestCase

class RecordTest : UnitTestCase() {
    fun testRendersTheNamelessBuiltinRecordType() {
        val type = OtpErlangTuple(
            arrayOf(OtpErlangAtom("type"), OtpErlangLong(1), OtpErlangAtom("record"), OtpErlangList())
        )

        assertEquals("record()", Record.ifToString(type))
    }

    fun testRendersARemoteNativeRecordType() {
        assertEquals(":shapes.point()", Record.ifToString(shapesRecordType("point")))
    }

    /** A record name need not be a callable name, and a remote one is quoted, not wrapped in a local `unquote`. */
    fun testRendersARemoteNativeRecordTypeWhoseNameIsNotCallable() {
        assertEquals(":shapes.\"Point\"()", Record.ifToString(shapesRecordType("Point")))
    }

    private fun shapesRecordType(name: String): OtpErlangTuple {
        val line = OtpErlangLong(1)
        val moduleName = OtpErlangTuple(
            arrayOf(
                OtpErlangAtom("tuple"),
                line,
                OtpErlangList(
                    arrayOf<OtpErlangObject>(
                        OtpErlangTuple(arrayOf(OtpErlangAtom("atom"), line, OtpErlangAtom("shapes"))),
                        OtpErlangTuple(arrayOf(OtpErlangAtom("atom"), line, OtpErlangAtom(name)))
                    )
                )
            )
        )

        return OtpErlangTuple(
            arrayOf(OtpErlangAtom("type"), line, OtpErlangAtom("record"), OtpErlangList(arrayOf<OtpErlangObject>(moduleName)))
        )
    }
}
