defprotocol NavigatedProtocol do
  def describe(value)
end

defprotocol ForProtocol do
  def show(value)
end

defimpl NavigatedProtocol, for: ForProtocol do
  def describe(_value), do: @f<caret>or
end
