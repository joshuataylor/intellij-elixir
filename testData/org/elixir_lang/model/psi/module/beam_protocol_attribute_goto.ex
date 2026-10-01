defmodule Point do
  defstruct [:x]
end

defimpl Inspect, for: Point do
  def inspect(_point, _opts), do: Kernel.inspect(@pro<caret>tocol)
end
