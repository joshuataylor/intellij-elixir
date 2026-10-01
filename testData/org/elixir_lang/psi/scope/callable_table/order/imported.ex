defmodule Order.Imported do
  def shadow(x), do: {:imported, x}
  def two(a), do: a
  def two(a, b), do: {a, b}
  def foobar(a), do: a
  def foo(a), do: a
end

defmodule Order.ImportedB do
  def two(a), do: a
  def two(a, b), do: {a, b}
end

defmodule Order.Target do
  def dd(a), do: a
  def ddx(a), do: a
  def dd(a, b), do: {a, b}
end

defmodule Order.TargetB do
  def dd(a), do: a
end
