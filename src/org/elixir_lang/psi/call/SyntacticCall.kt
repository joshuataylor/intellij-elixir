package org.elixir_lang.psi.call

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.elixir_lang.psi.*
import org.elixir_lang.psi.impl.ElixirPsiImplUtil
import org.elixir_lang.psi.impl.call.finalArity
import org.elixir_lang.psi.impl.enclosingMacroCall
import org.elixir_lang.psi.impl.hasKeywordKey
import org.elixir_lang.psi.operation.Match

/**
 * The view of a call that stub building gets: what the call's own text says, so no question asked through it
 * resolves a reference.
 */
interface SyntacticCall {
    enum class Shape { AT_UNQUALIFIED_NO_PARENTHESES, UNQUALIFIED_NO_ARGUMENTS, UNQUALIFIED_PARENTHESES, OTHER }

    val shape: Shape

    fun functionName(): String?
    fun resolvedModuleName(): String?
    fun resolvedFinalArity(): Int
    fun finalArity(): Int?
    fun hasDoBlockOrKeyword(): Boolean
    fun isCalling(resolvedModuleName: String, functionName: String): Boolean
    fun isCalling(resolvedModuleName: String, functionName: String, resolvedFinalArity: Int): Boolean
    fun isCallingMacro(resolvedModuleName: String, functionName: String): Boolean
    fun isCallingMacro(resolvedModuleName: String, functionName: String, resolvedFinalArity: Int): Boolean

    /** `@name` for `@name value`, and `null` for any other shape. */
    fun moduleAttributeName(): String?

    fun enclosingMacroCall(): SyntacticCall?

    /** The call whose arguments hold this call directly. */
    fun argumentOf(): SyntacticCall?

    fun isMatchOperand(): Boolean

    /**
     * The call reached through parenthesised argument lists, a `do:` keyword and no-arguments calls, as `quote` reaches
     * `quote(do: x)`'s `x`.
     */
    fun bareArgumentOf(): SyntacticCall?

    /** The call whose one no-parentheses argument is this call or a list of it, as `defdelegate` holds its heads. */
    fun headListOf(): SyntacticCall?

    fun firstPrimaryArgumentText(): String?

    fun name(): String?
    fun canonicalNameSet(): Set<String>
    fun implementedProtocolName(): String?

    companion object {
        @JvmStatic
        fun of(call: Call): SyntacticCall = PsiBacked(call)

        /**
         * The call behind [syntacticCall], only for a `Call` overload handing back a call its implementation found.
         * Stub code must never use it: it reopens every question the view leaves out.
         */
        @JvmStatic
        fun call(syntacticCall: SyntacticCall): Call = (syntacticCall as PsiBacked).call
    }
}

private class PsiBacked(val call: Call) : SyntacticCall {
    override val shape: SyntacticCall.Shape
        get() = when (call) {
            is AtUnqualifiedNoParenthesesCall<*> -> SyntacticCall.Shape.AT_UNQUALIFIED_NO_PARENTHESES
            is UnqualifiedNoArgumentsCall<*> -> SyntacticCall.Shape.UNQUALIFIED_NO_ARGUMENTS
            is UnqualifiedParenthesesCall<*> -> SyntacticCall.Shape.UNQUALIFIED_PARENTHESES
            else -> SyntacticCall.Shape.OTHER
        }

    override fun functionName(): String? = call.functionName()
    override fun resolvedModuleName(): String? = call.resolvedModuleName()
    override fun resolvedFinalArity(): Int = call.resolvedFinalArity()
    override fun finalArity(): Int? = call.finalArity()
    override fun hasDoBlockOrKeyword(): Boolean = call.hasDoBlockOrKeyword()

    override fun isCalling(resolvedModuleName: String, functionName: String): Boolean =
        call.isCalling(resolvedModuleName, functionName)

    override fun isCalling(resolvedModuleName: String, functionName: String, resolvedFinalArity: Int): Boolean =
        call.isCalling(resolvedModuleName, functionName, resolvedFinalArity)

    override fun isCallingMacro(resolvedModuleName: String, functionName: String): Boolean =
        call.isCallingMacro(resolvedModuleName, functionName)

    override fun isCallingMacro(resolvedModuleName: String, functionName: String, resolvedFinalArity: Int): Boolean =
        call.isCallingMacro(resolvedModuleName, functionName, resolvedFinalArity)

    override fun moduleAttributeName(): String? =
        (call as? AtUnqualifiedNoParenthesesCall<*>)?.let { ElixirPsiImplUtil.moduleAttributeName(it) }

    override fun enclosingMacroCall(): SyntacticCall? = call.enclosingMacroCall()?.let(::PsiBacked)

    override fun argumentOf(): SyntacticCall? =
        (call.parent as? Arguments)?.parent?.let { it as? Call }?.let(::PsiBacked)

    override fun isMatchOperand(): Boolean = call.parent is Match

    override fun bareArgumentOf(): SyntacticCall? {
        var element: PsiElement? = call.parent

        while (true) {
            element = when (element) {
                is UnqualifiedNoArgumentsCall<*>,
                is QuotableKeywordList,
                is ElixirParenthesesArguments,
                is ElixirMatchedParenthesesArguments -> element.parent
                is QuotableKeywordPair -> if (element.hasKeywordKey("do")) element.parent else return null
                is Call -> return PsiBacked(element)
                else -> return null
            }
        }
    }

    override fun headListOf(): SyntacticCall? {
        val parent = call.parent
        val arguments = if (parent is ElixirList) {
            parent.parent.let { it as? ElixirAccessExpression }?.parent
        } else {
            parent
        }

        return (arguments as? ElixirNoParenthesesOneArgument)?.parent?.let { it as? Call }?.let(::PsiBacked)
    }

    override fun firstPrimaryArgumentText(): String? = call.primaryArguments()?.firstOrNull()?.text

    override fun name(): String? = (call as? PsiNamedElement)?.name
    override fun canonicalNameSet(): Set<String> = (call as? StubBased<*>)?.canonicalNameSet() ?: emptySet()
    override fun implementedProtocolName(): String? = ElixirPsiImplUtil.implementedProtocolName(call)
}
