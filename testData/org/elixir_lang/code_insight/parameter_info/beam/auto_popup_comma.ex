defmodule ParameterInfo.Beam do
  def run do
    Code.eval_string("1 + 1"<caret>)
  end
end
