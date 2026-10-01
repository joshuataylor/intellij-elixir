defmodule Reach.OuterLive do
  use Reach.Using

  def own_live, do: injected(4)
end
