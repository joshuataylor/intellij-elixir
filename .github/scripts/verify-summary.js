'use strict';

// Appends the plugin verifier's reports to the job summary, one `## <IDE build>` section per verified IDE.
// ADD_TO_SUMMARY is a comma-separated list of report file-name patterns, with the aliases `markdown`
// (*.md) and `plain` (*.txt); empty skips the summary. Rendering follows the verifier action this
// replaced: a .md file raw, or folded when the IDE has several files; a .txt file inline up to three
// lines, folded beyond.
//
// Reporting, so like record-leg-status.js it swallows its own errors: a summary must never redden a leg.

const fs = require('fs');
const path = require('path');
const { addSummary, warn } = require('./actions');

// GitHub rejects a step summary over 1024 KiB; report content stops short of that, leaving room for the
// closing markup and the truncation notice.
const CONTENT_LIMIT = 1000 * 1024;
const FENCE = '`````';

function patternsFrom(input) {
  return input
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => ({ markdown: '*.md', plain: '*.txt' })[item.toLowerCase()] || item)
    .filter((pattern) => !pattern.includes('..'));
}

function globToRegExp(pattern) {
  const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.');
  return new RegExp(`^${escaped}$`);
}

function filesUnder(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? filesUnder(full) : entry.isFile() ? [full] : [];
  });
}

function lineCount(text) {
  if (!text) return 0;
  return text.split('\n').length - (text.endsWith('\n') ? 1 : 0);
}

function main() {
  const input = process.env.ADD_TO_SUMMARY || '';
  const reportsDir = process.env.REPORTS_DIR || '';
  const leg = process.env.LEG || 'this leg';
  if (!input.trim()) return;

  const patterns = patternsFrom(input);
  const ideDirs = fs.existsSync(reportsDir)
    ? fs.readdirSync(reportsDir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name)
    : [];

  const out = [];
  let contentBytes = 0;
  let truncated = false;

  // Only the report bodies count against the limit and are cut; the markup around them is always closed.
  function content(text) {
    const remaining = CONTENT_LIMIT - contentBytes;
    let body = Buffer.from(text);
    if (body.length > remaining) {
      body = body.subarray(0, Math.max(remaining, 0));
      truncated = true;
    }
    contentBytes += body.length;
    const kept = body.toString();
    out.push(kept.endsWith('\n') || !kept ? kept : `${kept}\n`);
  }

  function appendFile(file, label, total) {
    const text = fs.readFileSync(file, 'utf8');
    const lines = lineCount(text);
    if (label.toLowerCase().endsWith('.md')) {
      if (total > 1) {
        out.push('<details>\n', `<summary><strong>${label}</strong> (${lines} lines)</summary>\n`, '\n');
        content(text);
        out.push('\n', '</details>\n');
      } else {
        content(text);
        out.push('\n');
      }
    } else if (lines > 3) {
      out.push('<details>\n', `<summary><strong>${label}</strong> (${lines} lines)</summary>\n`, '\n', `${FENCE}\n`);
      content(text);
      out.push(`${FENCE}\n`, '\n', '</details>\n');
    } else {
      out.push(`**${label}**\n`, `${FENCE}\n`);
      content(text);
      out.push(`${FENCE}\n`, '\n');
    }
  }

  if (ideDirs.length === 0) out.push(`## ${leg}\n`, '\n', '**NO REPORTS FOUND**\n', '\n');

  for (const ide of ideDirs.sort()) {
    if (truncated) break;
    out.push(`## ${ide}\n`, '\n');
    const ideDir = path.join(reportsDir, ide);
    const all = filesUnder(ideDir).sort();
    const matched = [];
    for (const pattern of patterns) {
      const regExp = globToRegExp(pattern);
      for (const file of all) {
        if (regExp.test(path.basename(file)) && !matched.includes(file)) matched.push(file);
      }
    }
    if (matched.length === 0) {
      out.push('**NO REPORTS FOUND**\n', '\n');
      continue;
    }
    for (const file of matched) {
      if (truncated) break;
      // Relative to the IDE's directory, so same-named reports of different plugins stay apart.
      appendFile(file, path.relative(ideDir, file).split(path.sep).join('/'), matched.length);
    }
  }

  if (truncated) {
    out.push(
      "\n---\n\n> **Warning:** Job summary truncated to stay within GitHub's 1024KB size limit. See the verification output log for full details.\n",
    );
  }
  addSummary(out.join(''));
}

try {
  main();
} catch (error) {
  warn(`could not write the verification summary: ${error.message}`, 'Verification summary not written');
}
