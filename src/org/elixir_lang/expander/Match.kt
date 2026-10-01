package org.elixir_lang.expander

import org.elixir_lang.expander.ExState.Prematch.Dependency
import org.elixir_lang.expander.ExState.Prematch.InMatch
import org.elixir_lang.expander.ExState.Write
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.lowering.ElixirAst

/**
 * `elixir_clauses:match/6`: [pattern] expanded as a pattern, after the right side took [before] to [after].
 *
 * @param at the match, which a recursive pattern reports
 */
internal fun match(
    pattern: ElixirAst,
    after: ExState,
    before: ExState,
    env: Env,
    level: ElixirLanguageLevel,
    at: ElixirAst,
): Expansion {
    val callState = after.copy(prematch = InMatch(before.read, after.version, emptyMap(), emptyList()))

    return Expander.expand(pattern, callState, env.copy(context = Env.Context.MATCH), level).then { state, patternEnv ->
        if (isCyclic(state.prematch as InMatch)) {
            Expansion.Unported(at)
        } else {
            Expansion.Expanded(state.copy(prematch = before.prematch), patternEnv.copy(context = env.context))
        }
    }
}

/**
 * `elixir_clauses:parallel_match/4`: each side of a match inside a pattern, left to right, with its own write half, so
 * the variables each side binds feed the cycle check.
 */
internal fun parallelMatch(node: ElixirAst, state: ExState, env: Env, level: ElixirLanguageLevel): Expansion {
    val writes = mutableListOf<Map<Variable, Int>>()

    return mapfold(unpackMatch(node, emptyList()), state, env) { side, sideState, sideEnv ->
        Expander.expand(side, sideState.copy(write = Write.Writing(emptyMap())), sideEnv, level).then { s, e ->
            writes.add((s.write as Write.Writing).vars)
            Expansion.Expanded(s, e)
        }
    }.then { matched, matchedEnv ->
        val prematch = matched.prematch as InMatch
        val stored = storeCycles(writes.asReversed(), prematch)
        val write = when (val outer = state.write) {
            Write.NotWriting -> Write.NotWriting
            is Write.Writing -> Write.Writing(ExState.mergeVars(outer.vars, stored.writes))
        }

        Expansion.Expanded(
            matched.copy(write = write, prematch = prematch.copy(cycles = stored.cycles, skip = stored.skip)),
            matchedEnv
        )
    }
}

/** `elixir_clauses:unpack_match/4`: a chain of matches as its sides, left to right. */
private fun unpackMatch(node: ElixirAst, acc: List<ElixirAst>): List<ElixirAst> {
    if (!isCall(node, "=", 2)) return listOf(node) + acc

    val (left, right) = (node as ElixirAst.Call).arguments!!

    return unpackMatch(left, unpackMatch(right, acc))
}

private class StoredCycles(
    val cycles: Map<Variable, Map<Variable, Dependency>>,
    val skip: List<Set<Variable>>,
    val writes: Map<Variable, Int>,
)

/** `elixir_clauses:store_cycles/3`, over [writes] last side first, as `parallel_match` accumulates them. */
private fun storeCycles(writes: List<Map<Variable, Int>>, prematch: InMatch): StoredCycles {
    var cycles = prematch.cycles
    var skip = prematch.skip
    var acc = emptyMap<Variable, Int>()

    writes.forEachIndexed { index, write ->
        val dependsOn = acc.keys + writes.drop(index + 1).flatMap { it.keys }

        cycles = write.keys.fold(cycles) { accCycles, variable ->
            val current = accCycles[variable]
            val updated = if (current == null) {
                dependsOn.associateWith { Dependency.ONE_SIDE }
            } else {
                current.mapValues { (other, dependency) ->
                    if (other in dependsOn) Dependency.REPEATED else dependency
                } + (dependsOn - current.keys).associateWith { Dependency.ONE_SIDE }
            }

            accCycles + (variable to updated)
        }

        if (dependsOn.size > 1) skip = listOf(dependsOn) + skip

        acc = acc + write
    }

    return StoredCycles(cycles, skip, acc)
}

/** Whether `elixir_clauses:validate_cycles/4` raises `recursive`. */
private fun isCyclic(prematch: InMatch): Boolean {
    var seen = emptyMap<Variable, Boolean>()

    for (current in prematch.cycles.keys.sortedWith(TERM_ORDER)) {
        seen = recurCycles(prematch, current, null, seen) ?: return true
    }

    return false
}

/**
 * `elixir_clauses:recur_cycles/8`: [seen] grown by the variables reachable from [current], or `null` where it raises.
 *
 * @param source the variable [current] was reached from, `null` at the root
 */
private fun recurCycles(
    prematch: InMatch,
    current: Variable,
    source: Variable?,
    seen: Map<Variable, Boolean>,
): Map<Variable, Boolean>? {
    if (current in seen) return seen

    val dependsOn = prematch.cycles.getValue(current)
    if (current in dependsOn) return null

    val once = mutableSetOf<Variable>()
    val always = mutableListOf<Variable>()

    for (group in prematch.skip) {
        if (current in group) {
            for (variable in group) {
                if (variable in dependsOn) once.add(variable) else always.add(variable)
            }
        }
    }

    var accSeen = always.associateWith { false } + seen + (current to true)

    for (variable in dependsOn.keys.sortedWith(TERM_ORDER)) {
        when {
            dependsOn.getValue(variable) == Dependency.REPEATED -> return null
            variable == source || variable in once -> Unit
            // The original `seen`, not `accSeen`, as Elixir's guard reads it.
            seen[variable] == true -> return null
            else -> accSeen = recurCycles(prematch, variable, current, accSeen) ?: return null
        }
    }

    return accSeen
}

/** Erlang's term order on `{name, context}` atoms, which a small map folds in. */
private val TERM_ORDER = compareBy<Variable>({ it.name }, { it.context })
