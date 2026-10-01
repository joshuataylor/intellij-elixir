package org.elixir_lang.call;

public enum Visibility {
    PUBLIC,
    PRIVATE,
    /** Fixed only at compile time, as an EEx `function_from_*` kind that is not a literal atom is. */
    UNDECIDED
}
