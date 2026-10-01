package org.elixir_lang.expander

import com.intellij.openapi.progress.ProgressManager
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.lowering.ElixirAst

/**
 * Elixir's expander (`elixir_expand`), ported clause for clause, over [ElixirAst]. It holds no PSI and takes no lock.
 */
object Expander {
    /** [ast] expanded from [state] and [env] as Elixir at [level] expands it, or where it reaches what isn't ported. */
    fun expand(ast: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
        ProgressManager.checkCanceled()

        return Clause.entries
            .firstOrNull { it.matches(ast, state, env, level) }
            ?.expand(ast, state, env, level)
            ?: Expansion.Unported(ast)
    }
}

internal inline fun Expansion.then(next: (ExState, Env) -> Expansion): Expansion =
    when (this) {
        is Expansion.Expanded -> next(state, env)
        is Expansion.Unported -> this
    }

/** `mapfold/4`: [nodes] in order, each from the state and env the one before it left. */
internal inline fun mapfold(
    nodes: List<ElixirAst>,
    state: ExState,
    env: Env,
    expand: (ElixirAst, ExState, Env) -> Expansion,
): Expansion {
    var accState = state
    var accEnv = env

    for (node in nodes) {
        when (val expansion = expand(node, accState, accEnv)) {
            is Expansion.Expanded -> {
                accState = expansion.state
                accEnv = expansion.env
            }
            is Expansion.Unported -> return expansion
        }
    }

    return Expansion.Expanded(accState, accEnv)
}

/** `elixir_expand:expand_args/3`: outside a pattern, what an argument binds is readable only after the last one. */
internal fun expandArgs(args: List<ElixirAst>, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion =
    when {
        args.size == 1 -> Expander.expand(args.single(), state, env, level)
        env.context == Env.Context.MATCH -> mapfold(args, state, env) { arg, s, e -> Expander.expand(arg, s, e, level) }
        else ->
            argumentScope(state, env) { scope ->
                mapfold(args, scope, env) { arg, s, e -> expandArg(arg, s, state, e, level) }
            }
    }

/**
 * `elixir_expand:expand_list/5`, which takes a `|` as the last element apart.
 *
 * @param expand `expand/3` in a pattern, `expand_arg/3` otherwise
 */
internal inline fun expandList(
    elements: List<ElixirAst>,
    state: ExState,
    env: Env,
    crossinline expand: (ElixirAst, ExState, Env) -> Expansion,
): Expansion =
    mapfold(elements.dropLast(1), state, env) { element, s, e -> expand(element, s, e) }.then { s, e ->
        val last = elements.lastOrNull()

        when {
            last == null -> Expansion.Expanded(s, e)
            isCall(last, "|", 2) ->
                mapfold((last as ElixirAst.Call).arguments!!, s, e) { arg, ss, ee -> expand(arg, ss, ee) }
            else -> expand(last, s, e)
        }
    }

/**
 * Expands [body] between `elixir_env:prepare_write/1` and `close_write/2`, so its bindings become readable only after
 * it.
 */
internal inline fun argumentScope(state: ExState, env: Env, body: (ExState) -> Expansion): Expansion =
    body(state.prepareWrite()).then { s, e -> Expansion.Expanded(s.closeWrite(state), e) }

/** `elixir_expand:expand_arg/3`: an argument reads only what [start], the scope's start, could. */
internal fun expandArg(arg: ElixirAst, acc: ExState, start: ExState, env: Env, level: ElixirLanguageLevel): Expansion =
    if (arg is ElixirAst.Literal) {
        Expansion.Expanded(acc, env)
    } else {
        Expander.expand(arg, acc.resetRead(start), env, level)
    }

/** `{name, meta, args}` with [arity] arguments. */
internal fun isCall(node: ElixirAst, name: String, arity: Int): Boolean =
    node is ElixirAst.Call &&
        (node.callee as? ElixirAst.Literal.Atom)?.name == name &&
        node.arguments?.size == arity

/** `{name, meta, context}` with an atom context: a variable, `_` included. */
internal fun isVariable(node: ElixirAst): Boolean =
    node is ElixirAst.Call && node.callee is ElixirAst.Literal.Atom && node.arguments == null

/**
 * [node] in the shape its expansion has, as far as a ported clause's check reads it: a block of one expression is that
 * expression, and an empty block is `nil`. Every other ported clause keeps its node's shape.
 */
internal fun expandedShape(node: ElixirAst): ElixirAst =
    if (node is ElixirAst.Block) {
        when (node.expressions.size) {
            0 -> ElixirAst.Literal.Atom(node.meta, "nil")
            1 -> expandedShape(node.expressions.single())
            else -> node
        }
    } else {
        node
    }
