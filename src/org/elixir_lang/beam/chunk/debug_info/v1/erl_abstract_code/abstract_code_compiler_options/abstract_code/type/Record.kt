package org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.type

import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.AbstractCode
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Type.ifSubtypeTo
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.type.record.NameRecordFields

object Record {
    fun ifToString(type: OtpErlangTuple): String? = ifSubtypeTo(type, SUBTYPE) { toString(type) }

    private const val SUBTYPE = "record"

    private fun toString(type: OtpErlangTuple) =
            toNameRecordFields(type)
                    ?.let { nameRecordFields ->
                        // OTP 29's built-in `record()`, any native record, has no name.
                        if (nameRecordFields is OtpErlangList && nameRecordFields.arity() == 0) {
                            "$SUBTYPE()"
                        } else {
                            NameRecordFields.toString(nameRecordFields)
                        }
                    }
                    ?: AbstractCode.missing("name_record_fields", "type $SUBTYPE name and record fields", type)

    private fun toNameRecordFields(type: OtpErlangTuple): OtpErlangObject? = type.elementAt(3)
}
