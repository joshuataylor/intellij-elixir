defmodule Placement.Post do
  use Ecto.Schema
  import Ecto.Query

  def before_schema, do: __schema__(:source)

  schema "posts" do
    field :title
    def inside_schema, do: __changeset__()
  end

  from(p in "posts", where: fragment("1"), select: count(p.id))

  def after_schema, do: __changeset__()
  def query, do: from(p in "posts", where: fragment("1"))
  def own(a), do: a
  def use_own, do: own(1)
end

defmodule Placement.Embedded do
  use Ecto.Schema

  embedded_schema do
    field :name
  end

  def uses_embedded, do: __embedded__()
end
