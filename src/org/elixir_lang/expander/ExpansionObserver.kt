package org.elixir_lang.expander

import org.elixir_lang.lowering.ElixirAst

/** Told of each node [Expander] reaches, as its dispatch begins and before any clause runs. */
fun interface ExpansionObserver {
    fun entering(node: ElixirAst, state: ExState, env: Env)

    companion object {
        val NONE = ExpansionObserver { _, _, _ -> }
    }
}
