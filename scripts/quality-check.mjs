import fs from 'node:fs';

const read = (path) => fs.readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const checks = [
  ['src/main.jsx', /منبع پنهان نمی‌شود/, 'editorial source-transparency copy'],
  ['src/api.js', /Bearer/, 'admin bearer authentication'],
  ['src/api.js', /nasr-media\.com14\.workers\.dev/, 'non-placeholder API endpoint'],
  ['worker/src/index.js', /adminAuthorized/, 'protected newsroom write routes'],
  ['worker/src/index.js', /source_url/, 'source URL exposure'],
  ['worker/src/ingest.js', /lawNasrEligible/, 'Law Nasr ingestion gate'],
  ['worker/src/ingest.js', /news\\?\.google|news\.google|news\\\.google/, 'Google News redirect block'],
  ['index.html', /manifest\.webmanifest/, 'web app manifest']
];

let failed = false;
for (const [path, pattern, label] of checks) {
  const content = read(path);
  if (!pattern.test(content)) {
    failed = true;
    console.error(`FAIL: ${label} (${path})`);
  } else {
    console.log(`PASS: ${label}`);
  }
}
if (failed) process.exit(1);
