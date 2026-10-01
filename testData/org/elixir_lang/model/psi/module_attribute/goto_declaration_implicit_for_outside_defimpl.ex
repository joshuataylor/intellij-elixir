defmodule ImplicitForOuter do
  defmodule ImplicitForTarget do
    def describe(_), do: @f<caret>or
  end
end
