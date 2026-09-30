'use strict';

// Resolves an IDE version pointer to the build it names today. A pointer's meaning changes under a
// fixed cache key, so every job of a run must see the same build: compose-legs.js resolves once per
// run and hands the legs the build, never the pointer.
//
// Same selection rule as buildSrc/src/main/kotlin/versioning/VersionFetcher.kt.

const { setOutput, addSummary, fail } = require('./actions');

// The only pointer the build understands (build.gradle.kts useDynamicEapVersion).
const POINTER = 'LATEST-EAP-SNAPSHOT';

// intellij-repository artifact id (the declaration's `verify` values) to the product code shared by
// the Products API and IntelliJ Platform Gradle Plugin's IntelliJPlatformType.
const PRODUCT_CODES = {
  ideaIU: 'IU',
  ideaIC: 'IC',
  pycharmPY: 'PY',
  pycharmPC: 'PC',
  rubymine: 'RM',
  webstorm: 'WS',
  goland: 'GO',
  clion: 'CL',
  phpstorm: 'PS',
  riderRD: 'RD',
  mps: 'MPS',
};

function isPointer(version) {
  return version === POINTER;
}

function productCode(product) {
  const code = PRODUCT_CODES[product];
  if (!code) throw new Error(`no product code for ${product}; add it to PRODUCT_CODES in ide-releases.js`);
  return code;
}

function compareBuilds(a, b) {
  const x = a.split('.').map(Number);
  const y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d;
  }
  return 0;
}

async function fetchLatest(code, type) {
  const url = `https://data.services.jetbrains.com/products/releases?code=${code}&type=${type}&latest=true&fields=build`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  const builds = Object.values(await response.json()).flat().map((entry) => entry.build);
  return builds[0] || null;
}

// The newest EAP or RC build, only while it is newer than the newest release; otherwise no pre-release
// is active and `build` is null. Not a fixed type order: the API keeps answering "latest EAP/RC" with
// the previous cycle's build after its release ships. Not dates: MPS publishes an EAP of the next major
// dated before the current major's release.
async function resolvePrerelease(code, latest = fetchLatest) {
  const [eap, rc, release] = await Promise.all(['eap', 'rc', 'release'].map((type) => latest(code, type)));
  const candidate = [eap, rc].filter(Boolean).sort(compareBuilds).pop() || null;
  const active = candidate && (!release || compareBuilds(candidate, release) > 0);
  return { build: active ? candidate : null, release };
}

// A concrete version resolves to itself, without touching the network.
async function resolveBuild(code, version, latest = fetchLatest) {
  if (!isPointer(version)) return { build: version, release: null };
  return resolvePrerelease(code, latest);
}

function noActivePrerelease(version, code, release) {
  return `${version} (${code}): no active EAP or RC above release ${release || '<none>'}`;
}

// The verify job's entry point: PRODUCT and VERSION in, `code` and `build` out. `build` is empty when a
// pointer has no active pre-release, and the caller skips the leg.
async function main() {
  const product = process.env.PRODUCT || '';
  const version = process.env.VERSION || '';
  let code;
  let resolved;
  try {
    code = productCode(product);
    resolved = await resolveBuild(code, version);
  } catch (error) {
    fail(`resolving ${product} ${version}: ${error.message}`, 'IDE version not resolved');
  }

  if (resolved.build) {
    console.log(`Resolved ${product} ${version} to ${resolved.build}`);
  } else {
    const message = `${noActivePrerelease(version, code, resolved.release)}, verification skipped`;
    console.log(`::notice title=No active pre-release::${message}`);
    addSummary(`${message}\n`);
  }
  setOutput('code', code);
  setOutput('build', resolved.build || '');
}

if (require.main === module) main();

module.exports = { isPointer, productCode, compareBuilds, resolvePrerelease, resolveBuild, noActivePrerelease };
