defmodule EEx do
  defmacro function_from_string(kind, name, source, args \\ [], options \\ []) do
    {kind, name, source, args, options}
  end
end

defmodule Kernel do
  import EEx

  function_from_string(:def, :k_render, "<%= a %>", [:a])

  def k_use, do: k_render(1)
end

defmodule ReentryImported.User do
  def use_render, do: k_render(1)
end
