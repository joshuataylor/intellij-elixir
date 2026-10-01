package org.elixir_lang.code_insight.completion.provider

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.elixir_lang.code_insight.completion.callDefinitionClauseLookupElements
import org.elixir_lang.psi.QualifiableAlias
import org.elixir_lang.psi.qualifier
import org.elixir_lang.psi.impl.qualifierModulars

class CallDefinitionClause : CompletionProvider<CompletionParameters>() {
    /** The copy is read first: its dummy identifier terminates the qualified name. */
    private fun maybeModularName(parameters: CompletionParameters): PsiElement? =
        maybeModularNameAt(parameters.position) ?: maybeModularNameAt(parameters.originalPosition)

    private fun maybeModularNameAt(position: PsiElement?): PsiElement? =
        position?.parent?.parent?.let { qualifiedName ->
            // Bare, the copy's dummy makes `Mod.` a qualified alias; with a prefix typed it is a call.
            when (qualifiedName) {
                is org.elixir_lang.psi.qualification.Qualified -> qualifiedName.qualifier()
                is QualifiableAlias -> qualifiedName.qualifier()
                else -> null
            }
        }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet
    ) {
        maybeModularName(parameters)?.qualifierModulars()?.let { modulars ->
            val modularsResultSet = if (resultSet.prefixMatcher.prefix.endsWith(".")) {
                resultSet.withPrefixMatcher("")
            } else {
                resultSet
            }

            modularsResultSet.addAllElements(
                callDefinitionClauseLookupElements(modulars)
            )
        }
    }
}
