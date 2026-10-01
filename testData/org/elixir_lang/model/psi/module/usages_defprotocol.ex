defprotocol Tar<caret>get do
  def describe(value)
end

defimpl Target, for: Tuple do
  def describe(_value), do: "tuple"
end

defmodule Usage do
  def call(x), do: Target.describe(x)
end
