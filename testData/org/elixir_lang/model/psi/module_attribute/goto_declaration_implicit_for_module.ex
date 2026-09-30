defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForOuter do
  defimpl ImplicitForProtocol, for: __MODULE__ do
    def describe(_), do: to_string(@f<caret>or)
  end
end
