package org.elixir_lang.psi

import com.intellij.psi.PsiElement
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall

object Variable {
    @JvmStatic
    fun isDeclaration(element: PsiElement): Boolean = element is Call && isDeclaration(SyntacticCall.of(element))

    @JvmStatic
    fun isDeclaration(call: SyntacticCall): Boolean =
            call.shape == SyntacticCall.Shape.UNQUALIFIED_NO_ARGUMENTS && call.resolvedFinalArity() == 0 &&
                    (call.isMatchOperand() || call.bareArgumentOf()?.let { QuoteMacro.`is`(it) } == true)
}
