defprotocol Target do
  def describe(value)
end

defimpl Tar<caret>get, for: Tuple do
  def describe(_value), do: "tuple"
end

defmodule Usage do
  def call(x), do: Target.describe(x)
end
