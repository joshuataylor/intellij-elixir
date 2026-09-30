package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangDouble
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangString
import com.ericsson.otp.erlang.OtpErlangTuple

/**
 * Renders one argument pattern of an Erlang abstract-format clause, plus the clause's guard, as Erlang-like text that
 * does not depend on variable names or annotations: a `= Var` alias whose variable occurs nowhere else is dropped, a
 * variable occurring once in the pattern and guard together is `_`, and the rest are `V1`, `V2`, ... in order of first
 * appearance.
 */
object ClauseKey {
    fun of(pattern: OtpErlangObject, guards: OtpErlangList): String {
        val unaliased = dropAliases(pattern, variables(pattern, guards).groupingBy { it }.eachCount())
        val occurrences = variables(unaliased, guards)
        val counts = occurrences.groupingBy { it }.eachCount()
        val names = occurrences.distinct().filter { counts.getValue(it) > 1 }.withIndex().associate { (index, name) -> name to "V${index + 1}" }
        val renderer = Renderer(names)
        val guardText = guards.elements().joinToString("; ") { guard ->
            (guard as OtpErlangList).elements().joinToString(", ") { renderer.render(it, nested = false) }
        }

        return renderer.render(unaliased, nested = false) + if (guardText.isEmpty()) "" else " when $guardText"
    }

    private fun variables(pattern: OtpErlangObject, guards: OtpErlangList): List<String> =
        mutableListOf<String>().also {
            collectVariables(pattern, it)
            collectVariables(guards, it)
        }

    private fun dropAliases(term: OtpErlangObject, counts: Map<String, Int>): OtpErlangObject {
        fun unused(side: OtpErlangObject): Boolean =
            isVariable(side) && (counts[variableName(side as OtpErlangTuple)] ?: 0) <= 1

        return when {
            term is OtpErlangTuple && tag(term) == "match" -> {
                val left = term.elementAt(2)
                val right = term.elementAt(3)

                when {
                    unused(right) -> dropAliases(left, counts)
                    unused(left) -> dropAliases(right, counts)
                    else -> OtpErlangTuple(arrayOf(term.elementAt(0), term.elementAt(1), dropAliases(left, counts), dropAliases(right, counts)))
                }
            }
            term is OtpErlangTuple -> OtpErlangTuple(term.elements().map { dropAliases(it, counts) }.toTypedArray())
            term is OtpErlangList -> OtpErlangList(term.elements().map { dropAliases(it, counts) }.toTypedArray())
            else -> term
        }
    }

    private fun collectVariables(term: OtpErlangObject, into: MutableList<String>) {
        when {
            term is OtpErlangTuple && isVariable(term) -> variableName(term).takeIf { it != "_" }?.let { into += it }
            term is OtpErlangTuple && isForm(term) -> term.elements().drop(2).forEach { collectVariables(it, into) }
            term is OtpErlangTuple -> term.elements().forEach { collectVariables(it, into) }
            term is OtpErlangList -> term.elements().forEach { collectVariables(it, into) }
        }
    }

    private class Renderer(private val names: Map<String, String>) {
        fun render(term: OtpErlangObject, nested: Boolean): String {
            val form = term as? OtpErlangTuple ?: return any(term)
            if (!isForm(form)) return any(form)

            return when (tag(form)) {
                "var" -> names[variableName(form)] ?: "_"
                "atom" -> atom((form.elementAt(2) as OtpErlangAtom).atomValue())
                "integer", "float", "char" -> raw(form.elementAt(2))
                "string" -> string(form.elementAt(2))
                "nil" -> "[]"
                "cons" -> "[" + consElements(form) + "]"
                "tuple" -> "{" + children(form.elementAt(2)) + "}"
                "match" -> render(form.elementAt(2), true) + "=" + render(form.elementAt(3), true)
                "op" -> op(form, nested)
                "bin" -> "<<" + children(form.elementAt(2)) + ">>"
                "bin_element" -> binElement(form)
                "map" -> if (form.arity() == 3) {
                    "#{" + children(form.elementAt(2)) + "}"
                } else {
                    render(form.elementAt(2), true) + "#{" + children(form.elementAt(3)) + "}"
                }
                "map_field_exact" -> render(form.elementAt(2), true) + " := " + render(form.elementAt(3), true)
                "map_field_assoc" -> render(form.elementAt(2), true) + " => " + render(form.elementAt(3), true)
                "record" -> "#" + raw(form.elementAt(2)) + "{" + children(form.elementAt(3)) + "}"
                "record_field" -> recordField(form)
                "record_index" -> "#" + raw(form.elementAt(2)) + "." + render(form.elementAt(3), true)
                "call" -> render(form.elementAt(2), true) + "(" + children(form.elementAt(3)) + ")"
                "remote" -> render(form.elementAt(2), true) + ":" + render(form.elementAt(3), true)
                else -> raw(form.elementAt(0)) + "(" + form.elements().drop(2).joinToString(",") { any(it) } + ")"
            }
        }

        private fun any(term: OtpErlangObject): String =
            when {
                term is OtpErlangTuple && isForm(term) -> render(term, true)
                term is OtpErlangTuple -> "{" + term.elements().joinToString(",") { any(it) } + "}"
                term is OtpErlangList -> "[" + term.elements().joinToString(",") { any(it) } + "]"
                else -> raw(term)
            }

        private fun children(list: OtpErlangObject): String =
            (list as OtpErlangList).elements().joinToString(",") { render(it, true) }

        private fun consElements(cons: OtpErlangTuple): String {
            val head = render(cons.elementAt(2), true)
            val tail = cons.elementAt(3) as OtpErlangTuple

            return when (tag(tail)) {
                "nil" -> head
                "cons" -> head + "," + consElements(tail)
                else -> head + "|" + render(tail, true)
            }
        }

        private fun op(form: OtpErlangTuple, nested: Boolean): String {
            val operator = (form.elementAt(2) as OtpErlangAtom).atomValue()

            return if (form.arity() == 4) {
                operator + (if (operator.first().isLetter()) " " else "") + render(form.elementAt(3), true)
            } else {
                val text = render(form.elementAt(3), true) + " $operator " + render(form.elementAt(4), true)
                if (nested) "($text)" else text
            }
        }

        private fun binElement(form: OtpErlangTuple): String {
            val size = form.elementAt(3)
            val types = form.elementAt(4)

            return render(form.elementAt(2), true) +
                (if (isDefault(size)) "" else ":" + render(size, true)) +
                (if (isDefault(types)) "" else "/" + (types as OtpErlangList).elements().joinToString("-") { raw(it) })
        }

        /** `#name{field=Pattern}` inside a record pattern; `Expr#name.field` as an expression. */
        private fun recordField(form: OtpErlangTuple): String =
            if (form.arity() == 4) {
                render(form.elementAt(2), true) + "=" + render(form.elementAt(3), true)
            } else {
                render(form.elementAt(2), true) + "#" + raw(form.elementAt(3)) + "." + render(form.elementAt(4), true)
            }

        private fun raw(term: OtpErlangObject): String =
            when (term) {
                is OtpErlangAtom -> atom(term.atomValue())
                is OtpErlangLong -> term.bigIntegerValue().toString()
                is OtpErlangDouble -> term.doubleValue().toString()
                is OtpErlangString -> string(term)
                is OtpErlangList -> "[" + term.elements().joinToString(",") { raw(it) } + "]"
                is OtpErlangTuple -> "{" + term.elements().joinToString(",") { raw(it) } + "}"
                else -> term.toString()
            }

        private fun string(term: OtpErlangObject): String {
            val value = when (term) {
                is OtpErlangString -> term.stringValue()
                // A string of code points above 255 decodes as a list of integers.
                is OtpErlangList -> term.stringValue()
                else -> return raw(term)
            }

            return "\"" + escape(value, '"') + "\""
        }

        private fun isDefault(term: OtpErlangObject): Boolean = (term as? OtpErlangAtom)?.atomValue() == "default"
    }

    private fun tag(term: OtpErlangTuple): String? =
        if (term.arity() >= 2) (term.elementAt(0) as? OtpErlangAtom)?.atomValue() else null

    private fun isVariable(term: OtpErlangObject): Boolean =
        term is OtpErlangTuple && term.arity() == 3 && tag(term) == "var"

    private fun variableName(term: OtpErlangTuple): String = (term.elementAt(2) as OtpErlangAtom).atomValue()

    /** An abstract-format node, as opposed to data such as `{function, Name, Arity}`: element 1 is an annotation. */
    private fun isForm(term: OtpErlangTuple): Boolean {
        val annotation = if (tag(term) != null) term.elementAt(1) else return false

        return annotation is OtpErlangLong ||
            annotation is OtpErlangList ||
            (annotation is OtpErlangTuple && annotation.elements().all { it is OtpErlangLong })
    }

    private fun atom(name: String): String =
        if (UNQUOTED.matches(name) && name !in RESERVED) name else "'" + escape(name, '\'') + "'"

    /** Keeps a key on one manifest line. */
    private fun escape(text: String, quote: Char): String =
        buildString {
            for (char in text) {
                when {
                    char == '\\' || char == quote -> append('\\').append(char)
                    char == '\n' -> append("\\n")
                    char == '\r' -> append("\\r")
                    char == '\t' -> append("\\t")
                    char < ' ' -> append("\\x{").append(char.code.toString(16)).append('}')
                    else -> append(char)
                }
            }
        }

    private val UNQUOTED = Regex("[a-z][a-zA-Z0-9_@]*")

    private val RESERVED = setOf(
        "after", "and", "andalso", "band", "begin", "bnot", "bor", "bsl", "bsr", "bxor", "case", "catch", "cond", "div",
        "else", "end", "fun", "if", "let", "maybe", "not", "of", "or", "orelse", "receive", "rem", "try", "when", "xor",
    )
}
