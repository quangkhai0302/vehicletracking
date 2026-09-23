// Offline, exact-pixel diagnostics. No tolerance/masking and no baseline updates.
import { readFile, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
const require = createRequire(import.meta.url);
// PNG decoder shipped by the pinned Playwright version; no browser is launched.
const { PNG } = require(resolve(dirname(require.resolve('playwright-core/package.json')), 'lib/utilsBundle.js'));
const [baseline, candidate, reportPath] = process.argv.slice(2);
if (!baseline || !candidate) throw new Error('Usage: node tests/visual/compare.mjs <baseline-dir> <candidate-dir> [report.json]');
const json = async (directory, name) => JSON.parse(await readFile(resolve(directory, name), 'utf8'));
const before = await json(baseline, 'results.json'), after = await json(candidate, 'results.json');
const beforeMetrics = await json(baseline, 'metrics.json'), afterMetrics = await json(candidate, 'metrics.json');
const shots = [], missing = before.shots.filter(name => !after.shots.includes(name));
for (const name of after.shots) {
  if (!before.shots.includes(name)) { missing.push(name); continue; }
  const a = PNG.sync.read(await readFile(resolve(baseline, `${name}.png`))), b = PNG.sync.read(await readFile(resolve(candidate, `${name}.png`)));
  const metricsEqual = JSON.stringify(beforeMetrics[name]) === JSON.stringify(afterMetrics[name]);
  if (a.width !== b.width || a.height !== b.height) { shots.push({ name, metricsEqual, size: [a.width, a.height, b.width, b.height], equal: false }); continue; }
  let changedPixels = 0, minX = a.width, minY = a.height, maxX = 0, maxY = 0;
  for (let p = 0; p < a.width * a.height; p++) {
    if ([0, 1, 2, 3].every(k => a.data[p * 4 + k] === b.data[p * 4 + k])) continue;
    changedPixels++; const x = p % a.width, y = Math.floor(p / a.width);
    minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
  }
  shots.push({ name, metricsEqual, equal: changedPixels === 0, changedPixels, bounds: changedPixels ? [minX, minY, maxX, maxY] : null });
}
const result = { baseline, candidate, browsers: [before.browser, after.browser], errors: { baseline: before.errors, candidate: after.errors },
  missing, exactMatches: shots.filter(shot => shot.equal).length, metricsMatches: shots.filter(shot => shot.metricsEqual).length, total: shots.length, shots };
if (reportPath) await writeFile(reportPath, JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify({ ...result, shots: shots.filter(shot => !shot.equal || !shot.metricsEqual) }, null, 2));
// Diagnostic report only: equality alone is not the complete feature acceptance gate.
