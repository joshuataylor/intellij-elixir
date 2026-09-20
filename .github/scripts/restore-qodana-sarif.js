'use strict';
// Puts the previous successful run's Qodana SARIF where the upload step expects it, so code scanning
// gets a result for this head without re-running the scan.
//
// Sound only because the caller has established the code tree is byte-identical to that run's head:
// the findings and their line positions are the same tree's, and only the commit they are reported
// against differs.
//
// Gate contract: every failure answers `restored=false` rather than throwing, and the caller then
// runs the scan for real. Reusing is the optimisation; scanning is the correct answer.

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { setOutput, warn } = require('./actions.js');

const RUN_ID = process.env.PREVIOUS_RUN_ID;
const ARTIFACT = process.env.ARTIFACT_NAME || 'qodana-report';
const TARGET = process.env.SARIF_PATH;

// The artifact holds a single qodana-report.zip rather than loose files, so it has to come out
// before anything can be found in it.
function unpackNestedZips(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isFile() && entry.name.endsWith('.zip')) {
      try {
        execFileSync('unzip', ['-q', '-o', path.join(dir, entry.name), '-d', dir], { stdio: 'inherit' });
      } catch (e) {
        // These archives store entries with a leading slash, so unzip strips it and exits 1 - its
        // "completed with warnings" status, not a failure. 2 and above are real.
        if (!e.status || e.status > 1) throw e;
      }
    }
  }
}

function collect(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name === 'qodana.sarif.json') out.push(full);
  }
  return out;
}

// The report carries the same name at several paths - report/results, the root, end/ and start/ -
// and start/ is the state BEFORE the analysis. Picking by walk order could upload that, so choose
// explicitly: the path the scan itself uploads from first, then the root, and never start/.
function findSarif(dir) {
  const candidates = collect(dir).map(f => ({ f, rel: path.relative(dir, f).split(path.sep).join('/') }));
  const usable = candidates.filter(c => !c.rel.startsWith('start/') && !c.rel.includes('/start/'));
  const preferred = usable.find(c => c.rel.endsWith('report/results/qodana.sarif.json'))
    || usable.find(c => c.rel === 'qodana.sarif.json')
    || usable[0];
  return preferred ? preferred.f : null;
}

let restored = false;
try {
  if (!RUN_ID) throw new Error('no previous run id');
  if (!TARGET) throw new Error('SARIF_PATH is not set');

  const staging = fs.mkdtempSync(path.join(process.env.RUNNER_TEMP || '/tmp', 'qodana-restore-'));
  execFileSync('gh', ['run', 'download', RUN_ID, '-n', ARTIFACT, '-D', staging],
    { stdio: ['ignore', 'inherit', 'inherit'] });

  unpackNestedZips(staging);

  const sarif = findSarif(staging);
  if (!sarif) throw new Error(`no qodana.sarif.json in artifact ${ARTIFACT} of run ${RUN_ID}`);

  // Rejecting unparseable JSON here rather than letting the upload step fail on it.
  const parsed = JSON.parse(fs.readFileSync(sarif, 'utf8'));
  const results = (parsed.runs || []).reduce((n, r) => n + (r.results || []).length, 0);

  fs.mkdirSync(path.dirname(TARGET), { recursive: true });
  fs.copyFileSync(sarif, TARGET);
  restored = true;
  console.log(`restored SARIF from run ${RUN_ID} (${results} results) to ${TARGET}`);
} catch (e) {
  warn(`could not reuse the previous Qodana SARIF, scanning instead: ${e.message.split('\n')[0]}`);
}

setOutput('restored', String(restored));
