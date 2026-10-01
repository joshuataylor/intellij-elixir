defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForTarget do
end

defimpl ImplicitForProtocol, for: ImplicitForTarget, do: (def describe(_), do: @f<caret>or)
