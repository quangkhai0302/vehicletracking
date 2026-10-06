// Fixture-only: no GPS, backend or production requests are made.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, operationalReportFixture, stamp } from '../fixtures/api.mjs';

const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5173';
const origin = new URL(base);
assert(origin.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname), 'Use a local fixture-only frontend');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-060-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const errors = [];

try {
  for (const [width, height] of [[1440, 1000], [390, 844], [320, 740]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', reducedMotion: 'reduce' });
    try {
      const requests = await installFixture(context, base);
      await context.addCookies([{ name: 'XSRF-TOKEN', value: 'fixture-csrf', url: base }]);
      let reportError = false;
      await context.route('**/api/v1/reports/operations/detail?**', async (route) => {
        await route.fulfill({
          status: reportError ? 503 : 200,
          contentType: 'application/json',
          body: JSON.stringify(reportError ? { detail: 'Không tải được báo cáo kiểm thử.' } : operationalReportFixture(new URL(route.request().url()).searchParams)),
        });
      });
      const page = await context.newPage();
      page.on('pageerror', (error) => errors.push(error.stack || error.message));
      await page.clock.setFixedTime(new Date(stamp));
      await page.goto(`${base}/reports`);
      await page.locator('.reports-summary-strip strong').first().waitFor();
      assert.match(await page.locator('main').innerText(), /Báo cáo và thống kê/);
      assert.match(await page.locator('main').innerText(), /Số lượt theo xe/);
      assert.match(await page.locator('main').innerText(), /Số lượt theo tài xế/);
      assert.match(await page.locator('main').innerText(), /Các lần trễ trạm/);
      assert.match(await page.locator('main').innerText(), /Sự cố/);
      assert.match(await page.locator('main').innerText(), /Chưa có dữ liệu/);
      assert.equal(await page.locator('.report-table-card').count(), 4);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `Report page must not overflow at ${width}px`);
      await page.screenshot({ path: `${output}/reports-${width}.png`, fullPage: true });

      const download = page.waitForEvent('download');
      await page.getByRole('button', { name: 'Xuất Excel', exact: true }).click();
      assert.match((await download).suggestedFilename(), /^bao-cao-van-hanh-.*\.csv$/);

      reportError = true;
      await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
      await page.locator('.reports-page [role="alert"]').getByText('Không tải được báo cáo kiểm thử.', { exact: true }).waitFor();
      reportError = false;
      await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
      await page.locator('.reports-summary-strip strong').first().waitFor();
      assert.equal(requests.some((request) => request.path.startsWith('/reports/simulation')), false);
      console.log(`PASS operational report at ${width}px`);
    } finally {
      await context.close();
    }
  }
  assert.deepEqual(errors, []);
} finally {
  await browser.close();
}
