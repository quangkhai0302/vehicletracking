// @vitest-environment node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { expect, test } from 'vitest';
import configuration from '../../vite.config.js';

const readProject = path => readFile(resolve(process.cwd(), path), 'utf8');

test('the production HTML loads only the Vue entry and preserves the CSS root', async () => {
  const html = await readProject('index.html');
  expect(html).toContain('<div id="root"></div>');
  expect(html).toContain('src="/src/main.ts"');
  expect(html).not.toMatch(/\.tsx|tests\/visual/);
  const entry = await readProject('src/main.ts');
  expect(entry).toContain("import App from './App.vue'");
  expect(entry).toContain(".mount('#root')");
});

test('normal development loads local env and keeps the backend-compatible origin', async () => {
  expect(configuration.envDir).toBeUndefined();
  expect(configuration.server).toMatchObject({ port: 5173, strictPort: true });
  const { scripts } = JSON.parse(await readProject('package.json'));
  expect(scripts.dev).toBe('vite');
  expect(scripts['dev:vue']).toBe('npm run dev --');
  expect(scripts['build:vue']).toBe('npm run build --');
  expect(scripts['preview:vue']).toBe('npm run preview --');
});

test('the only application framework plugin is Vue and JSX compiler mode is removed', async () => {
  expect(configuration.plugins.map(plugin => plugin.name)).toEqual(['vite:vue']);
  // tsconfig permits comments; check the relevant settings without parsing JSONC as JSON.
  const tsconfig = await readProject('tsconfig.json');
  expect(tsconfig).not.toMatch(/"jsx"\s*:/);
  expect(tsconfig).toContain('"strict": true');
});

test('package and lockfile contain no legacy React dependency', async () => {
  const pkg = JSON.parse(await readProject('package.json'));
  const lock = JSON.parse(await readProject('package-lock.json'));
  const forbidden = /^(?:react|react-dom|react-router|react-router-dom|lucide-react|@types\/react|@types\/react-dom|@vitejs\/plugin-react|babel-plugin-react-compiler)$/;
  expect(Object.keys({ ...pkg.dependencies, ...pkg.devDependencies }).filter(name => forbidden.test(name))).toEqual([]);
  expect(Object.keys(lock.packages).filter(path => forbidden.test(path.split('node_modules/').at(-1)))).toEqual([]);
  expect(lock.packages[''].dependencies).toEqual(pkg.dependencies);
  expect(lock.packages[''].devDependencies).toEqual(pkg.devDependencies);
});
