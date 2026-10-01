defprotocol GotoImplementationProtocol do
  def perform(value)
end

defimpl Goto<caret>ImplementationProtocol, for: Atom do
  def perform(_value), do: :ok
end
