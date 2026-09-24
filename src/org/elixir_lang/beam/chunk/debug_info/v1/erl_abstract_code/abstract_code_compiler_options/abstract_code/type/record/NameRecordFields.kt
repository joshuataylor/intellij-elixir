package org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.type.record

import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.AbstractCode
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.AbstractCode.ifTag
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Atom
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Scope
import org.elixir_lang.beam.chunk.debug_info.v1.erl_abstract_code.abstract_code_compiler_options.abstract_code.Record as AbstractCodeRecord
import org.elixir_lang.beam.term.inspect
import org.elixir_lang.code.Identifier.inspectAsFunction

object NameRecordFields {
    fun toString(term: OtpErlangObject): String =
            when (term) {
                is OtpErlangList -> toString(term)
                else -> AbstractCode.unknown("name_record_fields", "type record", term)
            }

    private fun nameString(nameRecordFields: OtpErlangList): String =
            toName(nameRecordFields)
                    ?.let { nameToString(it) }
                    ?: AbstractCode.missing("name", "type record name", nameRecordFields)

    private fun nameToString(name: OtpErlangObject): String? =
            ifRemoteNameToString(name) ?: Atom.toElixirAtom(name)?.let { AbstractCodeRecord.nameToString(it) }

    /** OTP 29's `#Module:Name{}` names a native record by `{tuple, Anno, [Module, Name]}`. */
    private fun ifRemoteNameToString(name: OtpErlangObject): String? =
            ifTag(name, "tuple") { tuple ->
                val moduleName = (tuple.elementAt(2) as? OtpErlangList)?.takeIf { it.arity() == 2 }
                val module = Atom.toElixirAtom(moduleName?.elementAt(0))
                val recordName = Atom.toElixirAtom(moduleName?.elementAt(1))

                if (module != null && recordName != null) {
                    "${inspect(module)}.${inspectAsFunction(recordName, local = false)}"
                } else {
                    AbstractCode.unknown("name", "type record remote name", tuple)
                }
            }

    private fun recordFieldsString(nameRecordFields: OtpErlangList) =
            recordFieldsToString(toRecordFields(nameRecordFields))

    private fun recordFieldsToString(recordFields: List<OtpErlangObject>): String =
            recordFields.joinToString(", ") {
                AbstractCode.toMacroStringDeclaredScope(it, Scope.EMPTY).macroString.group().string
            }

    private fun toString(nameRecordFields: OtpErlangList): String {
        val nameString = nameString(nameRecordFields)
        val recordFieldsString = recordFieldsString(nameRecordFields)

        return "$nameString($recordFieldsString)"
    }

    private fun toName(nameRecordFields: OtpErlangList): OtpErlangObject? = nameRecordFields.elementAt(0)
    private fun toRecordFields(nameRecordFields: OtpErlangList): List<OtpErlangObject> = nameRecordFields.elements().drop(1)
}
