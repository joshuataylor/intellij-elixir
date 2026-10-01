defmodule ImportOptions.Replace do
  def before_both, do: {f(1), g(1)}

  import ImportOptions.M, only: [f: 1]

  def between, do: {f(1), g(1)}

  import ImportOptions.M, only: [g: 1]

  def after_both, do: {f(1), g(1)}
end

defmodule ImportOptions.Narrow do
  import ImportOptions.M, only: [f: 1, g: 1]

  def between, do: {f(1), f(1, 2), g(1), mac(1)}

  import ImportOptions.M, except: [f: 1]

  def after_both, do: {f(1), f(1, 2), g(1), mac(1)}
end
