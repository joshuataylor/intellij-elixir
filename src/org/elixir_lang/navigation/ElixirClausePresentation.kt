package org.elixir_lang.navigation

import com.intellij.psi.PsiNamedElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.CallDefinitionClause.enclosingModularMacroCall
import org.elixir_lang.psi.CallDefinitionClause.head
import org.elixir_lang.psi.Implementation
import org.elixir_lang.psi.Module
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall

/**
 * Concise presentation of an Elixir `def`/`defmacro` clause for navigation popups.
 *
 * Shared by [ElixirGotoTargetPresentationProvider] (Go To Implementation / Go To Target, via
 * `TargetPresentation`) and [ElixirImplementationCellRenderer] (the protocol gutter icon popup, via
 * `PsiElementListCellRenderer`) so both render each clause the same way:
 * ```
 * | icon  def to_string(%MyStruct{} = value)   MyApp.MyStruct   <module> |
 * ```
 * - [elementText] is the clause head (definer + name + argument patterns), so sibling clauses that
 *   share a name/arity are told apart by their patterns.
 * - [containerText] is the implementing type: the module the `defimpl` is for, which without `for:` is the
 *   module it is written in (as in `defimpl String.Chars do` nested in a module).
 */
object ElixirClausePresentation {
    @RequiresReadLock
    fun elementText(call: Call): String {
        val head = head(call)?.text?.let(::normalizeSignature)

        return if (head != null) {
            call.functionName()?.let { definer -> "$definer $head" } ?: head
        } else {
            (call as? PsiNamedElement)?.name ?: normalizeSignature(call.text)
        }
    }

    @RequiresReadLock
    fun containerText(call: Call): String? {
        val enclosingModular = enclosingModularMacroCall(call) ?: return null

        return if (Implementation.`is`(enclosingModular)) {
            Implementation.forText(enclosingModular) ?: Implementation.protocolName(enclosingModular)
        } else if (Module.`is`(enclosingModular)) {
            SyntacticCall.of(enclosingModular).canonicalName()?.let { org.elixir_lang.Module.inspect(it) }
        } else {
            null
        }
    }

    /** Collapse whitespace and tighten bracket spacing, mirroring the structure-view head presentation. */
    private fun normalizeSignature(text: String): String =
        text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("([\\[(]) ?"), "$1")
            .replace(Regex(" ?([])])"), "$1")
            .trim()
}
