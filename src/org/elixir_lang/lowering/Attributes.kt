package org.elixir_lang.lowering

import com.intellij.psi.PsiElement

/** Module attributes: not lowered yet. */
internal fun Lowering.attribute(element: PsiElement): ElixirAst = unlowered(element)
