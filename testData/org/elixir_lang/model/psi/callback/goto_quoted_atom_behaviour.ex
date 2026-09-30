defmodule :"goto.quoted" do
  @callback perform() :: any
end

defmodule GotoQuotedImpl do
  @behaviour :"goto.quoted"

  def per<caret>form, do: :ok
end
