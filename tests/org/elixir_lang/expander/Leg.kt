package org.elixir_lang.expander

import org.elixir_lang.beam.ReadResult
import org.elixir_lang.elixir_surface.LegManifest
import org.elixir_lang.language_level.ElixirLanguageLevel
import java.io.File

internal fun legLevel(): ElixirLanguageLevel = ElixirLanguageLevel.of(LegManifest.environment("ELIXIR_VERSION"))

internal val legKernel: KernelImports by lazy {
    val beam = File(LegManifest.ebin(), "Elixir.Kernel.beam")
    val read = KernelImports.read(beam.readBytes(), beam.path)

    (read as? ReadResult.Present)?.value ?: throw AssertionError("$beam: $read")
}
