'use strict';
// Folds changelog.d/*.md fragments into CHANGELOG.md's "## [Unreleased]" section.
//
// Fragments exist so two pull requests never edit the same lines of CHANGELOG.md. They carry only a
// group and a sentence: the pull request number and the author are known at merge time, so the link
// is written here rather than guessed by the author (no PR-TBD to resolve).
//
// Run: node .github/scripts/assemble-changelog.js --pr 4146 --author sh41 [--file changelog.d/x.md ...]
// With no --file, every fragment except README.md is taken. --check parses and prints, writing nothing.

const fs = require('fs');
const path = require('path');

const REPO = 'intellij-elixir/intellij-elixir';
const CHANGELOG = 'CHANGELOG.md';
const DIR = 'changelog.d';

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseArgs(argv) {
  const out = { files: [], check: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--pr') out.pr = argv[++i];
    else if (a === '--author') out.author = argv[++i];
    else if (a === '--file') out.files.push(argv[++i]);
    else if (a === '--check') out.check = true;
    else fail(`unknown argument: ${a}`);
  }
  return out;
}

// `---\ngroup: Bug Fixes\n---\nbody` - deliberately not a YAML dependency; the only key is `group`.
function parseFragment(file) {
  const raw = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
  const m = /^---\n([\s\S]*?)\n---\n([\s\S]*)$/.exec(raw);
  if (!m) fail(`${file}: expected '---' frontmatter with a group, then the entry body`);
  const groupLine = m[1].split('\n').map(l => l.trim()).find(l => l.startsWith('group:'));
  if (!groupLine) fail(`${file}: frontmatter has no 'group:' key`);
  const group = groupLine.slice('group:'.length).trim();
  const body = m[2].trim();
  if (!group) fail(`${file}: 'group:' is empty`);
  if (!body) fail(`${file}: no entry body after the frontmatter`);
  return { file, group, body };
}

// A bare #123 is the author's shorthand; CHANGELOG.md carries full links so it reads on any renderer.
const expandRefs = text =>
  text.replace(/(^|[^\w/[])#(\d+)\b/g, (_, pre, n) => `${pre}[#${n}](https://github.com/${REPO}/issues/${n})`);

function render(fragment, pr, author) {
  const head = `- [#${pr}](https://github.com/${REPO}/pull/${pr}) [@${author}](https://github.com/${author})`;
  // Continuation lines are indented to match, so the Keep a Changelog parser keeps them with the item.
  const body = expandRefs(fragment.body)
    .split('\n')
    .map((line, i) => (i === 0 ? `  - ${line}` : `    ${line}`.replace(/\s+$/, '')))
    .join('\n');
  return `${head}\n${body}`;
}

function insert(changelog, group, entry) {
  const lines = changelog.split('\n');
  const unreleased = lines.findIndex(l => /^## \[Unreleased\]/.test(l));
  if (unreleased < 0) fail(`${CHANGELOG}: no '## [Unreleased]' heading`);
  // The group heading must already exist under Unreleased; inventing one would put it in an order
  // gradle.properties' changelogGroups does not declare.
  let heading = -1;
  for (let i = unreleased + 1; i < lines.length; i++) {
    if (/^## /.test(lines[i])) break;
    if (lines[i].trim() === `### ${group}`) { heading = i; break; }
  }
  if (heading < 0) fail(`${CHANGELOG}: no '### ${group}' under [Unreleased] - check the group name against changelogGroups`);
  let at = heading + 1;
  while (at < lines.length && lines[at].trim() === '') at++;
  lines.splice(at, 0, ...entry.split('\n'));
  return lines.join('\n');
}

const args = parseArgs(process.argv.slice(2));
// <timestamp>-<slug>.md. Enforced so the directory sorts into the order entries were written - which
// is the order they are assembled in - and so two branches cannot pick the same obvious name.
const NAME = /^\d{14}-[a-z0-9]+(-[a-z0-9]+)*\.md$/;

const files = (args.files.length
  ? args.files
  : fs.readdirSync(DIR).filter(f => f.endsWith('.md') && f !== 'README.md').map(f => path.join(DIR, f))
).sort();

for (const f of files) {
  const base = path.basename(f);
  if (base !== 'README.md' && !NAME.test(base)) {
    fail(`${f}: name must be <timestamp>-<slug>.md, e.g. ${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-lift-decompiler-size-limits.md`);
  }
}

if (!files.length) {
  console.log('No changelog fragments to assemble.');
  process.exit(0);
}
if (!args.check && (!args.pr || !args.author)) fail('--pr and --author are required unless --check');

const fragments = files.map(parseFragment);

if (args.check) {
  for (const f of fragments) console.log(`${f.file}\n  group: ${f.group}\n  body:  ${f.body.split('\n')[0]}`);
  process.exit(0);
}

let changelog = fs.readFileSync(CHANGELOG, 'utf8');
for (const fragment of fragments) {
  changelog = insert(changelog, fragment.group, render(fragment, args.pr, args.author));
  console.log(`assembled ${fragment.file} into '${fragment.group}'`);
}
fs.writeFileSync(CHANGELOG, changelog);
for (const f of files) fs.unlinkSync(f);
console.log(`${files.length} fragment(s) folded into ${CHANGELOG} and removed.`);
