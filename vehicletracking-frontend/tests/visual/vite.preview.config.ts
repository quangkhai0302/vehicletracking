import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Isolated visual-test server; normal development uses vite.config.js and local .env.
export default defineConfig({
  envDir: false,
  build: { outDir: 'dist-vue-preview', rollupOptions: { input: resolve('tests/visual/vue-preview.html') } },
  plugins: [vue(), {
    name: 'vue-migration-preview',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        if (!request.headers.accept?.includes('text/html')) return next();
        if (new URL(request.url ?? '/', 'http://localhost').pathname.startsWith('/huong-dan/')) return next();
        try {
          const html = await readFile(resolve('tests/visual/vue-preview.html'), 'utf8');
          response.setHeader('Content-Type', 'text/html');
          response.end(await server.transformIndexHtml(request.url ?? '/', html));
        } catch (error) { next(error); }
      });
    },
    configurePreviewServer(server) {
      // The candidate HTML is intentionally nested so it cannot replace index.html.
      // Preview must serve that built HTML for cold deep links, not a dev entry.
      server.middlewares.use(async (request, response, next) => {
        if (!request.headers.accept?.includes('text/html')) return next();
        if (new URL(request.url ?? '/', 'http://localhost').pathname.startsWith('/huong-dan/')) return next();
        try {
          const html = await readFile(resolve(server.config.root, server.config.build.outDir, 'tests/visual/vue-preview.html'), 'utf8');
          response.setHeader('Content-Type', 'text/html');
          response.end(html);
        } catch (error) { next(error); }
      });
    },
  }],
});
