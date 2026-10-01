defmodule Target do
  def foo(x), do: x
  def foo_bar(x), do: x
  def bar(x), do: x
  def bar_baz(x), do: x
end

defmodule Chain do
  defdelegate size(x), to: Target, as: :bar
end

defmodule Uses do
  require EEx
  require Mix.Generator

  defdelegate foo(x), to: Target
  defdelegate renamed(x), to: Target, as: :bar
  defdelegate chained(x), to: Chain, as: :size
  defexception [:message]
  def fo_local(x), do: x
  EEx.function_from_string(:def, :"quoted_eex", "<%= a %>", [:a])
  Mix.Generator.embed_template(:page, "<%= @a %>")

  def uses do
    fo(1)
    foo(1)
    ren(1)
    chained(1, 2)
    exception(1)
    quoted_eex(1, 2)
    page_template()
  end

  defmacro dynamic(f) do
    quote do
      Uses.unquote(f)(1)
    end
  end
end
