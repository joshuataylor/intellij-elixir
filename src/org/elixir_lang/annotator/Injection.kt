package org.elixir_lang.annotator

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import org.elixir_lang.ElixirLanguage
import org.elixir_lang.injection.PsiLanguageInjectionHost

/**
 * Whether Elixir an annotator reports errors in is compiled with the module.
 */
internal enum class Injection {
    NONE,
    TEMPLATE,
    UNCOMPILED;

    companion object {
        /**
         * Elixir injected directly, as into documentation or Markdown, is skipped, and so is a template in documentation:
         * neither is compiled with the module.
         */
        fun of(element: PsiElement): Injection {
            val file = element.containingFile ?: return NONE
            val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)

            return when {
                !injectedLanguageManager.isInjectedFragment(file) -> NONE
                file.viewProvider.baseLanguage == ElixirLanguage -> UNCOMPILED
                injectedLanguageManager.getInjectionHost(file)?.let(PsiLanguageInjectionHost::isDocumentation) == true ->
                    UNCOMPILED
                else -> TEMPLATE
            }
        }
    }
}
