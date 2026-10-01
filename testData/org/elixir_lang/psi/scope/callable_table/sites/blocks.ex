defmodule Sites.Blocks do
  def own_for, do: for_def(1)
  def own_if, do: if_def(1)
  def own_try, do: try_def(1)

  for _ <- [1] do
    def for_def(a), do: a
    def for_def(a, b), do: {a, b}
  end

  if true do
    def if_def(a), do: a
    def if_def(a, b), do: {a, b}
  end

  try do
    def try_def(a), do: a
    def try_def(a, b), do: {a, b}
  rescue
    _ -> :ok
  end
end

defmodule Sites.QuoteIf do
  defmacro __using__(_) do
    quote do
      if true do
        def qif(a), do: a
        def qif(a, b), do: {a, b}
      else
        def qif(a, b, c), do: {a, b, c}
      end
    end
  end
end

defmodule Sites.QuoteIfUser do
  def own, do: qif(1)

  use Sites.QuoteIf
end

defmodule Sites.ForQuote do
  defmacro __using__(_) do
    quote do
      for _ <- [1] do
        def fq(a), do: a
        def fq(a, b), do: {a, b}
      end

      def fq(a, b, c), do: {a, b, c}
    end
  end
end

defmodule Sites.ForQuoteUser do
  def own, do: fq(1)

  use Sites.ForQuote
end
