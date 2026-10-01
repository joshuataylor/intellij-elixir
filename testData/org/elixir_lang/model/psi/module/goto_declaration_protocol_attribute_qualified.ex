defprotocol MyApp.Protocol do
  def describe(value)
end

defimpl MyApp.Protocol, for: Tuple do
  def describe(_value), do: @pro<caret>tocol
end
