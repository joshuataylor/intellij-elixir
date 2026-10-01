defmodule Other do
  def public(x), do: x
  def public(x, y), do: {x, y}
  defp private(x), do: x
  defmacro macro(x), do: x
  defdelegate delegated(x), to: Target
  @callback callback(term) :: term
end
