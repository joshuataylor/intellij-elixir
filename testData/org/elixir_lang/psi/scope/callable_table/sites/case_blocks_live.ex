defmodule Sites.CaseBlocksLive do
  use ExUnit.Case

  describe "group" do
    def cm(a), do: a
    def cm(a, b), do: {a, b}

    if true do
      def cif(a), do: a
      def cif(a, b), do: {a, b}
    end

    try do
      def ctry(a), do: a
      def ctry(a, b), do: {a, b}
    rescue
      _ -> :ok
    end
  end
end
