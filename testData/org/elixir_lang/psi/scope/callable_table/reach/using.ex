defmodule Reach.Using do
  defmacro __using__(_) do
    quote do
      def injected(a), do: a
    end
  end
end

defmodule Reach.Outer do
  use Reach.Using

  def own, do: injected(1)

  defmodule Inner do
    def nested, do: injected(2)
  end
end

defmodule Reach.UseAfterNested do
  defmodule Inner do
    def nested, do: injected(5)
  end

  use Reach.Using

  def own, do: injected(6)
end

defmodule Reach.SigilOuter do
  defmodule Inner do
    def render(assigns), do: ~H"<%= injected(7) %>"
  end

  use Reach.Using
end
