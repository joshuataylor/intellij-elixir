defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForTarget do
end

defimpl ImplicitForProtocol, for: ImplicitForTarget do
  def describe(x) do
    if x == @f<caret>or do
      :same
    end
  end
end
