defmodule GenServerRequest do
  def ping(pid), do: GenServer.call(pid, :ping)
  def poke(pid), do: GenServer.cast(pid, :poke)
  def handle_call(:ping, _from, state), do: {:reply, :pong, state}
  def handle_cast(:poke, state), do: {:noreply, state}
end
