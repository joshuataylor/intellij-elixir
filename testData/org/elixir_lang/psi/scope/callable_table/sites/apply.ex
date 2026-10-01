defmodule Sites.Views do
  def controller do
    quote do
      def target(a), do: a
    end
  end

  def controller(_extra) do
    quote do
      def target(a, b, c), do: {a, b, c}
    end
  end
end

defmodule Sites.Views do
  def controller do
    quote do
      def target(a, b), do: {a, b}
    end
  end
end

defmodule Sites.Web do
  defmacro __using__(which) when is_atom(which) do
    apply(Sites.Views, which, [])
  end
end

defmodule Sites.Web do
  defmacro __using__(_) do
    quote do
      def target(a, b, c, d), do: {a, b, c, d}
    end
  end
end

defmodule Sites.User do
  use Sites.Web, :controller

  def own, do: target(1)
end

defmodule Sites.Late do
  def own, do: target(1)

  use Sites.Web, :controller
end
