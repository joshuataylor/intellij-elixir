package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangMap
import com.ericsson.otp.erlang.OtpErlangTuple

/**
 * Variables as equivalence classes: two variables are in one class when they are at the same version. Classes, unlike
 * versions, don't depend on the order versions are allocated in, which differs between Elixir releases.
 */
object VariableClasses {
    /**
     * The variables a probe's `__CALLER__` holds: `versioned_vars` from 1.13, and before it the read half of
     * `current_vars`.
     */
    fun observed(env: OtpErlangMap): Map<Variable, Int> {
        val vars = env.get(OtpErlangAtom("versioned_vars"))
            ?: (env.get(OtpErlangAtom("current_vars")) as OtpErlangTuple).elementAt(0)

        return (vars as OtpErlangMap).entrySet().associate { (key, version) ->
            val (name, context) = (key as OtpErlangTuple).elements().map { (it as OtpErlangAtom).atomValue() }

            Variable(name, context) to (version as OtpErlangLong).intValue()
        }
    }

    /**
     * One line per step: each variable, sorted by name and then context, with its class, numbered in the order the
     * classes first appear across [steps].
     */
    fun canonical(steps: List<Map<Variable, Int>>): List<String> {
        val classes = mutableMapOf<Int, Int>()

        return steps.map { step ->
            step.entries
                .sortedWith(compareBy({ it.key.name }, { it.key.context }))
                .joinToString(" ") { (variable, version) ->
                    "${variable.name}/${variable.context}=${classes.getOrPut(version) { classes.size }}"
                }
        }
    }
}
