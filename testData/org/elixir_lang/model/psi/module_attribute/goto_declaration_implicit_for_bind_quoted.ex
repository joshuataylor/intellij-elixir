defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForTarget do
end

defimpl ImplicitForProtocol, for: ImplicitForTarget do
  defmacro __using__(_) do
    quote bind_quoted: [target: @f<caret>or] do
      def target, do: target
    end
  end
end
