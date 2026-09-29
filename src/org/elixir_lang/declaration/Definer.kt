package org.elixir_lang.declaration

import org.elixir_lang.call.Visibility
import org.elixir_lang.psi.call.name.Function

/** The `def*` a clause is written with, which alone decides the clause's [Capabilities]. */
enum class Definer(val keyword: String, val capabilities: Capabilities) {
    DEF(
        Function.DEF,
        Capabilities(quotesArguments = false, compileTime = false, usableInGuards = false, Visibility.PUBLIC)
    ),
    DEFP(
        Function.DEFP,
        Capabilities(quotesArguments = false, compileTime = false, usableInGuards = false, Visibility.PRIVATE)
    ),
    DEFMEMO(
        Function.DEFMEMO,
        Capabilities(quotesArguments = false, compileTime = false, usableInGuards = false, Visibility.PUBLIC)
    ),
    DEFMEMOP(
        Function.DEFMEMOP,
        Capabilities(quotesArguments = false, compileTime = false, usableInGuards = false, Visibility.PRIVATE)
    ),
    DEFMACRO(
        Function.DEFMACRO,
        Capabilities(quotesArguments = true, compileTime = true, usableInGuards = false, Visibility.PUBLIC)
    ),
    DEFMACROP(
        Function.DEFMACROP,
        Capabilities(quotesArguments = true, compileTime = true, usableInGuards = false, Visibility.PRIVATE)
    ),
    DEFGUARD(
        Function.DEFGUARD,
        Capabilities(quotesArguments = false, compileTime = true, usableInGuards = true, Visibility.PUBLIC)
    ),
    DEFGUARDP(
        Function.DEFGUARDP,
        Capabilities(quotesArguments = false, compileTime = true, usableInGuards = true, Visibility.PRIVATE)
    );

    companion object {
        private val BY_KEYWORD = entries.associateBy { it.keyword }

        fun of(keyword: String): Definer? = BY_KEYWORD[keyword]
    }
}
