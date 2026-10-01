defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForOuter do
  defmodule Inner do
    defimpl ImplicitForProtocol do
      def describe(_), do: @f<caret>or
    end
  end
end
