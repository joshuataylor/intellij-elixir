defmodule :"goto_requoted" do
  @callback perform() :: any
end

defmodule GotoRequotedImpl do
  @behaviour :goto_requoted

  def per<caret>form, do: :ok
end
