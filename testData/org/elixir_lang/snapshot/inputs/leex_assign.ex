defmodule LeexAssign do
  def mount(_params, _session, socket), do: {:ok, assign(socket, :count, 0)}
end
