defmodule Sites.UnquoteValue do
  defmacro __using__(_) do
    defs =
      quote do
        def uv(a), do: a
      end

    quote do
      unquote(defs)
      def uv(a, b), do: {a, b}
    end
  end
end

defmodule Sites.UnquoteValueUser do
  use Sites.UnquoteValue

  def call, do: uv(1)
end

defmodule Sites.UnquoteValueList do
  defmacro __using__(_) do
    defs = [
      quote do
        def uvl(a), do: a
      end,
      quote do
        def uvl(a, b, c), do: a
      end
    ]

    quote do
      unquote(defs)
    end
  end
end

defmodule Sites.UnquoteValueListUser do
  def call, do: uvl(1)

  use Sites.UnquoteValueList
end

defmodule Sites.UnquoteValueLateUser do
  def call, do: uv(1)

  use Sites.UnquoteValue
end
