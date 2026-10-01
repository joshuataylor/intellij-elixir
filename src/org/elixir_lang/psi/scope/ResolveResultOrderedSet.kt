package org.elixir_lang.psi.scope

import com.intellij.psi.PsiElement
import org.elixir_lang.declaration.Reach

class ResolveResultOrderedSet {
    fun add(
        element: PsiElement,
        name: String,
        validResult: Boolean,
        visitedElementSet: Set<PsiElement>,
        reach: Reach? = null,
        reached: List<ReachedDeclaration> = emptyList()
    ) {
        val existing = resultByElement[element]
        val routed = if (reached.isEmpty()) {
            reached
        } else {
            reached.map { if (it.reach == null) it.copy(reach = reach, visitedElementSet = visitedElementSet) else it }
                .distinct()
        }

        if (existing != null) {
            // Only the first result for an element is kept, but each declaration reached at it is, with its own route.
            existing.addReached(routed)
        } else {
            val visitedElementSetResolveResult =
                VisitedElementSetResolveResult(element, validResult, visitedElementSet, reach, routed)
            resultByElement[element] = visitedElementSetResolveResult
            val existingVisitedElementSetResolveResultList = visitedElementSetResolveResultListByName[name]

            if (existingVisitedElementSetResolveResultList != null) {
                existingVisitedElementSetResolveResultList.add(visitedElementSetResolveResult)
            } else {
                nameOrder.add(name)

                val visitedElementSetResolveResultList = mutableListOf(visitedElementSetResolveResult)
                visitedElementSetResolveResultListByName[name] = visitedElementSetResolveResultList
            }
        }
    }

    fun addAll(other: ResolveResultOrderedSet) {
        other.nameOrder.forEach { name ->
            other.visitedElementSetResolveResultListByName[name]!!.forEach { visitedElementSetResolveResult ->
                add(
                        visitedElementSetResolveResult.element,
                        name,
                        visitedElementSetResolveResult.isValidResult,
                        visitedElementSetResolveResult.visitedElementSet,
                        visitedElementSetResolveResult.reach,
                        visitedElementSetResolveResult.reached
                )
            }
        }
    }

    fun keepProcessing(incompleteCode: Boolean): Boolean = incompleteCode || !hasValidResult()

    fun toList(): List<VisitedElementSetResolveResult> =
            nameOrder
                    .flatMap { name ->
                        visitedElementSetResolveResultListByName[name]!!
                    }

    private val resultByElement = mutableMapOf<PsiElement, VisitedElementSetResolveResult>()
    private val visitedElementSetResolveResultListByName = mutableMapOf<String, MutableList<VisitedElementSetResolveResult>>()
    private val nameOrder = mutableListOf<String>()

    private fun hasValidResult() =
            visitedElementSetResolveResultListByName.values.any {
                it.any { it.isValidResult }
            }
}
