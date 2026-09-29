defprotocol ProtocolImpl do
  def convert(x)
end

defimpl ProtocolImpl, for: Atom do
  def convert(x), do: x
end
