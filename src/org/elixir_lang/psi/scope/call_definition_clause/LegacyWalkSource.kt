package org.elixir_lang.psi.scope.call_definition_clause

import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.code_insight.completion.callDefinitionClauseVisible
import org.elixir_lang.declaration.Applicability
import org.elixir_lang.declaration.ArityKnowledge
import org.elixir_lang.declaration.Candidate
import org.elixir_lang.declaration.CandidateSource
import org.elixir_lang.declaration.Found
import org.elixir_lang.declaration.Use
import org.elixir_lang.declaration.Visible
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.qualification.Qualified
import org.elixir_lang.psi.impl.qualifierModulars
import org.elixir_lang.psi.qualification.Unqualified
import org.elixir_lang.psi.scope.ReachedDeclaration
import org.elixir_lang.psi.scope.VisitedElementSetResolveResult
import org.elixir_lang.reference.resolver.Callable

/** The candidates today's call walk finds, read before `Resolver.preferred` narrows them for `multiResolve`. */
object LegacyWalkSource : CandidateSource {
    @RequiresReadLock
    override fun candidates(use: Use, incompleteCode: Boolean): List<Found> {
        ThreadingAssertions.assertReadAccess()

        return Callable.walk(use.entrance, incompleteCode).flatMap { found(it, use) }
    }

    @RequiresReadLock
    override fun visible(position: Call): List<Visible> {
        ThreadingAssertions.assertReadAccess()

        // Completion offers the walk's lookups only at an unqualified call; after a qualifier it offers that module's.
        return when (position) {
            is Unqualified -> Variants.lookupElementsAndVisible(position).second
            is Qualified -> position.qualifier().qualifierModulars()?.let(::callDefinitionClauseVisible).orEmpty()
            else -> emptyList()
        }
    }

    private fun found(result: VisitedElementSetResolveResult, use: Use): List<Found> =
        result.reached.mapNotNull { reached ->
            val reach = reached.reach ?: return@mapNotNull null

            applicability(reached, use)?.let { applicability ->
                val paths = reached.visitedElementSet.filterIsInstance<Call>().filter(Callable::isPath)

                Found(Candidate(reached.declaration, reach, applicability), result.element, paths + reached.via)
            }
        }

    private fun applicability(reached: ReachedDeclaration, use: Use): Applicability? =
        when (use) {
            // The walk finds no name-less use valid, so the arity decides, but behind a `defdelegate` only the name it
            // delegates to is reached.
            is Use.AnyName ->
                when {
                    reached.searchedAtom != null && reached.searchedAtom != reached.declaration.name -> null
                    admits(reached.declaration.arity, use.arity) -> Applicability.OPAQUE
                    else -> Applicability.WRONG_ARITY
                }
            is Use.Named ->
                if (reached.headNamed) {
                    reached.searchedAtom?.let { Applicability.of(reached.declaration, it, reached.valid) }
                } else {
                    null
                }
        }

    private fun admits(arity: ArityKnowledge, used: Int): Boolean =
        when (arity) {
            is ArityKnowledge.Exact -> arity.arity == used
            is ArityKnowledge.Range -> used in arity.minimum..arity.maximum
            is ArityKnowledge.Open -> used >= arity.minimum
            ArityKnowledge.Unknown -> true
        }
}
