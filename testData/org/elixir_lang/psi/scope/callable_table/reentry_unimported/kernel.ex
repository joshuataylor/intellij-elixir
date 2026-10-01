defmodule EEx do
  defmacro function_from_string(kind, name, source, args \\ [], options \\ []) do
    {kind, name, source, args, options}
  end
end

defmodule Kernel do
  function_from_string(:def, :k_unimported, "<%= a %>", [:a])
  m().function_from_string(:def, :k_qualifier, "<%= a %>", [:a])

  def m, do: EEx
  def k_use, do: k_unimported(1)
end

defmodule ReentryUnimported.User do
  def use_unimported, do: k_unimported(1)
  def use_qualifier, do: k_qualifier(1)
end
