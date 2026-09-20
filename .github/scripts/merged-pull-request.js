'use strict';
// The pull request a push to main came from, for the changelog entry's link and author.
//
// Reports empty rather than failing when there is none - a direct commit to main is legitimate, and
// the assembly step is skipped rather than the push being reddened.

const { execFileSync } = require('child_process');
const fs = require('fs');
const { setOutput, warn } = require('./actions.js');

let number = '';
let author = '';
let fragments = [];
try {
  const out = execFileSync('gh',
    ['api', `repos/${process.env.REPO}/commits/${process.env.SHA}/pulls`],
    { encoding: 'utf8', maxBuffer: 1 << 26 });
  const pulls = JSON.parse(out).filter(p => p.merged_at);
  // Newest merge wins if a commit somehow belongs to more than one.
  const pull = pulls.sort((a, b) => new Date(b.merged_at) - new Date(a.merged_at))[0];
  if (pull) {
    number = String(pull.number);
    author = pull.user.login;
    // Only this pull request's own fragments. Assembling whatever happens to be in the directory
    // would attribute a fragment to the wrong pull request when two merge close together - the
    // concurrency group serialises the runs, it does not stop the first seeing the second's files.
    const filesOut = execFileSync('gh',
      ['api', `repos/${process.env.REPO}/pulls/${number}/files`, '--paginate', '--jq', '.[].filename'],
      { encoding: 'utf8', maxBuffer: 1 << 26 });
    fragments = filesOut.split('\n')
      .filter(f => /^changelog\.d\/\d{14}-[a-z0-9]+(-[a-z0-9]+)*\.md$/.test(f))
      .filter(f => fs.existsSync(f));
    console.log(`merged pull request #${number} by @${author}, ${fragments.length} fragment(s)`);
  } else {
    console.log('no merged pull request for this commit; nothing to assemble');
  }
} catch (e) {
  warn(`could not identify the merged pull request: ${e.message.split('\n')[0]}`);
}

setOutput('number', number);
setOutput('author', author);
// Space-separated, consumed as `--file` arguments.
setOutput('fragments', fragments.join(' '));
