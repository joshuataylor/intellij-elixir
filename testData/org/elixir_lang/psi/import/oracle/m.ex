defmodule M do
  def f(a, b \\ 1), do: {a, b}
  def g(a), do: a
  defp private(a), do: a
  def _hidden(a), do: a

  defmacro mac(a), do: a
  defmacrop private_mac(a), do: a
  defmacro _hidden_mac(a), do: a
  defguard is_small(a) when a < 1

  def sigil_x(term, _modifiers), do: term
  def sigil_AB(term, _modifiers), do: term
  def sigil_ABC1(term, _modifiers), do: term
  def sigil_Ab(term, _modifiers), do: term
  def sigil_z(term), do: term
  defmacro sigil_Y(term, _modifiers), do: term

  def uses_privates(a), do: {private(a), private_mac(a)}
end
