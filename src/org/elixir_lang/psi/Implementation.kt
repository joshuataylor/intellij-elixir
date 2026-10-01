package org.elixir_lang.psi

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall
import org.elixir_lang.psi.call.name.Function
import org.elixir_lang.psi.impl.call.CanonicallyNamedImpl
import org.elixir_lang.psi.impl.call.finalArguments
import org.elixir_lang.psi.impl.keywordValue
import org.elixir_lang.psi.impl.moduleName
import org.elixir_lang.psi.stub.index.ModularName

object Implementation {
    @RequiresReadLock
    @JvmStatic
    fun `is`(call: Call): Boolean = `is`(SyntacticCall.of(call))

    @RequiresReadLock
    @JvmStatic
    fun `is`(call: SyntacticCall): Boolean =
        call.isCallingMacro(org.elixir_lang.psi.call.name.Module.KERNEL, Function.DEFIMPL, 2) ||
                call.isCallingMacro(org.elixir_lang.psi.call.name.Module.KERNEL, Function.DEFIMPL, 3)

    /**
     * @return `null` if protocol or module for the implementation cannot be derived or if the `for` argument is a
     * list.
     */
    @RequiresReadLock
    fun name(call: Call): String? = name(SyntacticCall.of(call))

    @RequiresReadLock
    fun name(call: SyntacticCall): String? = nameCollection(call)?.singleOrNull()

    /** A `PROTOCOL.FOR` name for each module the implementation is for, `null` if there are none. */
    @RequiresReadLock
    fun nameCollection(call: SyntacticCall): Collection<String>? {
        val protocolName = protocolName(call) ?: return null

        return forNames(call)?.takeIf { it.isNotEmpty() }?.map { forName -> "$protocolName.$forName" }
    }

    @RequiresReadLock
    fun forNames(call: Call): Collection<String>? = forNames(SyntacticCall.of(call))

    /**
     * The modules the implementation is for: empty for `for: []`, but `null` when there is no module to name, as for
     * a top-level `defimpl` without `for:`. Without `for:`, Elixir implements the protocol for `__MODULE__`.
     */
    @RequiresReadLock
    fun forNames(call: SyntacticCall): Collection<String>? {
        val written = call.forNames() ?: listOf(Function.__MODULE__)

        return if (written.isEmpty()) {
            written
        } else {
            written
                .mapNotNull { forName -> CanonicallyNamedImpl.expandModule(forName, call) }
                .takeIf { it.isNotEmpty() }
        }
    }

    @RequiresReadLock
    fun forText(call: Call): String? = forText(SyntacticCall.of(call))

    /** [forNames] as one module, or as a list when there are more or fewer. */
    @RequiresReadLock
    fun forText(call: SyntacticCall): String? =
        forNames(call)?.let { forNames -> forNames.singleOrNull() ?: forNames.joinToString(", ", "[", "]") }

    fun elementDescription(location: ElementDescriptionLocation): String? =
        if (location === UsageViewTypeLocation.INSTANCE) {
            "implementation"
        } else {
            null
        }

    private fun forNameCollection(forNameElement: ElixirAccessExpression): Collection<String> =
        forNameCollection(forNameElement.children)

    private fun forNameCollection(forNameElement: ElixirList): Collection<String> =
        forNameCollection(forNameElement.children)

    @RequiresReadLock
    fun forNameCollection(forNameElement: PsiElement): Collection<String>? = when (forNameElement) {
        is ElixirAccessExpression -> forNameCollection(forNameElement)
        is ElixirList -> forNameCollection(forNameElement)
        is QualifiableAlias -> forNameCollection(forNameElement)
        is PsiNamedElement -> forNameCollection(forNameElement)
        else -> listOf(forNameElement.text)
    }

    private fun forNameCollection(children: Array<PsiElement>): Collection<String> =
        children.flatMap { child -> forNameCollection(child) ?: emptyList() }

    private fun forNameCollection(forNameElement: PsiNamedElement): Collection<String>? =
        forNameElement.name?.let { listOf(it) }

    private fun forNameCollection(forNameElement: QualifiableAlias): Collection<String>? =
        (moduleName(forNameElement)?.name ?: forNameElement.name)?.let { listOf(it) }

    @RequiresReadLock
    fun forNameElement(call: Call): PsiElement? =
        call.finalArguments()?.lastOrNull()?.let { it as? QuotableKeywordList }?.keywordValue(Function.FOR)

    @RequiresReadLock
    fun processProtocols(defimpl: Call, consumer: Processor<in PsiElement>) {
        protocolName(defimpl)?.let { protocolName ->
            val project = defimpl.project

            StubIndex
                .getInstance()
                .processElements(
                    ModularName.KEY,
                    protocolName,
                    project,
                    GlobalSearchScope.everythingScope(project),
                    NamedElement::class.java,
                    consumer
                )
        }
    }

    @RequiresReadLock
    @JvmStatic
    fun protocolName(call: Call): String? = protocolName(SyntacticCall.of(call))

    @RequiresReadLock
    @JvmStatic
    fun protocolName(call: SyntacticCall): String? =
        call.protocolAliasText()?.let { CanonicallyNamedImpl.expandModule(it, call) }

    @RequiresReadLock
    @JvmStatic
    fun implementedProtocolName(call: SyntacticCall): String? = if (`is`(call)) protocolName(call) else null
}
