defmodule ImportOptions.Except do
  import ImportOptions.M, except: [f: 1]

  def u, do: {f(1), f(1, 2), g(1)}
end
