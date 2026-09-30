defmodule GotoSuperOuter do
  defprotocol Protocol do
    def run(value)
  end

  defimpl __MODULE__.Protocol, for: Atom do
    def ru<caret>n(value), do: value
  end
end
