defmodule EEx do
  defmacro function_from_string(kind, name, source, args \\ [], options \\ []) do
    {kind, name, source, args, options}
  end

  __MODULE__.function_from_string(:def, :self_render, "<%= a %>", [:a])

  def use_self_render, do: self_render(1)
end
