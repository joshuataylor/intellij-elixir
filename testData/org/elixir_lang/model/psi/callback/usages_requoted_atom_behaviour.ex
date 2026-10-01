defmodule :"usages_requoted" do
  @callback per<caret>form() :: any
end

defmodule UsagesRequotedImpl do
  @behaviour :usages_requoted

  def perform, do: :ok
end
