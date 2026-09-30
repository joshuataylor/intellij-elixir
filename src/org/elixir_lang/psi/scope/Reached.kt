package org.elixir_lang.psi.scope

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.declaration.Reach
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.ElixirPsiImplUtil.ENTRANCE
import org.elixir_lang.psi.stub.type.call.Stub.isModular

private val REACH = Key<Reach>("Reach")

/** [this] state, reached through an import, which is lexical however the walk found it. */
fun ResolveState.reachedThrough(reach: Reach): ResolveState = put(REACH, reach)

/**
 * [this] state for what [injector], a `use` or another macro call found with it, injects. That goes into the module
 * holding [injector], so an injection into an enclosing module stays [Reach.OUTER].
 */
@RequiresReadLock
fun ResolveState.reachedThroughInjection(injector: PsiElement): ResolveState {
    ThreadingAssertions.assertReadAccess()

    return put(REACH, reached(injector).let { if (it.held) Reach.USE else it })
}

/**
 * [this] state for a target of [delegation], found with it, that the walk of the target's module reached by
 * [targetReach]: a remote call of the module reaches it only if the module holds the delegation and a remote call of
 * the target's module reaches the target.
 */
@RequiresReadLock
fun ResolveState.reachedThroughDelegation(delegation: PsiElement, targetReach: Reach?): ResolveState {
    ThreadingAssertions.assertReadAccess()

    val remote = reached(delegation).held && targetReach?.remote == true

    return put(REACH, if (remote) Reach.DELEGATION_TARGET else Reach.UNHELD_DELEGATION_TARGET)
}

/**
 * How [element], found with [this] state, was reached: what the path says, or [Reach.OUTER] for one outside the
 * module the walk started in, since every other way out of that module records its own reach.
 */
@RequiresReadLock
fun ResolveState.reached(element: PsiElement): Reach {
    ThreadingAssertions.assertReadAccess()

    val reach = get(REACH) ?: Reach.OWN
    val home = if (reach == Reach.OWN) get(ENTRANCE)?.let(::homeModular) else null

    return if (home != null && !PsiTreeUtil.isAncestor(home, element, false)) Reach.OUTER else reach
}

// Through `getContext`, as the walk goes, so a `~H` sigil or a template is in the module holding it.
private fun homeModular(entrance: PsiElement): PsiElement? =
    generateSequence(entrance) { it.context }.firstOrNull { it is Call && isModular(it) }
