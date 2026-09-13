import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const prototype = new URL('../map-first-prototype.html', import.meta.url).href;
const output = fileURLToPath(new URL('../artifacts/map-first/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const checks = [];
try {
  await page.goto(prototype);
  await page.screenshot({ path: output + 'desktop.png' });
  assert.equal(await page.locator('#editor').isVisible(), true);
  await page.getByRole('button', { name: 'Thu gọn biên tập', exact: true }).click();
  assert.equal(await page.locator('#editor').isVisible(), false);
  await page.getByRole('button', { name: 'Mở biên tập', exact: true }).click();
  assert.equal(await page.locator('#editor').isVisible(), true);
  checks.push('Thu/mở drawer và đổi mode');
  await page.getByRole('button', { name: 'Xuống: Bến Thành', exact: true }).click();
  assert.equal(await page.locator('.stop-name').first().innerText(), 'Công trường Dân Chủ');
  assert.equal(await page.locator('#play').isDisabled(), true);
  await page.locator('.stop-row').first().dragTo(page.locator('.stop-row').nth(2));
  await page.getByRole('button', { name: 'Tính lại bản mẫu', exact: true }).click();
  assert.equal(await page.locator('#play').isDisabled(), false);
  checks.push('Reorder bằng nút/drag, đánh dấu stale, tính lại sơ đồ');
  await page.getByRole('button', { name: '10×', exact: true }).click();
  await page.locator('#play').click();
  await page.waitForTimeout(600);
  await page.locator('#play').click();
  assert.notEqual(await page.locator('#progress-label').innerText(), '0% hành trình');
  checks.push('Playback và tốc độ thời gian');
  await page.getByRole('button', { name: 'Tạo kẹt xe', exact: false }).click();
  await page.mouse.click(720, 500);
  assert.equal(await page.locator('[data-injected]').count(), 1);
  assert.match(await page.locator('#events .event').first().innerText(), /giả lập/);
  checks.push('Đặt sự cố SIM trên bản đồ và cập nhật stream');
  await page.locator('#traffic-toggle').click();
  assert.equal(await page.locator('#traffic-group').isVisible(), false);
  await page.locator('#traffic-toggle').click();
  checks.push('Bật/tắt lớp traffic minh họa');
  await page.reload();
  for (const width of [1024, 390]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 768 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: output + `map-${width}.png` });
    if (width === 390) {
      await page.locator('.mobile-tools [data-sheet=simulator]').click();
      assert.equal(await page.locator('#sim-panel').isVisible(), true);
      assert.equal(await page.locator('#editor').isVisible(), false);
      await page.screenshot({ path: output + 'mobile-simulator.png' });
      await page.locator('.mobile-tools [data-sheet=editor]').click();
      assert.equal(await page.locator('#sim-panel').isVisible(), false);
      assert.equal(await page.locator('#editor').isVisible(), true);
      await page.screenshot({ path: output + 'mobile-editor.png' });
    }
    checks.push(`Responsive ${width}px, không tràn ngang`);
  }
  assert.deepEqual(errors, []);
  await writeFile(output + 'results.json', JSON.stringify({ checks, pageErrors: errors, scope: 'Design prototype only; SVG diagram and fixture interactions, no application/API verification.' }, null, 2));
  console.log(JSON.stringify({ checks, pageErrors: errors }, null, 2));
} finally {
  await browser.close();
}
