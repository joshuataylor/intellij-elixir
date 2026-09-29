defmodule Capture do
  def run(x), do: x
  def captures, do: {&run/1, &Capture.run/1, &run/2}
end
