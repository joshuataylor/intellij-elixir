defprotocol GotoImplementationProtocol do
  def perform(value)
end

defimpl GotoImplementationProtocol, for: Atom do
  def perform(_value), do: :ok
end

defmodule Caller do
  def call(x), do: Goto<caret>ImplementationProtocol.perform(x)
end
