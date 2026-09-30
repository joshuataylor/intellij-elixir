defmodule Definitions do
  def exact(a), do: a
  def defaults(a, b \\ 1, c \\ 2), do: {a, b, c}
  def multi(0), do: :zero
  def multi(n), do: n
  def headed(a, b \\ 1)
  def headed(a, b), do: {a, b}

  args = Macro.generate_arguments(2, __MODULE__)
  def spliced(unquote_splicing(args)), do: :ok
  def spliced_after(a, unquote_splicing(args)), do: a

  name = :named
  def unquote(name)(a), do: a

  if true do
    def conditional(a), do: a
  end

  defmodule Nested do
    def inner, do: :ok
  end
end

defimpl String.Chars, for: [Definitions, Definitions.Nested] do
  def to_string(_), do: ""
end
