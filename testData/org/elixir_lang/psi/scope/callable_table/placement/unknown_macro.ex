defmodule Placement.Macros do
  defmacro with_helpers(do: block) do
    quote do
      def injected_helper(a), do: a
      unquote(block)
    end
  end
end

defmodule Placement.UsesMacro do
  import Placement.Macros

  with_helpers do
    def inside, do: injected_helper(1)
  end

  def outside, do: injected_helper(2)
  def local(a), do: a
  def use_local, do: local(1)
end
