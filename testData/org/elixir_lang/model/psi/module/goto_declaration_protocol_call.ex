defprotocol NavigatedProtocol do
  def describe(value)
end

defmodule Caller do
  def call(x), do: Navigated<caret>Protocol.describe(x)
end
