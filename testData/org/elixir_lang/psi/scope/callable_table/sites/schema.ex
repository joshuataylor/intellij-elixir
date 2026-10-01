defmodule Ecto.Schema do
  defmacro __using__(_) do
    quote do
      import Ecto.Schema
    end
  end

  defmacro schema(source, do: block) do
    quote do
      def schema_source, do: unquote(source)
      unquote(block)
    end
  end
end

defmodule Sites.Post do
  use Ecto.Schema

  schema "posts" do
    :ok
  end

  def own, do: schema_source()
end

defmodule Sites.Inside do
  use Ecto.Schema

  schema "inside" do
    schema_source()
  end
end
