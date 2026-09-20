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

function findSarif(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const hit = findSarif(full);
      if (hit) return hit;
    } else if (entry.name === 'qodana.sarif.json') {
      return full;
    }
  }
  return null;
}

let restored = false;
try {
  if (!RUN_ID) throw new Error('no previous run id');
  if (!TARGET) throw new Error('SARIF_PATH is not set');

  const staging = fs.mkdtempSync(path.join(process.env.RUNNER_TEMP || '/tmp', 'qodana-restore-'));
  execFileSync('gh', ['run', 'download', RUN_ID, '-n', ARTIFACT, '-D', staging],
    { stdio: ['ignore', 'inherit', 'inherit'] });

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
