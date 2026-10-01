defprotocol ImplicitForProtocol do
  def describe(term)
end

defmodule ImplicitForTarget do
end

defimpl ImplicitForProtocol, for: ImplicitForTarget do
  def describe(_) do
    IO.puts(to_string(@f<caret>or))
  end
end
