# Changelog fragments

One file per pull request, so two pull requests never edit the same lines and never conflict.
`CHANGELOG.md` is still the changelog - a post-merge job folds these into `## [Unreleased]` and
deletes them, so the committed file stays complete and linkable.

Name the file `<timestamp>-<slug>.md`, where the timestamp is `date -u +%Y%m%d%H%M%S`:

```bash
touch "changelog.d/$(date -u +%Y%m%d%H%M%S)-lift-decompiler-size-limits.md"
```

The prefix is enforced. It keeps the directory sorted in the order entries were written, which is the
order they are assembled in, and it stops two branches picking the same obvious name (`3996.md`) and
colliding - the thing fragments exist to prevent. Write the entry, not the link:

```markdown
---
group: Bug Fixes
---
**Quick Documentation for a `@macrocallback` now shows its `@doc`.** Fixes #3997.
```

- `group` is one of the headings in `changelogGroups` (gradle.properties). `Breaking changes`,
  `Enhancements` and `Bug Fixes` publish to the Marketplace "What's New"; `Threading / Platform
  Hygiene` and `Build / CI` are recorded but not published.
- The body is one bold user-facing sentence, sized for someone deciding whether the update affects
  them. Detail belongs in the pull request, not here.
- Bare `#123` is expanded to a full link during assembly. The pull request link and your `@handle`
  are added then too, from the merge itself - there is no number to guess and no `PR-TBD` to resolve.
- Nested sub-bullets are for the rare case where the user must *do* something (re-add an SDK,
  invalidate caches). Everything else stays one line.

If a change genuinely has nothing to tell users, apply the `no-changelog` label instead.
