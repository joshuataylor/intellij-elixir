defprotocol Outer.Goto<caret>ImplementationProtocol do
  def perform(value)
end

defimpl Outer.GotoImplementationProtocol, for: Atom do
  def perform(_value), do: :ok
end
