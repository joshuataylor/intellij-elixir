defmodule EExKind do
  require EEx

  @kind :def
  EEx.function_from_string(@kind, :dyn, "<%= a %>", [:a])
  def other(a, b, c), do: {a, b, c}
  EEx.function_from_string(@kind, :other, "<%= a %>", [:a])

  def local_wrong, do: dyn(1, 2)
  def mixed_wrong, do: other(1, 2)
end

defmodule EExKindUser do
  def remote_wrong, do: EExKind.dyn(1, 2)
end
