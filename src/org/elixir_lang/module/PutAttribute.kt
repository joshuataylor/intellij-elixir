package org.elixir_lang.module

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.SyntacticCall

object PutAttribute {
    fun `is`(element: PsiElement): Boolean =
            element is Call && `is`(element)

    @JvmStatic
    fun `is`(call: Call): Boolean = `is`(SyntacticCall.of(call))

    @JvmStatic
    fun `is`(call: SyntacticCall): Boolean =
            call.functionName()?.let { functionName ->
                functionName == "put_attribute" &&
                        call.resolvedFinalArity() == 3 &&
                        call.resolvedModuleName() == "Module"
            } ?: false

    @RequiresReadLock
    fun name(call: Call): String? = name(SyntacticCall.of(call))

    @RequiresReadLock
    fun name(call: SyntacticCall): String? = call.attributeAtomName()?.let { "@$it" }

    @RequiresReadLock
    fun nameIdentifier(call: Call): PsiElement? = RegisterAttribute.nameIdentifier(call)
}
