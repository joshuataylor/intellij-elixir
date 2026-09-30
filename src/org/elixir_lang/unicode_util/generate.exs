# Regenerates resources/org/elixir_lang/unicode_util/graphemes-<unicode version>.tsv: the properties that Erlang/OTP's
# unicode_util:gc/1 segments grapheme clusters by, for every Unicode version the OTP releases in
# .github/ci-versions.json were built with. Run it from the repository root:
#
#     mise exec -- elixir src/org/elixir_lang/unicode_util/generate.exs
#
# The properties are read as lib/stdlib/uc_spec/gen_unicode_mod.escript reads them, from that release's own uc_spec
# files. verify.exs then checks each table against unicode_util:gc/1 of every release that uses it, through
# `mise exec`, so every pair in the declaration must be installed in mise.
unless Version.match?(System.version(), ">= 1.18.0") do
  raise "generate.exs needs Elixir 1.18 or later for JSON; this is #{System.version()}"
end

here = __DIR__
root = Path.expand("../../../..", here)
declaration_path = List.first(System.argv()) || Path.join(root, ".github/ci-versions.json")
output_directory = Path.join(root, "resources/org/elixir_lang/unicode_util")
verifier = Path.join(here, "verify.exs")
otp_git = "https://github.com/erlang/otp"
files = ~w(GraphemeBreakProperty.txt emoji-data.txt IndicSyllabicCategory.txt)

beam = declaration_path |> File.read!() |> JSON.decode!() |> Map.fetch!("beam")

releases =
  [beam["baseline"] | beam["additional"] || []]
  |> Enum.map(&{&1["elixir"], &1["otp"]})
  |> Enum.uniq_by(fn {_, otp} -> otp end)
  |> Enum.sort_by(fn {_, otp} -> otp |> String.split(".") |> Enum.map(&String.to_integer/1) end)

{:ok, _} = Application.ensure_all_started(:inets)
{:ok, _} = Application.ensure_all_started(:ssl)

download = fn otp, file ->
  url = ~c"https://raw.githubusercontent.com/erlang/otp/OTP-#{otp}/lib/stdlib/uc_spec/#{file}"

  ssl = [
    verify: :verify_peer,
    cacerts: :public_key.cacerts_get(),
    customize_hostname_check: [match_fun: :public_key.pkix_verify_hostname_match_fun(:https)]
  ]

  case :httpc.request(:get, {url, []}, [ssl: ssl, timeout: 300_000], body_format: :binary) do
    {:ok, {{_, 200, _}, _, body}} -> body
    # IndicSyllabicCategory.txt arrived with the conjunct rule.
    {:ok, {{_, 404, _}, _, _}} when file == "IndicSyllabicCategory.txt" -> ""
    other -> raise "could not download #{url}: #{inspect(other)}"
  end
end

# gen_unicode_mod.escript's parse_properties/2: `<code point>[..<code point>] ; <value>`, the value lowercased.
properties = fn content ->
  for line <- String.split(content, ["\r\n", "\n"]),
      [data | _] = String.split(line, "#", parts: 2),
      [range, value] <- [data |> String.split(";") |> Enum.map(&String.trim/1)],
      range != "" do
    [first, last] =
      case String.split(range, "..") do
        [codepoint] -> [codepoint, codepoint]
        [first, last] -> [first, last]
      end

    {String.downcase(value), String.to_integer(first, 16), String.to_integer(last, 16)}
  end
end

# One letter per property unicode_util:gc/1 tests. Extend and SpacingMark share one, as its is_extend/1 does.
letters = %{
  "cr" => "c",
  "lf" => "c",
  "control" => "c",
  "prepend" => "p",
  "l" => "L",
  "v" => "V",
  "t" => "T",
  "lv" => "v",
  "lvt" => "t",
  "regional_indicator" => "r",
  "extended_pictographic" => "x",
  "extend" => "e",
  "spacingmark" => "e",
  "zwj" => "z",
  "consonant" => "k",
  "vowel_independent" => "i",
  "virama" => "l",
  "invisible_stacker" => "s"
}

build = fn sources ->
  emoji = for {"extended_pictographic", _, _} = property <- properties.(sources["emoji-data.txt"]), do: property
  rest = properties.(sources["GraphemeBreakProperty.txt"]) ++ properties.(sources["IndicSyllabicCategory.txt"])

  (emoji ++ rest)
  |> Enum.flat_map(fn {value, first, last} ->
    case Map.fetch(letters, value) do
      {:ok, letter} -> for codepoint <- first..last, do: {codepoint, letter}
      :error -> []
    end
  end)
  |> Enum.group_by(&elem(&1, 0), &elem(&1, 1))
  |> Enum.map(fn {codepoint, flags} -> {codepoint, flags |> Enum.uniq() |> Enum.sort() |> Enum.join()} end)
  |> Enum.sort()
  |> Enum.chunk_while(
    nil,
    fn
      {codepoint, flags}, {first, last, flags} when codepoint == last + 1 -> {:cont, {first, codepoint, flags}}
      {codepoint, flags}, nil -> {:cont, {codepoint, codepoint, flags}}
      {codepoint, flags}, range -> {:cont, range, {codepoint, codepoint, flags}}
    end,
    fn
      nil -> {:cont, nil}
      range -> {:cont, range, nil}
    end
  )
end

# The rules each release's gc/1 applies beyond the table, read from the escript that generated it.
rules = fn otp ->
  escript = download.(otp, "gen_unicode_mod.escript")

  cond do
    String.contains?(escript, "vowel_independent") -> "wider_conjuncts"
    String.contains?(escript, "is_indic_consonant") -> "conjuncts"
    true -> "none"
  end
end

tables =
  releases
  |> Enum.map(fn {elixir, otp} ->
    sources = Map.new(files, &{&1, download.(otp, &1)})
    [_, unicode] = Regex.run(~r/^# GraphemeBreakProperty-(\d+\.\d+)\.\d+\.txt/, sources["GraphemeBreakProperty.txt"])
    {unicode, {elixir, otp, sources}}
  end)
  |> Enum.group_by(&elem(&1, 0), &elem(&1, 1))
  |> Enum.sort_by(fn {unicode, _} -> Version.parse!(unicode <> ".0") end, &(Version.compare(&1, &2) != :gt))

File.mkdir_p!(output_directory)

for {unicode, [{_elixir, otp, sources} | _] = users} <- tables do
  if users |> Enum.map(fn {_, _, sources} -> sources end) |> Enum.uniq() |> length() > 1 do
    raise "releases built with Unicode #{unicode} ship different uc_spec files: #{Enum.map_join(users, ", ", &elem(&1, 1))}"
  end

  rows = build.(sources)
  path = Path.join(output_directory, "graphemes-#{unicode}.tsv")

  File.write!(path, [
    "# Generated by src/org/elixir_lang/unicode_util/generate.exs from #{otp_git}/tree/OTP-#{otp}/lib/stdlib/uc_spec\n",
    "# for OTP #{Enum.map_join(users, ", ", &elem(&1, 1))}. Do not edit.\n",
    "#\n",
    "#     <first code point>\\t<last code point>\\t<properties>\n",
    "#\n",
    "# c control, p prepend, L V T LV=v LVT=t Hangul, r regional indicator, x extended pictographic, e extend or\n",
    "# spacing mark, z zero width joiner, k consonant, i independent vowel, l virama, s invisible stacker\n",
    Enum.map(rows, fn {first, last, flags} ->
      [Integer.to_string(first, 16), ?\t, Integer.to_string(last, 16), ?\t, flags, ?\n]
    end)
  ])

  IO.puts("Unicode #{unicode}: #{length(rows)} ranges -> #{Path.relative_to(path, root)}")

  for {elixir, otp, _sources} <- users do
    # A bare elixir@<version> is the build for the newest OTP it supports, which an older OTP cannot load.
    [major | _] = String.split(otp, ".")

    case System.cmd("mise", ["exec", "elixir@#{elixir}-otp-#{major}", "erlang@#{otp}", "--", "elixir", verifier, path, rules.(otp)],
           stderr_to_stdout: true
         ) do
      {log, 0} -> IO.write("  OTP #{otp}: " <> log)
      {log, status} -> raise "verify.exs under OTP #{otp} exited #{status}:\n#{log}"
    end
  end
end

copyrights = fn sources ->
  sources
  |> Map.values()
  |> Enum.flat_map(&Regex.scan(~r/^# (© \d{4} Unicode®, Inc\.)\r?$/mu, &1, capture: :all_but_first))
  |> List.flatten()
  |> Enum.uniq()
  |> Enum.join(", ")
end

unicode_license = """
UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 1991-2026 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.
"""

File.write!(Path.join(output_directory, "NOTICE.md"), [
  """
  <!-- Generated by src/org/elixir_lang/unicode_util/generate.exs. Do not edit. -->

  # Unicode data for Erlang/OTP's grapheme clusters

  Each `graphemes-<Unicode version>.tsv` beside this file lists, for one Unicode version, the properties that
  Erlang/OTP's `unicode_util:gc/1` segments grapheme clusters by. They were derived by
  `src/org/elixir_lang/unicode_util/generate.exs`, which also writes this file, from the Unicode Character Database
  files in `lib/stdlib/uc_spec/` of
  #{otp_git},
  read as that repository's `lib/stdlib/uc_spec/gen_unicode_mod.escript` reads them. `src/org/elixir_lang/unicode_util/`
  ports the segmentation that escript generates. Erlang/OTP is copyright Ericsson AB and licensed under the Apache
  License, Version 2.0, the licence of this repository (`LICENSE`). The Unicode data is licensed under the Unicode
  License v3 below.

  ## Sources

  | Unicode | OTP | Copyright |
  |---|---|---|
  """,
  for {unicode, [{_, _, sources} | _] = users} <- tables do
    "| #{unicode} | #{Enum.map_join(users, ", ", &"[#{elem(&1, 1)}](#{otp_git}/tree/OTP-#{elem(&1, 1)}/lib/stdlib/uc_spec)")} | #{copyrights.(sources)} |\n"
  end,
  "\n## Unicode License v3\n\n",
  unicode_license |> String.trim_trailing() |> String.split("\n") |> Enum.map_join("\n", &String.trim_trailing("    " <> &1)),
  "\n"
])
