package org.elixir_lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.ElixirStructOperation
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.name.Function
import org.elixir_lang.psi.call.name.Module

/** The modulars whose functions a call qualified by this name can reach, or `null` where the name qualifies no call. */
@RequiresReadLock
fun PsiElement.qualifierModulars(): Set<PsiNamedElement>? {
    ThreadingAssertions.assertReadAccess()

    return takeIf(::callIsValidAt)
        ?.containingFile
        ?.let { containingFile -> maybeModularNameToModulars(maxScope = containingFile, useCall = null, incompleteCode = true) }
}

private fun callIsValidAt(qualifiedAlias: PsiElement): Boolean =
    generateSequence(qualifiedAlias.parent) { it.parent }
        .takeWhile { it !is PsiFile }
        .none { ancestor ->
            when (ancestor) {
                is ElixirStructOperation -> !isInsideStructArguments(ancestor, qualifiedAlias)
                is Call -> namesModuleDirective(ancestor, qualifiedAlias)
                else -> false
            }
        }

/**
 * `structOperation ::= mapPrefixOperator mapExpression eolStar mapArguments`, so a field value has the
 * struct operation as an ancestor too and only the name part must be refused.
 */
private fun isInsideStructArguments(
    structOperation: ElixirStructOperation,
    qualifiedAlias: PsiElement
): Boolean = PsiTreeUtil.isAncestor(structOperation.mapArguments, qualifiedAlias, false)

/**
 * `defmodule` and `defprotocol` need listing even though `QualifiableAliasImpl.computeReference` nulls
 * their names' references: `declaringModuleCall` recognises them by their `do` block, which a name still
 * being typed has not got yet.
 */
private fun namesModuleDirective(call: Call, qualifiedAlias: PsiElement): Boolean =
    call.functionName() in MODULE_NAMING_FUNCTION_NAMES &&
        call.resolvedModuleName() == Module.KERNEL &&
        call.primaryArguments()?.firstOrNull()
            ?.let { PsiTreeUtil.isAncestor(it, qualifiedAlias, false) } == true

/**
 * `Kernel` calls whose first argument is a module name rather than an expression.
 *
 * The other module-name positions this does not reach are listed in
 * [#4051](https://github.com/intellij-elixir/intellij-elixir/issues/4051).
 */
private val MODULE_NAMING_FUNCTION_NAMES = setOf(
    Function.ALIAS,
    Function.DEFIMPL,
    Function.DEFMODULE,
    Function.DEFPROTOCOL,
    Function.IMPORT,
    Function.REQUIRE,
    Function.USE
)
