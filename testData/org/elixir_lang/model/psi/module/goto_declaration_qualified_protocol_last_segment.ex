defprotocol MyApp.Protocol do
  def describe(value)
end

defimpl MyApp.Proto<caret>col, for: Tuple do
  def describe(_value), do: "tuple"
end
