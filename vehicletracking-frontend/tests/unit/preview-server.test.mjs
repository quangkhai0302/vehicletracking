import { beforeEach, expect, test, vi } from 'vitest';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import configuration from '../visual/vite.preview.config.ts';

vi.mock('node:fs/promises', async importOriginal => {
  const actual = await importOriginal(), readFile = vi.fn();
  return { ...actual, readFile, default: { ...actual.default, readFile } };
});
const plugin = configuration.plugins?.find(item => item && typeof item === 'object' && 'name' in item && item.name === 'vue-migration-preview');
beforeEach(() => { vi.mocked(readFile).mockReset(); vi.mocked(readFile).mockResolvedValue('<div id="root">Vue fixture</div>'); });

// Invoke middleware with lightweight request/response fixtures; no socket/server
// is opened, so these tests do not stand in for actual browser/deep-link checks.
async function request(mode, url, accept) {
  const handlers = [];
  const transform = vi.fn(async (_url, html) => html + '<!-- transformed -->');
  const server = { config: { root: process.cwd(), build: { outDir: 'dist-vue-preview' } },
    middlewares: { use: handler => handlers.push(handler) }, transformIndexHtml: transform };
  const hook = mode === 'dev' ? plugin.configureServer : plugin.configurePreviewServer;
  if (typeof hook !== 'function') throw new Error('Missing preview server hook');
  await Reflect.apply(hook, {}, [server]);
  const response = { setHeader: vi.fn(), end: vi.fn() }, next = vi.fn();
  await handlers[0]({ url, headers: { accept } }, response, next);
  return { response, next, transform };
}
test('Vue development cold deep links load candidate HTML and apply Vite HTML transform', async () => {
  const { response, next, transform } = await request('dev', '/operations?mode=simulation', 'text/html');
  expect(readFile).toHaveBeenCalledWith(resolve('tests/visual/vue-preview.html'), 'utf8');
  expect(transform).toHaveBeenCalledWith('/operations?mode=simulation', '<div id="root">Vue fixture</div>');
  expect(response.end).toHaveBeenCalledWith('<div id="root">Vue fixture</div><!-- transformed -->'); expect(next).not.toHaveBeenCalled();
});
test('isolated build preview deep links use built HTML, never a dev entry', async () => {
  const { response, transform } = await request('build', '/driver/schedules', 'text/html');
  expect(readFile).toHaveBeenCalledWith(resolve('dist-vue-preview/tests/visual/vue-preview.html'), 'utf8');
  expect(transform).not.toHaveBeenCalled(); expect(response.end).toHaveBeenCalledWith('<div id="root">Vue fixture</div>');
});
test.each(['dev', 'build'])('%s preview does not intercept public guide, assets or JSON', async mode => {
  for (const [url, accept] of [['/huong-dan/index.html?section=trips', 'text/html'], ['/assets/chunk.js', '*/*'], ['/api/v1/trips', 'application/json']]) {
    const { response, next } = await request(mode, url, accept); expect(next).toHaveBeenCalledOnce(); expect(response.end).not.toHaveBeenCalled();
  }
  expect(readFile).not.toHaveBeenCalled();
});
test('missing candidate HTML forwards a controlled middleware error', async () => {
  const failure = new Error('Fixture missing build'); vi.mocked(readFile).mockRejectedValueOnce(failure);
  const { response, next } = await request('build', '/dashboard', 'text/html');
  expect(response.end).not.toHaveBeenCalled(); expect(next).toHaveBeenCalledWith(failure);
});
