package org.elixir_lang.declaration

/** What is known about a declaration's arities. */
sealed class ArityKnowledge {
    data class Exact(val arity: Int) : ArityKnowledge()

    /** From default arguments. */
    data class Range(val minimum: Int, val maximum: Int) : ArityKnowledge()

    /** Any arity from [minimum] up, as after `unquote_splicing`; `Open(0)` is any arity. */
    data class Open(val minimum: Int) : ArityKnowledge()

    /** Not yet knowable, as for arguments given by a module attribute. */
    data object Unknown : ArityKnowledge()
}
