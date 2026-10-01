# Writes the `.beam` fixtures into `ebin/` beside this script. Run from the repository root:
#
#     mise exec -- elixir testData/org/elixir_lang/psi/scope/callable_table/beam_mirror/generate.exs
#
# The checked-in beams were compiled with Elixir 1.20.4 on Erlang/OTP 29.
directory = Path.join(Path.dirname(__ENV__.file), "ebin")

for size <- [10, 100, 1000] do
  functions =
    for i <- 0..(size - 1) do
      "  def f_#{i}(a), do: f_#{rem(i + 1, size)}(a)\n"
    end

  uses =
    for i <- 0..4 do
      "  def use_#{i}(a), do: target(a)\n"
    end

  source = """
  defmodule CallableTable.Size#{size} do
  #{functions}  def target(a), do: a
  #{uses}end
  """

  for {module, binary} <- Code.compile_string(source) do
    File.write!(Path.join(directory, "#{module}.beam"), binary)
  end
end
