# Checks one graphemes-<unicode version>.tsv against the running OTP's own unicode_util:gc/1. Every code point is
# segmented in the contexts below, as elixir_interpolation hands gc/1 the rest of a quoted text; code points must
# share their properties exactly when they share their clusters in every context. generate.exs runs it under every
# release that uses the table; it takes the table's path and the release's rules: none, conjuncts or wider_conjuncts.
[path, rules] = System.argv()

table =
  for row <- path |> File.read!() |> String.split("\n", trim: true),
      not String.starts_with?(row, "#"),
      [first, last, flags] = String.split(row, "\t"),
      codepoint <- String.to_integer(first, 16)..String.to_integer(last, 16),
      into: %{},
      do: {codepoint, flags}

# The properties this release's gc/1 tests: the conjunct sets as its escript builds them.
effective = fn codepoint ->
  flags = String.graphemes(Map.get(table, codepoint, ""))
  base = flags -- ~w(k i l s)

  conjunct =
    case rules do
      "none" ->
        []

      "conjuncts" ->
        Enum.filter(flags, &(&1 in ~w(k l)))

      "wider_conjuncts" ->
        consonant = "k" in flags or "i" in flags or codepoint in 0x1B0B..0x1B0C
        linker = "l" in flags or "s" in flags
        if(consonant, do: ["k"], else: []) ++ if(linker, do: ["l"], else: [])
    end

  # gc/1 tests CR and LF by code point.
  Enum.join(base ++ conjunct) <> if(codepoint in [?\r, ?\n], do: <<codepoint>>, else: "")
end

contexts = [
  &[?a, &1],
  &[&1, ?a],
  &[&1, 0x301],
  &[&1, &1],
  &[&1],
  &[&1, 0x0A],
  &[0x0D, &1],
  &[&1, 0x200D, 0x1F600],
  &[0x1F600, 0x200D, &1],
  &[0x1F600, &1, 0x200D, 0x1F600],
  &[0x0915, 0x094D, &1],
  &[0x0915, &1, 0x094D, 0x0915],
  &[0x0915, &1, 0x0915],
  &[0x0915, 0x094D, 0x0301, &1],
  &[&1, 0x094D, 0x0915],
  &[&1, 0x0301, 0x094D, 0x0915],
  &[0x1100, &1],
  &[&1, 0x1161],
  &[&1, 0x11A8],
  &[0xAC00, &1],
  &[0xAC01, &1],
  &[0x1F1E6, &1],
  &[&1, 0x1F1E6],
  &[0x0600, &1]
]

clusters = fn codepoints ->
  codepoints
  |> Stream.unfold(fn
    [] -> nil
    rest -> case :unicode_util.gc(rest) do [cluster | rest] -> {cluster |> List.wrap() |> length(), rest}; [] -> nil end
  end)
  |> Enum.join(",")
end

signature = fn codepoint -> Enum.map(contexts, &clusters.(&1.(codepoint) ++ [?"])) end

groups =
  Enum.concat(0..0xD7FF, 0xE000..0x10FFFF)
  |> Enum.group_by(effective, signature)
  |> Map.new(fn {flags, signatures} -> {flags, Enum.frequencies(signatures)} end)

split =
  for {flags, signatures} <- groups, map_size(signatures) > 1, do: {flags, signatures |> Map.values() |> Enum.sort(:desc)}

unless split == [] do
  raise "code points with the same properties segment differently: #{inspect(split, limit: 20)}"
end

shared =
  groups
  |> Enum.group_by(fn {_, signatures} -> signatures |> Map.keys() |> hd() end, &elem(&1, 0))
  |> Enum.filter(fn {_, flags} -> length(flags) > 1 end)
  |> Enum.map(&elem(&1, 1))

unless shared == [] do
  raise "properties that segment alike: #{inspect(shared)}"
end

IO.puts("#{map_size(groups)} property sets over #{0x110000 - 0x800} code points agree")
