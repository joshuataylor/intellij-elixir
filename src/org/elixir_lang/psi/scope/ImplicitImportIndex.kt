package org.elixir_lang.psi.scope

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.ResolveState
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.declaration.Form
import org.elixir_lang.psi.Modular
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.scope.call_definition_clause.Declarations
import org.jetbrains.annotations.TestOnly
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap

/**
 * The clauses an implicit `import` of a source module brings in, by the atom each declares. Building it resolves
 * nothing, so a resolve inside the module itself can reach it while it is built.
 */
class ImplicitImportIndex private constructor(
    /** Every clause, named or not, in `Modular.callDefinitionClauseCallSequence` order. */
    val clauses: List<Call>,
    /** Each name's positions in [clauses], ascending. */
    private val positionsByName: NavigableMap<String, out List<Int>>
) {
    /** The clauses declaring a name [prefix] starts, in [clauses] order; one named `unquote(name)` declares none. */
    fun startingWith(prefix: String): List<Call> =
        positionsByName
            .subMap(prefix, true, prefix + Char.MAX_VALUE, true)
            .values
            .flatten()
            .sorted()
            .map(clauses::get)

    /** Every name with its clauses. */
    @TestOnly
    fun byName(): SortedMap<String, List<Call>> = positionsByName.mapValuesTo(TreeMap()) { (_, positions) -> positions.map(clauses::get) }

    companion object {
        private val KEY = Key<CachedValue<ImplicitImportIndex>>("ImplicitImportIndex")

        @RequiresReadLock
        fun of(modular: Call): ImplicitImportIndex {
            ThreadingAssertions.assertReadAccess()

            return CachedValuesManager.getCachedValue(modular, KEY) {
                CachedValueProvider.Result.create(build(modular), PsiModificationTracker.MODIFICATION_COUNT)
            }
        }

        private fun build(modular: Call): ImplicitImportIndex {
            WalkProbe.count(WalkProbe.Counter.KERNEL_INDEX_BUILD)
            val clauses = Modular.callDefinitionClauseCallSequence(modular).toList()
            val positionsByName = TreeMap<String, MutableList<Int>>()

            clauses.forEachIndexed { position, clause ->
                ProgressManager.checkCanceled()
                WalkProbe.cancelPoint(WalkProbe.CancelPoint.KERNEL_INDEX)

                Declarations.of(Form.CLAUSE, clause, ResolveState.initial()).singleOrNull()?.declaration?.name?.let { name ->
                    positionsByName.getOrPut(name) { mutableListOf() }.add(position)
                }
            }

            return ImplicitImportIndex(clauses, positionsByName)
        }
    }
}
