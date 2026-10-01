defmodule Sites.QuoteIfStop do
  defmacro __using__(_) do
    quote do
      if true do
        def qi(a), do: a
      else
        def qi_other(a), do: a
      end

      def qi(a, b), do: {a, b}
    end
  end
end

defmodule Sites.QuoteIfStopUser do
  use Sites.QuoteIfStop

  def call, do: qi(1)
end

defmodule Sites.QuoteIfOther do
  defmacro __using__(_) do
    quote do
      if true do
        def qo(a), do: a
      else
        def other(a), do: a
      end

      def qo(a, b), do: {a, b}
    end
  end
end

defmodule Sites.QuoteIfOtherUser do
  def call, do: qo(1)

  use Sites.QuoteIfOther
end

defmodule Sites.QuoteIfNoElse do
  defmacro __using__(_) do
    quote do
      if true do
        def qn(a), do: a
      end

      def qn(a, b), do: {a, b}
    end
  end
end

defmodule Sites.QuoteIfNoElseUser do
  def call, do: qn(1)

  use Sites.QuoteIfNoElse
end
