package org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.v1.definitions.definition

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.Macro
import org.elixir_lang.beam.MacroNameArity
import org.elixir_lang.beam.chunk.debug_info.v1.elixir_erl.v1.definitions.Definition
import org.elixir_lang.toOtpErlangList

/**
 * Elixir compiles `def f(a, b \\ 1)` into `f/2` and a clause `def f(x0), do: super(x0, 1)`. That clause's
 * arguments are variables `elixir_def` generates, so they are renamed after the arguments of the clause `super`
 * calls, when those are all plain variables.
 */
internal object DefaultArgumentClause {
    fun named(clauses: OtpErlangList, definition: Definition): OtpErlangList {
        val clause = clauses.singleOrNull() as? OtpErlangTuple ?: return clauses
        if (clause.arity() != Clause.EXPECTED_ARITY) return clauses

        val arguments = clause.elementAt(1).toOtpErlangList()
        if (arguments.arity() == 0 || !arguments.all { Clause.isGeneratedVariable(it) }) return clauses

        val superArguments = superArguments(clause.elementAt(3)) ?: return clauses
        val targetArguments = targetArguments(definition, superArguments.arity()) ?: return clauses

        val nameByGenerated = arguments.associateWith { argument ->
            superArguments.indexOf(argument)
                .takeIf { it >= 0 }
                ?.let { targetArguments.elementAt(it) }
                ?.takeIf { isNameable(it) }
                ?: return clauses
        }
        if (nameByGenerated.values.map(::variableName).distinct().size != nameByGenerated.size) return clauses

        val renamed = OtpErlangTuple(
            arrayOf(
                clause.elementAt(0),
                OtpErlangList(arguments.map { nameByGenerated.getValue(it) }.toTypedArray()),
                clause.elementAt(2),
                rename(clause.elementAt(3), nameByGenerated)
            )
        )

        return OtpErlangList(arrayOf<OtpErlangObject>(renamed))
    }

    private fun superArguments(block: OtpErlangObject): OtpErlangList? =
        (block as? OtpErlangTuple)
            ?.takeIf { it.arity() == 3 && (it.elementAt(0) as? OtpErlangAtom)?.atomValue() == "super" }
            ?.elementAt(2) as? OtpErlangList

    private fun targetArguments(definition: Definition, arity: Int): OtpErlangList? {
        val macro = definition.macro ?: return null
        val name = definition.name ?: return null
        val target = definition.debugInfo.definitions?.get(MacroNameArity(macro, name, arity)) ?: return null

        return (target.clausesTerm as? OtpErlangList)
            ?.firstOrNull()
            ?.let { it as? OtpErlangTuple }
            ?.takeIf { it.arity() == Clause.EXPECTED_ARITY }
            ?.elementAt(1)
            ?.toOtpErlangList()
    }

    private fun isNameable(term: OtpErlangObject): Boolean =
        Macro.isVariable(term) && !Clause.isGeneratedVariable(term) && variableName(term) != "_"

    private fun variableName(term: OtpErlangObject): String =
        ((term as OtpErlangTuple).elementAt(0) as OtpErlangAtom).atomValue()

    private fun rename(term: OtpErlangObject, nameByGenerated: Map<OtpErlangObject, OtpErlangObject>): OtpErlangObject =
        nameByGenerated[term]
            ?: when (term) {
                is OtpErlangTuple -> OtpErlangTuple(term.elements().map { rename(it, nameByGenerated) }.toTypedArray())
                is OtpErlangList -> {
                    val elements = term.elements().map { rename(it, nameByGenerated) }.toTypedArray()

                    term.lastTail
                        ?.let { OtpErlangList(elements, rename(it, nameByGenerated)) }
                        ?: OtpErlangList(elements)
                }
                else -> term
            }
}
