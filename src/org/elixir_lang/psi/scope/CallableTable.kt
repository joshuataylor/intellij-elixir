package org.elixir_lang.psi.scope

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveState
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.isAncestor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.beam.psi.CallDefinition as BeamCallDefinition
import org.elixir_lang.declaration.Form
import org.elixir_lang.psi.AtUnqualifiedNoParenthesesCall
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE
import org.elixir_lang.psi.scope.call_definition_clause.Declarations
import org.elixir_lang.psi.visitedElementSet
import org.jetbrains.annotations.TestOnly
import java.util.TreeMap

/**
 * What the call walk's own dispatch reaches from a module's calls, recorded once by running that dispatch with a
 * recording processor, and replayed per resolve with the sites' entrance-dependent gates re-run, so a resolve costs a
 * lookup by name instead of a walk of the whole module.
 */
class CallableTable private constructor(nodes: List<Node>) {
    sealed class Node {
        /** The node's place in the walk's own order. */
        abstract val order: Int
        abstract val group: Recording.Group?
        abstract val visited: Set<PsiElement>
        abstract val canonical: String?
    }

    /** A declaring call, or a compiled definition, with the atoms of the names it declares. */
    class Leaf(
        override val order: Int,
        val element: PsiElement,
        override val group: Recording.Group?,
        override val visited: Set<PsiElement>,
        override val canonical: String?,
        val names: List<String>
    ) : Node()

    /** A call the walk does not record, but re-runs at replay through [rerun]. */
    class Live(
        override val order: Int,
        val call: Call,
        val kind: Recording.LiveKind,
        val rerun: Recording.Rerun,
        override val group: Recording.Group?,
        override val visited: Set<PsiElement>,
        override val canonical: String?
    ) : Node()

    val leaves: List<Leaf> = nodes.filterIsInstance<Leaf>()
    private val lives: List<Live> = nodes.filterIsInstance<Live>()
    private val queries: List<Live> = lives.filter { it.kind == Recording.LiveKind.QUERY }

    /** In recorded order. */
    val implicitImports: List<Live> = lives.filter { it.kind == Recording.LiveKind.IMPLICIT_IMPORTS }

    private val byName = TreeMap<String, MutableList<Leaf>>().also { byName ->
        for (leaf in leaves) for (name in leaf.names.distinct()) byName.getOrPut(name) { mutableListOf() }.add(leaf)
    }

    /** Each file's, by start offset, for the lookup of the one holding the entrance. */
    private val unknownMacrosByFile: Map<PsiFile, List<Live>> =
        lives
            .filter { it.kind == Recording.LiveKind.UNKNOWN_MACRO }
            .groupBy { it.call.containingFile }
            .mapValues { (_, macros) -> macros.sortedBy { it.call.textRange.startOffset } }

    /** The leaves declaring a name [prefix] starts, in walk order. */
    fun leavesStartingWith(prefix: String): List<Leaf> =
        byName.subMap(prefix, true, prefix + Char.MAX_VALUE, true).values.flatten().distinct().sortedBy { it.order }

    /**
     * The live nodes a walk from [entrance] would do anything with: every query, and the unknown macro of [entrance]'s
     * file that starts nearest before it, when that one holds [entrance], since `executeOnUnknownMacroCall` walks only
     * an unknown macro holding the entrance.
     */
    fun livesFor(entrance: PsiElement?): List<Live> {
        repeat(queries.size) { WalkProbe.count(WalkProbe.Counter.LIVE_EXAMINED) }
        entrance ?: return queries
        WalkProbe.count(WalkProbe.Counter.LIVE_EXAMINED)

        val unknownMacros = unknownMacrosByFile[entrance.containingFile] ?: return queries
        val offset = entrance.textRange.startOffset
        val index = unknownMacros.binarySearchBy(offset) { it.call.textRange.startOffset }.let { if (it >= 0) it else -it - 2 }

        return queries + listOfNotNull(unknownMacros.getOrNull(index)?.takeIf { it.call.isAncestor(entrance, strict = true) })
    }

    companion object {
        /** Off, every module is walked live, as the walk the table replays. */
        @JvmStatic
        @set:TestOnly
        var enabled = true

        private val KEY = Key<CachedValue<CallableTable>>("CallableTable")

        private val buildingOnThisThread: ThreadLocal<MutableSet<Call>> = ThreadLocal.withInitial { mutableSetOf() }

        /**
         * `null` while [modular]'s own table is being built on this thread, while dumb, or when building it needs a
         * resolve already under way on this thread.
         */
        @RequiresReadLock
        fun ofOrNull(modular: Call): CallableTable? {
            ThreadingAssertions.assertReadAccess()

            return when {
                !enabled || DumbService.isDumb(modular.project) -> null
                modular in buildingOnThisThread.get() -> {
                    WalkProbe.count(WalkProbe.Counter.TABLE_REENTRY)
                    null
                }
                else -> try {
                    CachedValuesManager.getCachedValue(modular, KEY) {
                        CachedValueProvider.Result.create(build(modular), PsiModificationTracker.MODIFICATION_COUNT)
                    }
                } catch (_: AbandonedBuild) {
                    null
                }
            }
        }

        /**
         * Ends the innermost table being built on this thread, if any: the build needs a resolve that is already under
         * way, which would answer without the call it is resolving.
         */
        internal fun abandonBuild() {
            if (buildingOnThisThread.get().isNotEmpty()) {
                throw AbandonedBuild()
            }
        }

        /** Unwinds an abandoned build to [ofOrNull], which keeps nothing. */
        private class AbandonedBuild : RuntimeException(null, null, false, false), ControlFlowException

        private fun build(modular: Call): CallableTable {
            val building = buildingOnThisThread.get()
            building.add(modular)

            try {
                WalkProbe.count(WalkProbe.Counter.TABLE_BUILD)
                val recorder = Recorder()
                // An entrance that no call in the module holds, as `Import` and `Use` compare it with `isAncestor`.
                val state = ResolveState.initial()
                    .put(ENTRANCE, modular.containingFile)
                    .put(Recording.RECORDER, recorder)

                for (child in CallDefinitionClause.modularCallsToExecute(modular)) {
                    ProgressManager.checkCanceled()
                    WalkProbe.cancelPoint(WalkProbe.CancelPoint.TABLE_BUILD)
                    recorder.execute(child, state)
                }

                return CallableTable(recorder.nodes)
            } finally {
                building.remove(modular)
            }
        }

        /** The atoms a declaration declares, as the call's own declarations name them; none for a name with no atom. */
        private fun names(form: Form?, element: PsiElement, state: ResolveState): List<String> =
            if (form == null) {
                listOfNotNull((element as? BeamCallDefinition)?.declaration()?.name)
            } else {
                Declarations.of(form, element as Call, state).mapNotNull { it.declaration?.name }
            }

        @TestOnly
        fun buildingOnThisThread(): Set<Call> = buildingOnThisThread.get().toSet()

        /** Whether [modular]'s table is cached and up to date, without building one. */
        @TestOnly
        fun isCached(modular: Call): Boolean =
            // `getCachedValue(holder, key, provider)` keeps a parameterized cached value under the key.
            (modular.getUserData(KEY as Key<*>) as? ParameterizedCachedValue<*, *>)?.hasUpToDateValue() == true
    }

    /** Records every node the dispatch reaches, and answers `true` so the build reaches everything. */
    private class Recorder : CallDefinitionClause(), Recording.Recorder {
        val nodes = mutableListOf<Node>()

        override fun executeOnCallDefinitionClause(element: Call, state: ResolveState): Boolean =
            leaf(element, Form.CLAUSE, state)

        override fun executeOnCallback(element: AtUnqualifiedNoParenthesesCall<*>, state: ResolveState): Boolean =
            leaf(element, Form.CALLBACK, state)

        override fun executeOnDelegation(element: Call, state: ResolveState): Boolean =
            leaf(element, Form.DELEGATION, state)

        override fun executeOnEExFunctionFrom(element: Call, state: ResolveState): Boolean =
            leaf(element, Form.EEX_FUNCTION_FROM, state)

        override fun executeOnException(element: Call, state: ResolveState): Boolean =
            leaf(element, Form.EXCEPTION, state)

        override fun executeOnMixGeneratorEmbed(element: Call, state: ResolveState): Boolean =
            leaf(element, Form.GENERATOR_EMBED, state)

        override fun execute(element: BeamCallDefinition, state: ResolveState): Boolean = leaf(element, null, state)

        override fun keepProcessing(): Boolean = true

        override fun live(call: Call, kind: Recording.LiveKind, state: ResolveState, rerun: Recording.Rerun) {
            nodes += Live(
                nodes.size, call, kind, rerun, state.get(Recording.GROUP), state.visitedElementSet(),
                state.get(MODULAR_CANONICAL_NAME)
            )
        }

        private fun leaf(element: PsiElement, form: Form?, state: ResolveState): Boolean {
            nodes += Leaf(
                nodes.size, element, state.get(Recording.GROUP), state.visitedElementSet(),
                state.get(MODULAR_CANONICAL_NAME), names(form, element, state)
            )

            return true
        }
    }
}
