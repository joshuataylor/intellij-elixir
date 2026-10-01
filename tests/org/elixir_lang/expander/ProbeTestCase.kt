package org.elixir_lang.expander

import org.elixir_lang.parser_definition.ParsingTestCase
import org.elixir_lang.psi.ElixirFile

/** A test that compiles case bodies through a [ProbeHarness] parsing them with this fixture. */
abstract class ProbeTestCase : ParsingTestCase() {
    protected val harness = ProbeHarness { createPsiFile(getTestName(false), it) as ElixirFile }
}
