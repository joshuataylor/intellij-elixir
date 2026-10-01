defmodule Ecto.Schema do
  defmacro __using__(_) do
    quote do
      import Ecto.Schema
    end
  end

  defmacro schema(source, do: block) do
    quote do
      def __schema__(:source), do: unquote(source)
      def __changeset__, do: %{}
      unquote(block)
    end
  end

  defmacro embedded_schema(do: block) do
    quote do
      def __embedded__, do: true
      unquote(block)
    end
  end

  defmacro field(name, type \\ :string, opts \\ []) do
    {name, type, opts}
  end
end

defmodule Ecto.Query do
  defmacro from(expr, kw \\ []) do
    {expr, kw}
  end

  defmacro where(query, binding \\ [], expr) do
    {query, binding, expr}
  end
end

defmodule Ecto.Query.API do
  def fragment(parts), do: parts
  def count(value), do: value
end
