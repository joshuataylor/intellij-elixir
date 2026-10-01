# Writes two versions of one module's `.beam`, `v1/` and `v2/` beside this script, the second declaring one more
# function. Run from the repository root:
#
#     mise exec -- elixir testData/org/elixir_lang/psi/scope/callable_table/replaced/generate.exs
#
# The checked-in beams were compiled with Elixir 1.20.4 on Erlang/OTP 29.
for {version, functions} <- [{"v1", "  def one, do: 1\n"}, {"v2", "  def one, do: 1\n  def two, do: 2\n"}] do
  directory = Path.join(Path.dirname(__ENV__.file), version)
  File.mkdir_p!(directory)

  for {module, binary} <- Code.compile_string("defmodule CallableTable.Replaced do\n#{functions}end\n") do
    File.write!(Path.join(directory, "#{module}.beam"), binary)
  end
end
