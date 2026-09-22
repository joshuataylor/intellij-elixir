defmodule MixGeneratorEmbedTemplateUsage do
  require Mix.Generator

  Mix.Generator.embed_template(:log, "Log: <%= @log %>")

  def usage do
    log_t<caret>
  end
end
