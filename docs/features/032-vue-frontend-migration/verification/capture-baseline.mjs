// Whitelisted source-only checkpoint: never reads .env or copies node_modules.
// Usage: node capture-baseline.mjs <existing-empty-baseline-directory>
import { createHash } from 'node:crypto';
import { cp, lstat, readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const frontend = resolve(root, 'vehicletracking-frontend');
assert(process.argv[2], 'An existing, empty baseline directory is required');
const target = resolve(process.argv[2]);
assert.notEqual(target, frontend);
assert.equal((await readdir(target)).length, 0, 'Never overwrite an existing checkpoint');
const whitelist = ['src', 'public', 'tests', 'index.html', 'package.json', 'package-lock.json',
  'vite.config.js', 'tsconfig.json', '.oxlintrc.json', '.nvmrc', 'Dockerfile', 'Caddyfile', '.dockerignore'];
const files = [];
async function inspect(path) {
  const info = await lstat(path);
  assert(!info.isSymbolicLink(), `Unexpected symlink: ${relative(frontend, path)}`);
  if (info.isDirectory()) {
    for (const entry of (await readdir(path)).sort()) await inspect(resolve(path, entry));
  } else {
    const name = relative(frontend, path);
    assert(!name.split('/').some(part => part.startsWith('.env')), 'Environment files are excluded');
    const data = await readFile(path);
    let replacement = null;
    if (name === 'src/main.tsx') replacement = 'src/main.ts';
    else if (name === 'src/auth/AuthContext.tsx') replacement = 'src/auth/authState.ts + src/auth/useAuth.ts';
    else if (name === 'src/auth/RouteGuards.tsx') replacement = 'src/app/router.ts';
    else if (name.endsWith('.tsx')) replacement = name.replace(/\.tsx$/, '.vue');
    else if (name.startsWith('src/hooks/')) replacement = name.replace('src/hooks/', 'src/composables/');
    files.push({ path: name, bytes: data.length, sha256: createHash('sha256').update(data).digest('hex'),
      ...(replacement ? { replacement, migrationStatus: 'pending' } : {}) });
  }
}
for (const name of whitelist) await inspect(resolve(frontend, name));
for (const name of whitelist) await cp(resolve(frontend, name), resolve(target, name), { recursive: true, errorOnExist: true, force: false });
const manifest = { capturedAt: new Date().toISOString(), source: 'vehicletracking-frontend working tree, including untracked source',
  excludes: ['.env*', 'node_modules', 'dist', '.git', 'backend', 'credentials'], files };
const manifestJson = JSON.stringify(manifest, null, 2) + '\n';
await writeFile(resolve(target, 'baseline-manifest.json'), manifestJson, { flag: 'wx' });
console.log(JSON.stringify({ target, files: files.length, tsx: files.filter(f => f.path.endsWith('.tsx')).length,
  hooks: files.filter(f => f.path.startsWith('src/hooks/')).length, css: files.filter(f => f.path.startsWith('src/') && f.path.endsWith('.css')).length }, null, 2));
