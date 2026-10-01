package org.elixir_lang.expander

import com.ericsson.otp.erlang.OtpErlangObject
import org.elixir_lang.language_level.ElixirLanguageFeature.PIN_IN_MAP_KEY_PATTERN
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.lowering.ElixirAst

/** `elixir_map:expand_map/4`. */
internal fun expandMap(node: ElixirAst.Call, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
    val args = node.arguments!!
    val update = args.singleOrNull()?.takeIf { isCall(it, "|", 2) } as ElixirAst.Call?

    return when {
        update == null -> expandArgs(args, state, env, level).then { s, e -> validated(node, args, s, e, level) }
        // `update_syntax_in_wrong_context`
        env.context != Env.Context.NONE -> Expansion.Unported(node)
        else -> {
            val (map, pairs) = update.arguments!!

            if (pairs is ElixirAst.ListNode) {
                expandArgs(listOf(map) + pairs.elements, state, env, level).then { s, e ->
                    validated(node, pairs.elements, s, e, level)
                }
            } else {
                Expansion.Unported(node)
            }
        }
    }
}

private fun validated(node: ElixirAst, kv: List<ElixirAst>, state: ExState, env: Env, level: ElixirLanguageLevel) =
    if (isValidKv(kv, env.context, level)) Expansion.Expanded(state, env) else Expansion.Unported(node)

/**
 * Whether `elixir_map:validate_kv/4` passes without an error: each argument is a pair, and in a pattern each key is
 * one a pattern may have, and no literal key repeats.
 */
private fun isValidKv(args: List<ElixirAst>, context: Env.Context, level: ElixirLanguageLevel): Boolean {
    val used = mutableSetOf<OtpErlangObject>()

    for (arg in args) {
        // `not_kv_pair`
        val pair = expandedShape(arg) as? ElixirAst.Tuple ?: return false
        if (pair.elements.size != 2) return false

        val key = expandedShape(pair.elements[0])

        if (!PIN_IN_MAP_KEY_PATTERN.isSufficient(level) && isCall(key, "^", 1)) continue

        // `invalid_variable_in_map_key_match`, and before 1.14 `invalid_pin_in_map_key_match`
        if (context == Env.Context.MATCH && !isValidMatchKey(key, level)) return false

        // `repeated_key`, which outside a pattern only warns
        if (isLiteral(key) && !used.add(literalShape(key).toOtp()) && context == Env.Context.MATCH) return false
    }

    return true
}

/** `elixir_map:validate_match_key/3`, less the 1.15 `::` clause: a key with `::` is unported before it is checked. */
private fun isValidMatchKey(node: ElixirAst, level: ElixirLanguageLevel): Boolean {
    val key = expandedShape(node)

    return when {
        isCall(key, "^", 1) && isVariable((key as ElixirAst.Call).arguments!!.single()) ->
            PIN_IN_MAP_KEY_PATTERN.isSufficient(level)
        isVariable(key) -> false
        key is ElixirAst.Call && (key.callee as? ElixirAst.Literal.Atom)?.name == "%{}" -> true
        key is ElixirAst.Call ->
            isValidMatchKey(key.callee, level) && key.arguments.orEmpty().all { isValidMatchKey(it, level) }
        key is ElixirAst.Alias -> key.segments.all { isValidMatchKey(it, level) }
        key is ElixirAst.Block -> key.expressions.all { isValidMatchKey(it, level) }
        key is ElixirAst.Tuple -> key.elements.all { isValidMatchKey(it, level) }
        key is ElixirAst.ListNode -> key.elements.all { isValidMatchKey(it, level) }
        else -> true
    }
}

/** `elixir_map:is_literal/1`, on the expanded key. */
private fun isLiteral(node: ElixirAst): Boolean {
    val key = expandedShape(node)

    return when {
        key.hasMetadata() -> false
        key is ElixirAst.Tuple -> key.elements.all(::isLiteral)
        key is ElixirAst.ListNode -> key.elements.all(::isLiteral)
        else -> true
    }
}

/** A literal key as its expansion is, at every depth, so equal keys give equal terms. */
private fun literalShape(node: ElixirAst): ElixirAst =
    when (val key = expandedShape(node)) {
        is ElixirAst.Tuple -> ElixirAst.Tuple(key.meta, key.elements.map(::literalShape))
        is ElixirAst.ListNode -> ElixirAst.ListNode(key.meta, key.elements.map(::literalShape))
        else -> key
    }
