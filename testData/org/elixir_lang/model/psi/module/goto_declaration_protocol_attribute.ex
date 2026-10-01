defprotocol NavigatedProtocol do
  def describe(value)
end

defimpl NavigatedProtocol, for: Tuple do
  def describe(_value), do: @pro<caret>tocol
end
