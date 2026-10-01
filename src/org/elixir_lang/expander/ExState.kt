package org.elixir_lang.expander

/** `{name, context}`, both as atom text: the context is `nil` for a variable written in source. */
data class Variable(val name: String, val context: String)

/**
 * The part of Elixir's expansion state `#elixir_ex{}` that the ported clauses read and write. On 1.11–1.12 Elixir kept
 * it in `E`; it is kept here on every version.
 *
 * @property read the variables that can be read, each at its version
 * @property version the version the next new variable takes
 */
data class ExState(
    val read: Map<Variable, Int>,
    val write: Write,
    val prematch: Prematch,
    val version: Int,
) {
    /** The write half of `vars`: where bindings go that the expression being expanded can't read yet. */
    sealed interface Write {
        /** `false`: outside an argument scope, a binding is only read. */
        data object NotWriting : Write

        data class Writing(val vars: Map<Variable, Int>) : Write
    }

    sealed interface Prematch {
        data object OutsideMatch : Prematch

        /**
         * Inside a pattern.
         *
         * @property read the variables that could be read before the match's right side, which `^` reads
         * @property version the next version when the match began: a variable at this version or later was already
         *   bound by this match
         * @property cycles from 1.18, each variable bound by one side of a match inside a pattern, mapped to the
         *   variables its sibling sides bind
         * @property skip from 1.18, the groups of variables bound together by one side, which never form a cycle
         *   among themselves
         */
        data class InMatch(
            val read: Map<Variable, Int>,
            val version: Int,
            val cycles: Map<Variable, Map<Variable, Dependency>>,
            val skip: List<Set<Variable>>,
        ) : Prematch

        /** Whether a sibling binds the variable on one side of the match, or on more than one. */
        enum class Dependency { ONE_SIDE, REPEATED }
    }

    /** `elixir_env:prepare_write/1`. */
    fun prepareWrite(): ExState = copy(write = Write.Writing(read))

    /** `elixir_env:reset_read/2`: [start]'s read half, keeping this state's writes. */
    fun resetRead(start: ExState): ExState = copy(read = start.read)

    /** `elixir_env:close_write/2`: the writes become readable, and merge into [upper]'s write half when it has one. */
    fun closeWrite(upper: ExState): ExState {
        val writes = (write as Write.Writing).vars

        return copy(
            read = writes,
            write = when (val upperWrite = upper.write) {
                Write.NotWriting -> Write.NotWriting
                is Write.Writing -> Write.Writing(mergeVars(upperWrite.vars, writes))
            }
        )
    }

    companion object {
        val EMPTY = ExState(emptyMap(), Write.NotWriting, Prematch.OutsideMatch, 0)

        /** `elixir_env:merge_vars/2`: the union, keeping the higher version of a variable in both. */
        fun mergeVars(into: Map<Variable, Int>, from: Map<Variable, Int>): Map<Variable, Int> =
            from.entries.fold(into) { acc, (variable, version) ->
                if ((acc[variable] ?: Int.MIN_VALUE) >= version) acc else acc + (variable to version)
            }
    }
}
