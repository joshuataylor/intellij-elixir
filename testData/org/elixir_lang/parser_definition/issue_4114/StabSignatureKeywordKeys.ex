fn %{start: s, end: e} -> e - s end
fn [do: x] -> x end
fn else: x, after: y, catch: z, rescue: w -> {x, y, z, w} end
case opts do
  [do: body] -> body
end
