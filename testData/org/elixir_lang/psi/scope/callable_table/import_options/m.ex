defmodule ImportOptions.M do
  def f(a, b \\ 1), do: {a, b}
  def g(a), do: a
  defmacro mac(a), do: a
end
