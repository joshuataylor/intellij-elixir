defmodule Kernel do
  import ImplicitImportIndex.Helper

  def kernel_target(a), do: a

  if true do
    def kernel_conditional(a), do: a
  end

  case :x do
    _ -> def kernel_in_case(a), do: a
  end

  defp kernel_private(a), do: a
  defmacro kernel_macro(a), do: a
  defdelegate kernel_delegated(a), to: ImplicitImportIndex.Helper, as: :helper_only

  quote do
    def kernel_quoted(a), do: a
  end
end

defmodule ImplicitImportIndex.Helper do
  def helper_only(a), do: a
end

defmodule ImplicitImportIndex.User do
  def a, do: kernel_target(1)
  def b, do: kernel_conditional(1)
  def c, do: helper_only(1)
  def d, do: kernel_private(1)
  def e, do: kernel_in_case(1)
  def f, do: kernel_macro(1)
  def g, do: kernel_delegated(1)
  def h, do: kernel_quoted(1)
end
