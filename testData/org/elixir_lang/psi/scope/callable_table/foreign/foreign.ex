defmodule Foreign.Foreign do
  use ExUnit.Case

  test "first" do
    :ok
  end

  defmodule Inner do
    def render(assigns), do: ~H"<%= binding(1) %>"
  end

  def binding(a), do: a
  def own, do: binding(2)
end
