package org.elixir_lang.expander

import org.elixir_lang.lowering.ElixirAst

/** Told of each node [Expander] reaches, as its dispatch begins and before any clause runs, and as it is left. */
fun interface ExpansionObserver {
    fun entering(node: ElixirAst, state: ExState, env: Env)

    /** [node]'s [expansion], as [Expander.expand] returns it. */
    fun left(node: ElixirAst, expansion: Expansion) {}

    companion object {
        val NONE = ExpansionObserver { _, _, _ -> }
    }
}
