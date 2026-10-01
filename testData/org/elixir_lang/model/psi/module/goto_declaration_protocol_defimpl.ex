defprotocol NavigatedProtocol do
  def describe(value)
end

defimpl Navigated<caret>Protocol, for: Tuple do
  def describe(_value), do: "tuple"
end
