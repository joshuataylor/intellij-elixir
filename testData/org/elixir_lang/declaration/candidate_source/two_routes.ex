defmodule TwoRoutesTarget do
  def target(x), do: x
end

defmodule TwoRoutes do
  import TwoRoutesTarget
  defdelegate target(x), to: TwoRoutesTarget
  def usage, do: target(1)
end
