defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForFirst do
end

defmodule ImplicitForSecond do
end

defimpl ImplicitForProtocol, for: [ImplicitForFirst, ImplicitForSecond] do
  def describe(_), do: @f<caret>or
end
