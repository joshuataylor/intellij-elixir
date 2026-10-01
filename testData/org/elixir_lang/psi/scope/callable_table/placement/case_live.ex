defmodule Placement.CaseLive do
  use ExUnit.Case

  def helper(a), do: a

  describe "group" do
    def in_describe(a), do: a
  end

  defmodule Nested do
    def nested_helper(a), do: a
  end
end
