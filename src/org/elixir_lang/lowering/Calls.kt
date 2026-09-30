package org.elixir_lang.lowering

import com.intellij.psi.PsiElement

/** Calls, `A.{B, C}`, bracket access and the `do` and keyword blocks of a call: not lowered yet. */
internal fun Lowering.call(element: PsiElement): ElixirAst = unlowered(element)
