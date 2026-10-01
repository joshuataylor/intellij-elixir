defmodule Src.Defdelegate.XNfc do
  defdelegate src_defdelegate_x_nfc_snoć(q, x), to: Src.Defdelegate.XNfc.Target

  def local_site(a, b), do: src_defdelegate_x_nfc_snoć(a, b) # @local
end
