package org.elixir_lang.declaration

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightVirtualFile

/** A source declaration in `m.ex`, at [offset] so that declarations otherwise alike are distinct. */
internal fun declaration(
    name: String,
    arity: ArityKnowledge,
    definer: Definer = Definer.DEF,
    offset: Int = 0
): Declaration =
    Declaration(
        name,
        arity,
        definer.capabilities,
        Declared.Source(Form.CLAUSE, SourceOrigin(LightVirtualFile("m.ex"), 0, TextRange(offset, offset)))
    )
