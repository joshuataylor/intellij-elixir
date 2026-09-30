package org.elixir_lang.settings

/** The `id` each Elixir Settings page has in plugin.xml, which the compiler cannot check. */
enum class SettingsPageId(val id: String) {
    ELIXIR("language.elixir"),
    ELIXIR_SDKS("language.elixir.sdks.elixir"),
    ERLANG_SDKS("language.elixir.sdks.erlang"),
    EXPERIMENTAL("language.elixir.experimental"),
    TOOL_MANAGERS("language.elixir.tool_managers"),
    CREDO("language.elixir.credo"),
    DIALYZER("language.elixir.dialyzer"),
}
