defmodule CallbackImpl.Behaviour do
  @callback run(term) :: term
end

defmodule CallbackImpl do
  @behaviour CallbackImpl.Behaviour

  def run(x), do: x
end
