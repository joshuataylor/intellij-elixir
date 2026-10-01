defmodule Sites.LateImported do
  def li(a), do: a
  def li(a, b), do: {a, b}
end

defmodule Sites.LateImport do
  def call, do: li(1)

  import Sites.LateImported
end

defmodule Sites.LateSchema do
  def own, do: schema_source()

  use Ecto.Schema

  schema "late" do
    :ok
  end
end
