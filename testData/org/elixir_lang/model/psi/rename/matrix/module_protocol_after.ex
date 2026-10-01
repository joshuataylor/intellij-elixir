defprotocol Fresh do
  def describe(value)
end

defimpl Fresh, for: Tuple do
  def describe(_value), do: "tuple"
end

defmodule Caller do
  alias Fresh, as: Alias

  def call(x), do: Fresh.describe(x)
  def aliased(x), do: Alias.describe(x)
end
