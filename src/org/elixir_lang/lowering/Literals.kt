package org.elixir_lang.lowering

import com.intellij.psi.PsiElement

/** Literals and containers: not lowered yet. */
internal fun Lowering.literal(element: PsiElement): ElixirAst = unlowered(element)
