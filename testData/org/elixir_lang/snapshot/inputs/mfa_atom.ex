defmodule MfaAtom do
  def run(x), do: x
  def mfas, do: {{MfaAtom, :run, 1}, apply(MfaAtom, :run, [1]), {MfaAtom, :run, 2}, :run}
end
