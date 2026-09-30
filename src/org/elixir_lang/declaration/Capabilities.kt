package org.elixir_lang.declaration

import org.elixir_lang.call.Visibility

/**
 * What a declaration can do. A site asks the capability it means rather than which `def*` wrote the declaration: a
 * guard is expanded at compile time, as a macro is, but evaluates its arguments, as a function does.
 *
 * @property quotesArguments whether a call receives its arguments unevaluated.
 * @property compileTime whether it is expanded at compile time: a `MACRO-` export, which a caller in another module
 *   must `require`.
 * @property usableInGuards whether a guard may call it.
 */
data class Capabilities(
    val quotesArguments: Boolean,
    val compileTime: Boolean,
    val usableInGuards: Boolean,
    val visibility: Visibility
) {
    /** Called at run time: by a local call, `apply/3`, a capture or a dispatch by name. */
    val runtimeFunction: Boolean get() = !compileTime

    val public: Boolean get() = visibility == Visibility.PUBLIC

    /** What `apply/3` or an MFA tuple reaches from another module. */
    val remoteCallable: Boolean get() = runtimeFunction && public

    val presentation: Presentation
        get() = when {
            quotesArguments -> Presentation.MACRO
            usableInGuards -> Presentation.GUARD
            else -> Presentation.FUNCTION
        }
}

enum class Presentation { FUNCTION, MACRO, GUARD }
