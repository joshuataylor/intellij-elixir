package org.elixir_lang.lowering

import com.intellij.psi.PsiElement

/** Operators of every precedence, captures, `..` and `..//`: not lowered yet. */
internal fun Lowering.operator(element: PsiElement): ElixirAst = unlowered(element)
