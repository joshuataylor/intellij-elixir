defmodule NestedC do
  def t(x), do: x
end

defmodule NestedB do
  import NestedC
  defdelegate t(x), to: NestedC
end

defmodule NestedA do
  defdelegate t(x), to: NestedB
  def usage, do: t(1)
end
