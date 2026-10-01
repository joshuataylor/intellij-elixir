defmodule Sites.Twice do
  defmacro __using__(_) do
    quote do
      def twice(a), do: a
    end
  end
end

defmodule Sites.Twice do
  defmacro __using__(_) do
    quote do
      def twice(a, b), do: {a, b}
    end
  end
end

defmodule Sites.TwiceUser do
  def own, do: twice(1)

  use Sites.Twice
end

defmodule Sites.Views2 do
  def controller do
    quote do
      def late(a), do: a
    end
  end
end

defmodule Sites.Views2 do
  def other, do: :ok
end

defmodule Sites.Web2 do
  defmacro __using__(which) when is_atom(which) do
    apply(Sites.Views2, which, [])
  end
end

defmodule Sites.Web2 do
  defmacro __using__(_) do
    quote do
      def late(a, b), do: {a, b}
    end
  end
end

defmodule Sites.Late2 do
  def own, do: late(1)

  use Sites.Web2, :controller
end

defmodule Sites.Quoted do
  defmacro __using__(_) do
    quote do
      use Sites.Twice
      def twice(a, b, c), do: {a, b, c}
    end
  end
end

defmodule Sites.QuotedUser do
  def own, do: twice(1)

  use Sites.Quoted
end
