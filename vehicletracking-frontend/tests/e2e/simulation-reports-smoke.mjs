// Fixture-only: no GPS, backend or production requests are made.
import assert from 'node:assert/strict';
import { mkdir, readFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, operationalReportFixture, stamp } from '../fixtures/api.mjs';

const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5173';
const origin = new URL(base);
assert(origin.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname), 'Use a local fixture-only frontend');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-065-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const errors = [];

try {
  for (const [width, height] of [[1440, 1000], [768, 1000], [390, 844], [320, 740]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale: 'en-US', timezoneId: 'Asia/Ho_Chi_Minh', reducedMotion: 'reduce' });
    try {
      const requests = await installFixture(context, base);
      await context.addCookies([{ name: 'XSRF-TOKEN', value: 'fixture-csrf', url: base }]);
      let reportError = false;
      let reportRequestCount = 0;
      await context.route('**/api/v1/reports/operations/detail?**', async (route) => {
        reportRequestCount++;
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
      assert.match(await page.locator('main').innerText(), /Thống kê theo xe/);
      assert.equal(await page.locator('[role="tabpanel"]:visible').count(), 1);
      assert.equal(await page.locator('#report-filter-fields').isVisible(), false);
      await page.screenshot({ path: `${output}/reports-${width}.png`, fullPage: true });
      await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click();
      assert.equal(await page.locator('#report-filter-fields').isVisible(), true);
      assert.equal(await page.getByLabel('Tháng báo cáo').locator('option').nth(8).innerText(), 'Tháng 9');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `Filters must not overflow at ${width}px`);
      await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click();

      for (const [label, id] of [['Theo xe', 'vehicles'], ['Theo tài xế', 'drivers'], ['Hành khách', 'occupancy'], ['Trễ trạm', 'late-stops'], ['Sự cố', 'incidents']]) {
        await page.getByRole('tab', { name: label, exact: true }).click();
        assert.equal(await page.locator(`[id="report-panel-${id}"]`).isVisible(), true);
        assert.equal(await page.locator('[role="tabpanel"]:visible').count(), 1);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${label} must not overflow at ${width}px`);
        assert.doesNotMatch(await page.locator('main').innerText(), /Người trên xe TB|CRITICAL|MAJOR|OFF_ROUTE_DETECTED/);
        await page.locator(`#report-panel-${id}`).scrollIntoViewIfNeeded();
        await page.screenshot({ path: `${output}/reports-${id}-${width}.png`, fullPage: true });
        if (['occupancy', 'late-stops', 'incidents'].includes(id)) {
          const alignments = await page.locator(`#report-panel-${id} table`).evaluateAll((tables) => tables.flatMap((table) =>
            [...table.rows].flatMap((row) => [...row.cells].map((cell, index) => ({ index, alignment: getComputedStyle(cell).textAlign })))));
          assert(alignments.length > 0 && alignments.every(({ index, alignment }) => alignment === (index === 0 ? 'left' : 'center')),
            `${label} must align its first column left and remaining columns center`);
        }
        if (id === 'occupancy') {
          assert.equal(await page.locator('.reports-occupancy-metrics').count(), 0);
          assert.equal(await page.locator('.reports-method-details').count(), 0);
          assert.match(await page.locator('.report-seat-usage').first().innerText(), /50%/);
          await page.locator('#report-panel-occupancy .report-occupancy-nav').getByRole('button', { name: 'Theo ngày' }).click();
          assert.match(await page.locator('[aria-label="Bảng hành khách theo ngày"]').innerText(), /35/);
          await page.locator('#report-panel-occupancy .report-occupancy-nav').getByRole('button', { name: 'Theo trạm' }).click();
          assert.match(await page.locator('[aria-label="Bảng hành khách theo trạm"]').innerText(), /14/);
          await page.locator('#report-panel-occupancy .report-occupancy-nav').getByRole('button', { name: 'Theo chuyến' }).click();
          await page.locator('.report-occupancy-detail-button').first().click();
          assert.match(await page.locator('dialog.employee-occupancy-trip').innerText(), /5/);
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Occupancy details must not overflow');
          await page.screenshot({ path: `${output}/reports-occupancy-details-${width}.png`, fullPage: true });
          await page.keyboard.press('Escape');
          assert.equal(await page.locator('dialog.employee-occupancy-trip').count(), 0);
          assert.equal(await page.locator('.report-occupancy-detail-button').first().evaluate((button) => document.activeElement === button), true, 'Focus returns to the trip opener');
        }
        if (id === 'drivers') {
          const beforeOpen = reportRequestCount;
          const opener = page.locator('.report-driver-trips-button').first();
          await opener.click();
          const dialog = page.locator('dialog.driver-report-trips');
          await dialog.waitFor();
          assert.equal(await dialog.locator('.driver-report-trip').count(), 4);
          assert.match(await dialog.innerText(), /Chuyến #100/);
          assert.match(await dialog.innerText(), /Bến Thành → Suối Tiên/);
          assert.match(await dialog.innerText(), /Hoàn thành/);
          assert.match(await dialog.innerText(), /Đang thực hiện/);
          assert.equal(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth), true, 'Trip dialog does not overflow horizontally');
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
          const box = await dialog.boundingBox();
          assert(box && box.x >= 0 && box.y >= 0 && box.x + box.width <= width + 1 && box.y + box.height <= height + 1);
          await page.screenshot({ path: `${output}/reports-driver-trips-${width}.png`, fullPage: true });
          await page.keyboard.press('Escape');
          await dialog.waitFor({ state: 'detached' });
          assert.equal(await opener.evaluate(el => el === document.activeElement), true, 'Closing details restores focus');
          assert.equal(reportRequestCount, beforeOpen, 'Opening driver details uses the current report snapshot');
        }
      }
      await page.getByRole('tab', { name: 'Sự cố', exact: true }).focus();
      await page.keyboard.press('Home');
      assert.equal(await page.locator('#report-tab-vehicles').getAttribute('aria-selected'), 'true');
      await page.keyboard.press('ArrowRight');
      assert.equal(await page.locator('#report-tab-drivers').getAttribute('aria-selected'), 'true');
      await page.getByRole('button', { name: 'Xem trễ trạm', exact: true }).click();
      assert.equal(await page.locator('#report-panel-late-stops').isVisible(), true);
      await page.getByLabel('Tìm chuyến, xe, tài xế hoặc trạm').fill('không có trạm này');
      await page.getByText('Không có lần trễ trạm nào khớp bộ lọc.', { exact: true }).waitFor();
      await page.getByRole('tab', { name: 'Theo xe', exact: true }).click();
      await page.getByRole('tab', { name: 'Trễ trạm', exact: true }).click();
      assert.equal(await page.getByLabel('Tìm chuyến, xe, tài xế hoặc trạm').inputValue(), 'không có trạm này');
      await page.getByRole('button', { name: 'Xóa lọc trễ trạm', exact: true }).click();

      const download = page.waitForEvent('download');
      await page.getByRole('button', { name: 'Xuất Excel', exact: true }).click();
      const exportedFile = await download;
      assert.match(exportedFile.suggestedFilename(), /^bao-cao-van-hanh-.*\.xlsx$/);
      const workbook = await readFile(await exportedFile.path());
      assert.equal(workbook.subarray(0, 2).toString(), 'PK', 'Excel download must be a ZIP-based XLSX file');
      assert(workbook.includes(Buffer.from('xl/workbook.xml')), 'Excel download must contain the workbook definition');
      await exportedFile.saveAs(`${output}/report-${width}.xlsx`);

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
