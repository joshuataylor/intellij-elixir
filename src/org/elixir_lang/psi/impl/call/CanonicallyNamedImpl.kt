package org.elixir_lang.psi.impl.call

import org.elixir_lang.psi.CallDefinitionClause.enclosingModularMacroCall
import org.elixir_lang.psi.Implementation
import org.elixir_lang.psi.Module
import org.elixir_lang.psi.Protocol
import org.elixir_lang.psi.QuoteMacro
import org.elixir_lang.psi.call.StubBased
import org.elixir_lang.psi.call.SyntacticCall
import org.elixir_lang.psi.call.name.Function.__MODULE__
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.stub.type.call.Stub.isModular

object CanonicallyNamedImpl {
    @RequiresReadLock
    fun canonicalName(stubBased: StubBased<*>): String? = canonicalName(SyntacticCall.of(stubBased))

    /** Elixir does not nest an implementation in the module around it, as it does a module or protocol. */
    @RequiresReadLock
    fun canonicalName(call: SyntacticCall): String? =
        if (isModular(call)) {
            if (Implementation.`is`(call)) {
                Implementation.name(call)
                    ?: "${Implementation.protocolName(call) ?: '?'}.${Implementation.forText(call) ?: '?'}"
            } else {
                val canonicalNameSuffix = when {
                    Module.`is`(call) -> Module.name(call)
                    Protocol.`is`(call) -> Module.name(call)
                    else -> null
                }

                val enclosing = enclosingModularMacroCall(call)

                if (canonicalNameSuffix != null && isModuleRelative(canonicalNameSuffix)) {
                    expandModule(canonicalNameSuffix, call) ?: "?"
                } else if (enclosing != null) {
                    "${enclosing.canonicalName() ?: '?'}.${canonicalNameSuffix ?: '?'}"
                } else {
                    canonicalNameSuffix ?: "?"
                }
            }
        } else {
            call.name()
        }

    @RequiresReadLock
    fun canonicalNameSet(stubBased: StubBased<*>): Set<String> = canonicalNameSet(SyntacticCall.of(stubBased))

    @RequiresReadLock
    fun canonicalNameSet(call: SyntacticCall): Set<String> =
        if (isModular(call)) {
            if (Implementation.`is`(call)) {
                Implementation.nameCollection(call)?.toSet() ?: setOf("${Implementation.protocolName(call) ?: '?'}.?")
            } else {
                val canonicalNameSuffix = if (Module.`is`(call) || Protocol.`is`(call)) Module.name(call) else "?"

                if (isModuleRelative(canonicalNameSuffix)) {
                    setOf(expandModule(canonicalNameSuffix, call) ?: "?")
                } else {
                    enclosingModularMacroCall(call)
                        ?.canonicalNameSet()
                        ?.map { canonicalNamePrefix -> "$canonicalNamePrefix.$canonicalNameSuffix" }
                        ?.toSet()
                        ?: setOf(canonicalNameSuffix)
                }
            }
        } else {
            call.name()?.let { setOf(it) } ?: emptySet()
        }

    /** At the top level `__MODULE__` is `nil`, which Elixir drops from an alias. */
    @RequiresReadLock
    fun expandModule(name: String, call: SyntacticCall): String? =
        if (isModuleRelative(name)) {
            val relative = name.removePrefix(__MODULE__)

            enclosingModuleName(call)?.let { "$it$relative" } ?: relative.removePrefix(".").ifEmpty { null }
        } else {
            name
        }

    private fun isModuleRelative(name: String): Boolean = name == __MODULE__ || name.startsWith("$__MODULE__.")

    /** Inside a `quote`, `__MODULE__` is the module the quote is injected into. */
    private fun enclosingModuleName(call: SyntacticCall): String? {
        var enclosing = enclosingModularMacroCall(call)

        while (enclosing != null) {
            when {
                QuoteMacro.`is`(enclosing) -> return "?"
                isModular(enclosing) -> return enclosing.canonicalName() ?: "?"
            }

            enclosing = enclosingModularMacroCall(enclosing)
        }

        return null
    }
}
