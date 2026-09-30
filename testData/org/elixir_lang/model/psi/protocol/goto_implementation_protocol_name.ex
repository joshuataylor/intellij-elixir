defprotocol Goto<caret>ImplementationProtocol do
  def perform(value)
end

defimpl GotoImplementationProtocol, for: Atom do
  def perform(_value), do: :ok
end
