defmodule LocalTypeInReturn do
  @spec a() :: {:ok, t}
  def a, do: {:ok, 1}

  @type t :: integer
end
