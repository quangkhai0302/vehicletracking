// Audit the real Vue production entry. No local .env and no browser/backend access.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { build } from 'vite';

const frontend = process.cwd();
const verification = resolve(frontend, '../docs/features/032-vue-frontend-migration/verification');
const manifest = JSON.parse(await readFile(resolve(verification, 'artifacts/react-baseline-manifest.json'), 'utf8'));
const originalSource = manifest.files.filter(file => file.path.startsWith('src/'));
const legacy = originalSource.filter(file => file.path.endsWith('.tsx') || file.path.startsWith('src/hooks/') || file.path === 'src/app/routeConfig.ts');
const retained = originalSource.filter(file => !legacy.includes(file));
const changed = [];
for (const file of retained) {
  const hash = createHash('sha256').update(await readFile(resolve(frontend, file.path))).digest('hex');
  if (hash !== file.sha256) changed.push(file.path);
}
async function sourceFiles(directory) {
  const entries = await readdir(resolve(frontend, directory), { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory()
    ? sourceFiles(`${directory}/${entry.name}`) : [`${directory}/${entry.name}`]))).flat();
}
const source = await sourceFiles('src');
const legacySource = source.filter(path => /\.[jt]sx$/.test(path) || legacy.some(file => file.path === path));
const forbiddenPackages = /^(?:react|react-dom|react-router|react-router-dom|lucide-react|@types\/react|@types\/react-dom|@vitejs\/plugin-react|babel-plugin-react-compiler)$/;
const pkg = JSON.parse(await readFile(resolve(frontend, 'package.json'), 'utf8'));
const lock = JSON.parse(await readFile(resolve(frontend, 'package-lock.json'), 'utf8'));
const reactDependencies = Object.keys({ ...pkg.dependencies, ...pkg.devDependencies, ...pkg.optionalDependencies }).filter(name => forbiddenPackages.test(name));
const reactLockEntries = Object.keys(lock.packages).filter(path => forbiddenPackages.test(path.split('node_modules/').at(-1)));
const modules = [];
await build({ configFile: resolve(frontend, 'vite.config.js'), envDir: false, build: { outDir: 'dist-vue-audit' }, plugins: [{
  name: 'audit-vue-production-graph',
  generateBundle() { modules.push(...this.getModuleIds()); },
}] });
const graph = [...new Set(modules)].map(id => id.replaceAll('\\', '/')).sort();
const reactModules = graph.filter(id => /\/node_modules\/(?:react|react-dom|react-router|react-router-dom|lucide-react)(?:\/|$)/.test(id));
const tsxModules = graph.filter(id => /\/src\/.*\.[jt]sx(?:\?|$)/.test(id));
const fixtureModules = graph.filter(id => /\/tests\//.test(id));
const html = await readFile(resolve(frontend, 'index.html'), 'utf8');
const report = { productionEntry: html.includes('src="/src/main.ts"') && !html.includes('/src/main.tsx') ? 'Vue' : 'unexpected',
  removedLegacyFiles: legacy.length, retainedBaselineFilesChecked: retained.length, baselineCssChecked: retained.filter(file => file.path.endsWith('.css')).length,
  changedBaselineSource: changed, legacySource, reactDependencies, reactLockEntries,
  moduleCount: graph.length, reactModules, tsxModules, fixtureModules };
await writeFile(resolve(frontend, 'dist-vue-audit/vue-audit.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
assert(graph.some(id => id.endsWith('/src/App.vue')), 'Production must build the real Vue app');
assert.equal(report.productionEntry, 'Vue', 'index.html must load the Vue entry');
assert.deepEqual(reactModules, [], 'Production must not import a React runtime');
assert.deepEqual(tsxModules, [], 'Production must not use legacy TSX components');
assert.deepEqual(fixtureModules, [], 'Production must not import test fixtures or test-only entry points');
assert.deepEqual(legacySource, [], 'Replaced React source must be removed');
assert.deepEqual(reactDependencies, [], 'React dependencies must be removed');
assert.deepEqual(reactLockEntries, [], 'Lockfile must not retain React packages');
// Opt-in for migration verification; normal feature work may legitimately edit CSS/services.
if (process.argv.includes('--baseline')) assert.deepEqual(changed, [], 'Retained baseline source changed: inspect before accepting migration parity');
