package org.elixir_lang.psi.scope

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.visitedElementSet

/**
 * What a [CallableTable] build records of the walk's structure. A walk site that gates on the entrance, or that treats
 * a consumer's `false` other than by stopping and passing it up, marks the state it hands its children with a [Group]
 * carrying the site's own behaviour, so a replay can re-run it. Outside a build the state carries no [RECORDER] and
 * [enter] returns it unchanged.
 */
object Recording {
    /**
     * One entry of a walk site into its children, as that site declares it.
     *
     * @property label for reading a table in a debugger; nothing branches on it.
     * @property stops a `false` from a child ends this group's iteration.
     * @property absorbs this group answers `true` to its parent whatever its children answered.
     * @property answersLast this group answers its last child's answer; otherwise `false` if any child answered `false`.
     * @property gate the site's own test for walking [call] at all, on the replaying state.
     * @property childGate the site's own test for each child it iterates, on the replaying state.
     * @property reach the site's own reach step, re-run on the replaying state.
     * @property fallThrough when [gate] fails, the dispatcher goes on to its later arms for [call].
     * @property visited the visited set the site entered with, for a [fallThrough] re-dispatch.
     */
    class Group(
        val label: String,
        val call: PsiElement,
        val parent: Group?,
        val stops: Boolean,
        val absorbs: Boolean,
        val answersLast: Boolean,
        val gate: ((ResolveState) -> Boolean)?,
        val childGate: ((ResolveState, PsiElement) -> Boolean)?,
        val reach: ((ResolveState) -> ResolveState)?,
        val fallThrough: Boolean,
        val visited: Set<PsiElement>
    ) {
        /** Outermost first. */
        val chain: List<Group> by lazy { generateSequence(this) { it.parent }.toList().asReversed() }

        /** The groups recorded directly inside this one; a group with no node for a name still counts. */
        var children = 0
            private set

        /** This group's place among its parent's [children]. */
        val ordinal: Int = parent?.let { it.children++ } ?: 0
    }

    /** Why a call is walked live at replay rather than tabled. */
    enum class LiveKind {
        /** A `do`/keyword macro the walk doesn't know: it contributes only when the entrance is inside it. */
        UNKNOWN_MACRO,

        /** An Ecto query: it contributes through the state its own walk sets. */
        QUERY,

        /** The modular arm's implicit imports, whose scope and `keepProcessing()` belong to the replaying walk. */
        IMPLICIT_IMPORTS,
    }

    /** What the site does for a live node at replay, run on the replaying processor. */
    typealias Rerun = CallDefinitionClause.(call: Call, state: ResolveState) -> Boolean

    interface Recorder {
        fun live(call: Call, kind: LiveKind, state: ResolveState, rerun: Rerun)
    }

    val RECORDER = Key<Recorder>("Recording.RECORDER")
    val GROUP = Key<Group>("Recording.GROUP")

    fun isRecording(state: ResolveState): Boolean = state.get(RECORDER) != null

    fun enter(
        state: ResolveState,
        label: String,
        call: PsiElement,
        stops: Boolean,
        absorbs: Boolean,
        answersLast: Boolean = false,
        gate: ((ResolveState) -> Boolean)? = null,
        childGate: ((ResolveState, PsiElement) -> Boolean)? = null,
        reach: ((ResolveState) -> ResolveState)? = null,
        fallThrough: Boolean = false
    ): ResolveState =
        if (isRecording(state)) {
            state.put(
                GROUP,
                Group(
                    label, call, state.get(GROUP), stops, absorbs, answersLast, gate, childGate, reach, fallThrough,
                    state.visitedElementSet()
                )
            )
        } else {
            state
        }

    /** Records [call] as walked live at replay; `true` when recording, and the arm's own walk is then skipped. */
    fun live(state: ResolveState, call: Call, kind: LiveKind, rerun: Rerun): Boolean =
        state.get(RECORDER)?.let { it.live(call, kind, state, rerun); true } ?: false
}
