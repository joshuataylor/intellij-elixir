package org.elixir_lang.model.psi.module_attribute

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.model.psi.module.ModuleReference
import org.elixir_lang.psi.CallDefinitionClause
import org.elixir_lang.psi.Implementation
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.impl.call.body

/** `@for` or `@protocol` inside a `defimpl`, which Elixir defines without a declaration in the source. */
@Suppress("UnstableApiUsage")
class ImplementationAttributeReference(
    private val call: Call,
    private val implementation: Call,
    private val rangeInElement: TextRange
) : PsiSymbolReference {
    override fun getElement(): PsiElement = call

    override fun getRangeInElement(): TextRange = rangeInElement

    @RequiresReadLock
    override fun resolveReference(): Collection<Symbol> =
        when (call.functionName()) {
            FOR ->
                Implementation.forNames(implementation)
                    .orEmpty()
                    .flatMap { forName -> ModuleReference.resolve(implementation, forName) }
                    .distinct()
            PROTOCOL ->
                Implementation.protocolName(implementation)
                    ?.let { protocolName -> ModuleReference.resolve(implementation, protocolName) }
                    .orEmpty()
            else -> emptyList()
        }

    companion object {
        private const val FOR = "for"
        private const val PROTOCOL = "protocol"
        val NAMES = listOf(FOR, PROTOCOL)

        /** The `defimpl` whose module [usage] is read in, through function bodies but not a nested module or `quote`. */
        @RequiresReadLock
        fun implementation(usage: Call): Call? {
            ThreadingAssertions.assertReadAccess()

            // `enclosingModularMacroCall` stops at an `@` operation and at a call's arguments, so every ancestor is asked.
            return generateSequence(usage.parent) { it.parent }
                .takeWhile { it !is PsiFile }
                .filterIsInstance<Call>()
                .firstOrNull { call ->
                    CallDefinitionClause.startsNewScope(call) &&
                        !CallDefinitionClause.`is`(call) &&
                        PsiTreeUtil.isAncestor(call.body(), usage, false)
                }
                ?.takeIf { Implementation.`is`(it) }
        }

        /** What [usage] names, as text, when it is `@for` or `@protocol` in [implementation]. */
        @RequiresReadLock
        fun valueText(usage: Call, implementation: Call): String? {
            ThreadingAssertions.assertReadAccess()

            return when (usage.functionName()) {
                FOR -> Implementation.forText(implementation)
                PROTOCOL -> Implementation.protocolName(implementation)
                else -> null
            }
        }
    }
}
