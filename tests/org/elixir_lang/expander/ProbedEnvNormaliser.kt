package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangMap
import com.ericsson.otp.erlang.OtpErlangObject
import org.elixir_lang.NameArity
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.lowering.inspect

/** Puts an [Env] and a probe's `__CALLER__` into one form, each field as `E` holds it, so the two can be compared. */
object ProbedEnvNormaliser {
    /** Fields no probe can match: they name the compile, its file and its position, or carry variables. */
    private val DROPPED = setOf(
        "file", "line", "lexical_tracker", "tracers",
        "vars", "current_vars", "unused_vars", "prematch_vars", "versioned_vars",
    )

    /** Fields that differ with the modules around the case, until `defmodule` is expanded. */
    private val NOT_YET_COMPARED = setOf("module", "context_modules")

    /**
     * [env] in `E`'s form at [level], where [stacktrace] says whether `__STACKTRACE__` can be read: before 1.13 `E`
     * holds that in `contextual_vars`.
     */
    fun projected(env: Env, stacktrace: Boolean, level: ElixirLanguageLevel): Map<String, OtpErlangObject> =
        mapOf(
            "aliases" to list(env.aliases.map { tuple(atom(it.alias), atom(it.module)) }),
            "requires" to list(env.requires.map(::atom)),
            "functions" to imports(env.functions),
            "macros" to imports(env.macros),
            "macro_aliases" to list(
                env.macroAliases.map { tuple(atom(it.alias), tuple(counter(it.counter), atom(it.module))) }
            ),
            "context" to atom(
                when (env.context) {
                    Env.Context.NONE -> "nil"
                    Env.Context.MATCH -> "match"
                    Env.Context.GUARD -> "guard"
                }
            ),
            "context_modules" to list(env.contextModules.map(::atom)),
            "module" to atom(env.module ?: "nil"),
            "function" to (env.function?.let(::nameArity) ?: atom("nil")),
        ) + contextualVars(stacktrace, level) - NOT_YET_COMPARED

    private fun contextualVars(stacktrace: Boolean, level: ElixirLanguageLevel): Map<String, OtpErlangObject> =
        if (level.elixir < CONTEXTUAL_VARS_REMOVED.elixir) {
            mapOf("contextual_vars" to list(if (stacktrace) listOf(atom("__STACKTRACE__")) else emptyList()))
        } else {
            emptyMap()
        }

    /**
     * [env], a probe's `__CALLER__` as a map, less the fields [projected] can't match and the probe's own require. A
     * `catch` or `rescue` inside another's clauses pushes `__STACKTRACE__` onto `contextual_vars` again, which
     * [projected]'s flag can't count, so each atom is kept once.
     */
    fun observed(env: OtpErlangMap, probeModule: String): Map<String, OtpErlangObject> =
        env.keys().associate { (it as OtpErlangAtom).atomValue() to env.get(it) }
            .minus(DROPPED + NOT_YET_COMPARED)
            .mapValues { (field, value) ->
                when (field) {
                    "requires" -> list((value as OtpErlangList).elements().filterNot { it == atom(probeModule) })
                    "contextual_vars" -> list((value as OtpErlangList).elements().distinct())
                    else -> value
                }
            }

    /** [fields] one per line, sorted, so a failed comparison shows which field differs. */
    fun render(fields: Map<String, OtpErlangObject>): String =
        fields.toSortedMap().entries.joinToString("\n") { (field, value) -> "$field: ${inspect(value)}" }

    private fun imports(imports: List<Env.Imports>): OtpErlangList =
        list(imports.map { tuple(atom(it.module), list(it.nameArities.map(::nameArity))) })

    private fun counter(counter: Env.Counter): OtpErlangObject =
        when (counter) {
            is Env.Counter.InModule -> tuple(atom(counter.module), OtpErlangLong(counter.n))
            is Env.Counter.Unique -> OtpErlangLong(counter.n)
        }

    private fun nameArity(nameArity: NameArity) = tuple(atom(nameArity.name), OtpErlangLong(nameArity.arity.toLong()))

    /** `elixir-lang/elixir@739ad53fe` ("Break Macro.Env apart") moved the stacktrace flag out of `E`. */
    private val CONTEXTUAL_VARS_REMOVED = ElixirLanguageLevel.of("1.13.0-rc.0")
}
