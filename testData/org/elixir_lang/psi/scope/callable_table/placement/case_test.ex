defmodule Placement.CaseTest do
  use ExUnit.Case

  def helper(a), do: a

  describe "group" do
    def in_describe(a), do: a

    test "one" do
      helper(1)
      in_describe(1)
    end
  end

  test "two" do
    helper(2)
    in_describe(2)
  end

  test "three"

  def after_tests, do: helper(3)
end
