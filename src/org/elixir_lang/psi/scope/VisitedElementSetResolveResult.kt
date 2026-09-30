package org.elixir_lang.psi.scope

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import org.elixir_lang.declaration.Reach

/** @property reach `null` for a result of a walk that records none: variables, modules, types and attributes. */
class VisitedElementSetResolveResult(
    element: PsiElement,
    validResult: Boolean,
    val visitedElementSet: Set<PsiElement>,
    val reach: Reach? = null
) : PsiElementResolveResult(element, validResult) {
    constructor(element: PsiElement) : this(element, true, emptySet())
}
