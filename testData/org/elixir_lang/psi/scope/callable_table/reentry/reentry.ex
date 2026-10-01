defmodule EEx do
  defmacro function_from_string(kind, name, source, args \\ [], options \\ []) do
    {kind, name, source, args, options}
  end
end

defmodule Mix.Generator do
  defmacro embed_template(name, contents) do
    {name, contents}
  end
end

defmodule Reentry do
  require EEx
  require Mix.Generator

  EEx.function_from_string(:def, :q7_render, "<%= a %>", [:a])
  Mix.Generator.embed_template(:q7_log, "Log")

  def use_render, do: q7_render(1)
  def use_log, do: q7_log_template(a: 1)
end
