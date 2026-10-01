defprotocol Renamee do
  def describe(value)
end

defimpl Renamee, for: Tuple do
  def describe(_value), do: "tuple"
end

defmodule Caller do
  alias Renamee, as: Alias

  def call(x), do: Renamee.describe(x)
  def aliased(x), do: Alias.describe(x)
end
