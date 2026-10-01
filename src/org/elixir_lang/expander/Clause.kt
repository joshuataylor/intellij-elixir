package org.elixir_lang.expander

import org.elixir_lang.expander.ExState.Prematch.InMatch
import org.elixir_lang.expander.ExState.Write
import org.elixir_lang.language_level.ElixirLanguageFeature.PARALLEL_MATCH
import org.elixir_lang.language_level.ElixirLanguageFeature.REPEATED_PATTERN_VARIABLE_WRITTEN_AT_NEXT_VERSION
import org.elixir_lang.language_level.ElixirLanguageFeature.UNDERSCORE_TAKES_VERSION
import org.elixir_lang.language_level.ElixirLanguageFeature.ZERO_FLOAT_MATCH_WARNS
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.lowering.ElixirAst

/**
 * The ported clauses of Elixir's expander, in the order Elixir tries them: [Expander] takes the first entry that
 * [matches]. Each entry declares the heads of Elixir's expander it ports, as the leg manifests render them, and gives
 * [Expansion.Unported] for each error branch.
 */
internal enum class Clause(vararg val heads: Head) {
    /** `=` inside a pattern from 1.18: `elixir_clauses:parallel_match/4`. */
    MATCH_IN_PATTERN(
        expandHead("{'=',_,[_,_]}"),
        Head("elixir_clauses", "unpack_match", 1, "{'=',_,[_,_]}"),
        Head("elixir_clauses", "unpack_match", 1, "_"),
    ) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isCall(node, "=", 2) && env.context == Env.Context.MATCH && PARALLEL_MATCH.isSufficient(level)

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            parallelMatch(node, state, env, level)
    },

    MATCH(expandHead("{'=',_,[_,_]}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isCall(node, "=", 2)

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
            val (left, right) = (node as ElixirAst.Call).arguments!!

            return when (env.context) {
                // `assert_no_guard_scope`
                Env.Context.GUARD -> Expansion.Unported(node)
                // Before 1.18 Elixir expands the right side first here; left first allocates versions as 1.18 does.
                Env.Context.MATCH ->
                    mapfold(listOf(left, right), state, env) { side, s, e -> Expander.expand(side, s, e, level) }
                Env.Context.NONE ->
                    Expander.expand(right, state, env, level).then { after, rightEnv ->
                        match(left, after, state, rightEnv, level, node)
                    }
            }
        }
    },

    TUPLE(expandHead("{'{}',_,_}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Tuple && node.elements.size != 2

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            expandArgs((node as ElixirAst.Tuple).elements, state, env, level)
    },

    MAP(expandHead("{'%{}',_,_}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Call &&
                (node.callee as? ElixirAst.Literal.Atom)?.name == "%{}" &&
                node.arguments != null

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            expandMap(node as ElixirAst.Call, state, env, level)
    },

    EMPTY_BLOCK(expandHead("{'__block__',_,[]}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Block && node.expressions.isEmpty()

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expansion.Expanded(state, env)
    },

    SINGLE_BLOCK(expandHead("{'__block__',_,[_]}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Block && node.expressions.size == 1

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expander.expand((node as ElixirAst.Block).expressions.single(), state, env, level)
    },

    /** `expand_block/5`. */
    BLOCK(expandHead("{'__block__',_,V1} when is_list(V1)")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Block

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
            val expressions = (node as ElixirAst.Block).expressions

            return mapfold(expressions, state, env) { expression, s, e ->
                if (expression !== expressions.last() && isDiscardedFor(expression)) {
                    Expansion.Unported(expression)
                } else {
                    Expander.expand(expression, s, e, level)
                }
            }
        }
    },

    /**
     * `__MODULE__`, `__DIR__`, `__CALLER__`, `__STACKTRACE__` and `__ENV__`, which have a variable's shape and clauses
     * of their own ahead of the variables'.
     */
    ENVIRONMENT_NAME(*ENVIRONMENT_NAMES.map { expandHead("{'$it',_,V1} when is_atom(V1)") }.toTypedArray()) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isVariable(node) && ((node as ElixirAst.Call).callee as ElixirAst.Literal.Atom).name in ENVIRONMENT_NAMES

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expansion.Unported(node)
    },

    /** `^` inside a pattern, which reads the variables from before the match. */
    PIN(expandHead("{'^',_,[_]}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isCall(node, "^", 1) && state.prematch is InMatch

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
            val arg = (node as ElixirAst.Call).arguments!!.single()
            val prematch = state.prematch as InMatch
            val pinState = state.copy(read = prematch.read, prematch = ExState.Prematch.OutsideMatch)

            return Expander.expand(arg, pinState, env.copy(context = Env.Context.NONE), level).then { _, _ ->
                // `invalid_arg_for_pin`
                if (isVariable(expandedShape(arg))) Expansion.Expanded(state, env) else Expansion.Unported(node)
            }
        }
    },

    /** `pin_outside_of_match`. */
    PIN_OUTSIDE_PATTERN(expandHead("{'^',_,[_]}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isCall(node, "^", 1)

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expansion.Unported(node)
    },

    UNDERSCORE(expandHead("{'_',_,V1} when is_atom(V1)")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isUnderscore(node)

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            if (env.context == Env.Context.MATCH) {
                val version = if (UNDERSCORE_TAKES_VERSION.isSufficient(level)) state.version + 1 else state.version

                Expansion.Expanded(state.copy(version = version), env)
            } else {
                // `unbound_underscore`
                Expansion.Unported(node)
            }
    },

    VARIABLE_IN_PATTERN(expandHead("{V1,_,V2} when is_atom(V1), is_atom(V2)")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isVariable(node) && env.context == Env.Context.MATCH

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
            val variable = variable(node)
            val bound = state.read[variable]

            return if (bound != null && bound >= (state.prematch as InMatch).version) {
                val written = if (REPEATED_PATTERN_VARIABLE_WRITTEN_AT_NEXT_VERSION.isSufficient(level)) {
                    state.version
                } else {
                    bound
                }

                Expansion.Expanded(state.copy(write = state.write.plus(variable, written)), env)
            } else {
                Expansion.Expanded(
                    state.copy(
                        read = state.read + (variable to state.version),
                        write = state.write.plus(variable, state.version),
                        version = state.version + 1,
                    ),
                    env
                )
            }
        }
    },

    VARIABLE(expandHead("{V1,_,V2} when is_atom(V1), is_atom(V2)")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            isVariable(node)

        // An undefined variable becomes a local call or an error, neither of which is ported.
        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            if (variable(node) in state.read) Expansion.Expanded(state, env) else Expansion.Unported(node)
    },

    PAIR(expandHead("{_,_}")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Tuple && node.elements.size == 2

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            expandArgs((node as ElixirAst.Tuple).elements, state, env, level)
    },

    LIST_IN_PATTERN(expandHead("V1 when is_list(V1)"), *EXPAND_LIST) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.ListNode && env.context == Env.Context.MATCH

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            expandList((node as ElixirAst.ListNode).elements, state, env) { element, s, e ->
                Expander.expand(element, s, e, level)
            }
    },

    LIST(expandHead("V1 when is_list(V1)"), *EXPAND_LIST) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.ListNode

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            argumentScope(state, env) { scope ->
                expandList((node as ElixirAst.ListNode).elements, scope, env) { element, s, e ->
                    expandArg(element, s, state, e, level)
                }
            }
    },

    /** From 1.16 Elixir warns of `0.0` in a pattern, which isn't ported. */
    ZERO_FLOAT_IN_PATTERN(expandHead("V1 when is_float(V1), V1 == 0.0")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Literal.Float &&
                node.value == 0.0 &&
                env.context == Env.Context.MATCH &&
                ZERO_FLOAT_MATCH_WARNS.isSufficient(level)

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expansion.Unported(node)
    },

    LITERAL(expandHead("V1 when is_number(V1); is_atom(V1); is_binary(V1)")) {
        override fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            node is ElixirAst.Literal

        override fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel) =
            Expansion.Expanded(state, env)
    };

    abstract fun matches(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Boolean

    abstract fun expand(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion

    /** `{module, function, argument, pattern}`: one line of a leg's `expander-clauses.txt`, less its count. */
    data class Head(val module: String, val function: String, val argument: Int, val pattern: String) {
        override fun toString() = "$module $function $argument $pattern"
    }
}

private fun expandHead(pattern: String) = Clause.Head("elixir_expand", "expand", 1, pattern)

private val ENVIRONMENT_NAMES = listOf("__MODULE__", "__DIR__", "__CALLER__", "__STACKTRACE__", "__ENV__")

private val EXPAND_LIST = arrayOf(
    Clause.Head("elixir_expand", "expand_list", 1, "[]"),
    Clause.Head("elixir_expand", "expand_list", 1, "[_|_]"),
    Clause.Head("elixir_expand", "expand_list", 1, "[{'|',_,[_,_]}]"),
)

private fun variable(node: ElixirAst) =
    Variable(((node as ElixirAst.Call).callee as ElixirAst.Literal.Atom).name, "nil")

private fun Write.plus(variable: Variable, version: Int): Write =
    when (this) {
        Write.NotWriting -> this
        is Write.Writing -> Write.Writing(vars + (variable to version))
    }

/** `{for, _, [_ | _]}`, or `_ = ` one, which `expand_block/5` sends to `expand_for/4`. */
private fun isDiscardedFor(node: ElixirAst): Boolean {
    fun isFor(candidate: ElixirAst) =
        candidate is ElixirAst.Call &&
            (candidate.callee as? ElixirAst.Literal.Atom)?.name == "for" &&
            !candidate.arguments.isNullOrEmpty()

    if (isFor(node)) return true
    if (!isCall(node, "=", 2)) return false

    val (left, right) = (node as ElixirAst.Call).arguments!!

    return isUnderscore(left) && isFor(right)
}

private fun isUnderscore(node: ElixirAst) =
    isVariable(node) && ((node as ElixirAst.Call).callee as ElixirAst.Literal.Atom).name == "_"
