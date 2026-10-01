defmodule PreferValid do
  def two(a, b), do: {a, b}
  def two(a), do: a
  def more, do: two(1)
end

defmodule :queue do
  def new(), do: nil
end

defmodule PreferSource do
  defdelegate make(), to: :queue, as: :new

  def direct, do: :queue.new()
  def delegated, do: make()
  def wrong, do: :queue.new(1)
  def prefix, do: :queue.ne()
end
