defmodule Order.ImportBeforeLocal do
  def use_shadow, do: shadow(1)
  import Order.Imported
  def shadow(x), do: {:local, x}
end

defmodule Order.LocalBeforeImport do
  def use_shadow, do: shadow(1)
  def shadow(x), do: {:local, x}
  import Order.Imported
end

defmodule Order.SiblingImport do
  import Order.Imported
  def shadow(x), do: {:local, x}
  def use_shadow, do: shadow(1)
end

defmodule Order.TwoImports do
  def use_two, do: two(1)
  def two(a), do: a
  import Order.Imported
  import Order.ImportedB
end

defmodule Order.Interleaved do
  def foo(a), do: a
  def foobar(a), do: a
  def foo(a, b), do: {a, b}
  def foobar(a, b), do: {a, b}
  def use_foo, do: foo(1)
  def use_foo_partial, do: fo(1)
end

defmodule Order.ImportStop do
  def use_foo, do: foo(1)
  def foo(a), do: a
  import Order.Imported
  def foobar(a, b), do: {a, b}
end

defmodule Order.QuoteStop do
  def use_q, do: qq(1)
  def qq(a), do: a

  quote do
    def qqa(a), do: a
    def qq(a, b), do: {a, b}
  end

  def qqb(a), do: a
end

defmodule Order.DelegationStop do
  def use_dd, do: dd(1)
  defdelegate dd(a), to: Order.Target
  defdelegate dd(a, b), to: Order.Target
  defdelegate ddx(a), to: Order.TargetB, as: :dd
end

defmodule Order.QuoteGate do
  def gated(a), do: a

  quote do
    def gated(a, b), do: {a, b}
    unquote(gated(1))
  end

  def after_gate, do: gated(2)
end
