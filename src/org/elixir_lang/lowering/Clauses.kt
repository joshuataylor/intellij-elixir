package org.elixir_lang.lowering

import com.intellij.psi.PsiElement

/** `fn`, `->` and its signatures: not lowered yet. */
internal fun Lowering.clause(element: PsiElement): ElixirAst = unlowered(element)
