package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple

internal fun atom(name: String) = OtpErlangAtom(name)

internal fun list(vararg elements: OtpErlangObject) = OtpErlangList(elements)

internal fun list(elements: List<OtpErlangObject>) = OtpErlangList(elements.toTypedArray())

internal fun tuple(vararg elements: OtpErlangObject) = OtpErlangTuple(elements)
