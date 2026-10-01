package org.elixir_lang.expander

import org.elixir_lang.lowering.ElixirAst

/** What [Expander.expand] gives a node. */
sealed class Expansion {
    /** The state and env Elixir leaves after the node. */
    data class Expanded(val state: ExState, val env: Env) : Expansion()

    /** [at] reaches a clause, or a branch of one, that isn't ported, an error branch included. */
    data class Unported(val at: ElixirAst) : Expansion()
}
