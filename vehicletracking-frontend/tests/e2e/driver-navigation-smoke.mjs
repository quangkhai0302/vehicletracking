import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { driverOptions, driverSnapshot, encode } from '../unit/fixtures/driverNavigation.ts';

// Browser UI verification only. Backend/HERE/GPS are fixtures; no operational data is changed.
const base = process.env.FRONTEND_URL ?? 'http://127.0.0.1:4185';
const output = process.env.DRIVER_NAV_OUTPUT_DIR ?? '/tmp/vehicletracking-driver-navigation';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const failures = [], results = [];
const state = { status: 'SCHEDULED', revision: null, started: 0, route: driverSnapshot().route, notices: [] };
const counters = { start: 0, apply: 0, navigation: 0, adminStreams: 0, driverGlobalReads: 0 };
let detailStopCount = 2;
function tripDetail() {
  const data = navigation();
  const stops = Array.from({ length: detailStopCount }, (_, index) => ({
    ...data.stops[index === 0 ? 0 : 1], sequenceNumber: index + 1,
    stationName: index === 1 ? 'Trạm dừng - Đại học Y Dược với tên dài để kiểm tra xuống dòng trên điện thoại' : `Trạm ${index + 1}`,
  }));
  return { trip: data.trip, stops, route: data.route };
}
function navigation() {
  const data = driverSnapshot(state.status, state.revision);
  const stamp = new Date().toISOString();
  data.serverTime = stamp; data.route = state.route;
  if (data.position) {
    data.position.latitude += Math.min(.0001, (Date.now() - state.started) / 1000 * .000002);
    data.position.recordedAt = stamp; data.position.receivedAt = stamp;
    data.simulation.elapsedSeconds = (Date.now() - state.started) / 1000;
    data.simulation.durationSeconds = 600;
    data.simulation.frame.latitude = data.position.latitude;
    data.simulation.frame.progressPercent = 25;
    data.simulation.frame.nextStopEtaSeconds = 75;
    data.checkIns = { ...data.checkIns, revision: 1, visits: [{ id: 1, tripId: 7, stopSequence: 1, source: 'SIMULATOR', evidenceKind: 'POINT',
      actualArrivalAt: stamp, simulatedArrivalAt: stamp, detectedAt: stamp, fromSampleId: null, toSampleId: 1, evidenceFraction: 0,
      latitude: 10.77, longitude: 106.7, attemptNumber: 1 }] };
    if (state.revision) data.guidance.maneuver.instruction = 'Rẽ trái theo lộ trình mới';
  }
  return data;
}
function operations() {
  const data = navigation();
  return { serverTime: data.serverTime, trips: [data.trip], positions: data.position ? [data.position] : [],
    simulations: data.simulation ? [data.simulation] : [], checkIns: [data.checkIns], notifications: state.notices };
}
const alternative = driverOptions();
alternative.options[1].sections = [{ ...alternative.options[1].sections[0],
  encodedPolyline: encode([[10.77, 106.7], [10.7703, 106.702], [10.771, 106.701]]),
  instructions: [{ action: 'turn', direction: 'left', instruction: 'Rẽ trái theo lộ trình mới', offset: 1 }] }];
const tiles = '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><rect width="256" height="256" fill="#e8eef0"/><path d="M0 50H256M0 150H256M50 0V256M180 0V256" stroke="#fff" stroke-width="12"/><path d="M0 50H256M0 150H256M50 0V256M180 0V256" stroke="#cad5db" stroke-width="1"/></svg>';
async function context(role, viewport) {
  const ownCounters = { navigation: 0 };
  const ctx = await browser.newContext({ viewport, isMobile: role === 'DRIVER' && viewport.width < 700, hasTouch: viewport.width < 700 });
  await ctx.route('**/vt/**', route => route.fulfill({ contentType: 'image/svg+xml', body: tiles }));
  await ctx.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const json = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/auth/me') return json({ accountId: role === 'ADMIN' ? 1 : 2, username: role.toLowerCase(), role,
      active: true, driverId: role === 'DRIVER' ? 1 : null, driverName: role === 'DRIVER' ? 'Tài xế thử nghiệm' : null });
    if (path === '/auth/csrf') return route.fulfill({ contentType: 'application/json',
      headers: { 'set-cookie': 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax' }, body: '{}' });
    if (path === '/driver/trips/7/navigation') { counters.navigation++; ownCounters.navigation++; return json(navigation()); }
    if (path === '/driver/trips/7/start') { counters.start++; state.status = 'IN_PROGRESS'; state.started = Date.now(); return json(navigation()); }
    if (path === '/driver/trips/7/route-options') return json({ ...alternative, expiresAt: new Date(Date.now() + 120000).toISOString(), routeRevisionId: state.revision });
    if (path.includes('/route-options/') && path.endsWith('/apply')) {
      counters.apply++; assert.equal(route.request().postDataJSON().optionIndex, 1);
      state.revision = 42; state.route = { ...state.route, sections: alternative.options[1].sections };
      state.notices = [{ id: 9, tripId: 7, vehicleId: 1, vehiclePlateNumber: '51B-12345', revisionId: 42,
        type: 'DRIVER_ROUTE_CHANGED', severity: 'MAJOR', title: 'Tài xế đã đổi lộ trình chuyến #7',
        reason: 'Tài xế thử nghiệm đã chọn đường mới.', incidentId: null, affectedStopSequences: '2',
        baselineEtaSeconds: 60, revisedEtaSeconds: 90, createdAt: new Date().toISOString(), readAt: null }];
      return json(navigation());
    }
    if (path === '/telemetry/stream') {
      if (role === 'DRIVER') counters.driverGlobalReads++; else counters.adminStreams++;
      return route.fulfill({ contentType: 'text/event-stream', body: `retry: 300\nevent: snapshot\ndata: ${JSON.stringify(operations())}\n\n` });
    }
    if (path === '/telemetry/snapshot') { if (role === 'DRIVER') counters.driverGlobalReads++; return json(operations()); }
    if (path === '/trips/7/route') return json(state.route);
    if (path === '/trips/7' || path === '/driver/trips/7') return json(tripDetail());
    if (path === '/trips' || path === '/driver/trips') return json([navigation().trip]);
    if (path === '/notifications') return json(state.notices);
    if (path === '/stations') return json(navigation().stations);
    if (path.startsWith('/traffic/')) return json({ source: 'UNAVAILABLE', status: 'UNAVAILABLE', data: [], warning: null });
    if (path.endsWith('/eta')) return json({ tripId: 7, routeId: 1, source: 'UNAVAILABLE', status: 'UNAVAILABLE',
      calculatedAt: new Date().toISOString(), totalRemainingSeconds: 60, baselineRemainingSeconds: 60, stops: [], affectedSegments: [], warning: null });
    if (path.endsWith('/check-ins')) return json(navigation().checkIns);
    if (['/routes', '/drivers', '/vehicles', '/schedules', '/driver/schedules'].includes(path)) return json([]);
    return json({ detail: `Unhandled fixture endpoint ${path}` }, 404);
  });
  const page = await ctx.newPage();
  page.on('pageerror', error => failures.push(error.message));
  return { ctx, page, counters: ownCounters };
}
async function presentation(page, root) {
  const styles = {};
  for (const selector of ['.trip-status', '.trip-summary-vehicle', '.trip-itinerary', '.trip-stop-card', '.stop-order.start', '.stop-order.end', '.trip-times dt']) {
    styles[selector] = await page.locator(`${root} ${selector}`).first().evaluate(el => {
      const css = getComputedStyle(el);
      return { color: css.color, background: css.backgroundImage, backgroundColor: css.backgroundColor,
        borderColor: css.borderColor, borderRadius: css.borderRadius, fontSize: css.fontSize };
    });
  }
  return styles;
}
async function verifyModal(viewport, adminPresentation) {
  const { ctx, page } = await context('DRIVER', viewport);
  await page.goto(`${base}/driver/today`);
  const card = page.locator('.driver-trip-card').first();
  await card.click();
  await page.locator('dialog.driver-trip-modal[open]').waitFor();
  assert.deepEqual(await presentation(page, '.driver-trip-detail'), adminPresentation);
  assert.equal(await page.locator('.driver-trip-detail button').count(), 1);
  assert.equal(await page.locator('.driver-trip-detail').evaluate(el => el.scrollWidth <= el.clientWidth), true);
  assert.equal(await page.locator('.driver-trip-detail-body').evaluate(el => el.scrollWidth <= el.clientWidth), true);
  const box = await page.locator('.driver-trip-modal').boundingBox();
  const footer = await page.locator('.driver-trip-map-link').boundingBox();
  assert(box && box.y >= 0 && box.y + box.height <= viewport.height);
  assert(footer && footer.height >= 44 && footer.y + footer.height <= viewport.height);
  await page.screenshot({ path: `${output}/driver-detail-${viewport.width}.png` });
  for (let i = 0; i < 4; i++) {
    await page.keyboard.press('Tab');
    assert.equal(await page.evaluate(() => document.activeElement === document.body || document.querySelector('dialog').contains(document.activeElement)), true);
  }
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('.driver-trip-modal').count(), 0);
  assert.equal(await card.evaluate(el => document.activeElement === el), true);
  if (viewport.width > 1000) {
    detailStopCount = 0;
    await card.click();
    await page.locator('dialog.driver-trip-modal[open]').waitFor();
    const compactBox = await page.locator('.driver-trip-modal').boundingBox();
    assert(compactBox && compactBox.height < 840, 'Empty itinerary modal must fit its content');
    await page.keyboard.press('Escape');
  }
  detailStopCount = 18;
  await card.click();
  await page.locator('dialog.driver-trip-modal[open]').waitFor();
  const body = page.locator('.driver-trip-detail-body');
  assert.equal(await body.evaluate(el => el.scrollHeight > el.clientHeight), true);
  await body.evaluate(el => { el.scrollTop = el.scrollHeight; });
  const last = await page.locator('.driver-trip-stop-name').last().boundingBox();
  const bodyBox = await body.boundingBox();
  const fixedFooter = await page.locator('.driver-trip-map-link').boundingBox();
  assert(last && bodyBox && last.y >= bodyBox.y && last.y + last.height <= bodyBox.y + bodyBox.height);
  assert(fixedFooter && fixedFooter.y + fixedFooter.height <= viewport.height);
  await page.screenshot({ path: `${output}/driver-detail-${viewport.width}-stops.png` });
  await page.getByRole('button', { name: 'Đóng chi tiết chuyến', exact: true }).click();
  detailStopCount = 2;
  await card.click();
  await page.locator('dialog.driver-trip-modal[open]').waitFor();
  await page.getByRole('link', { name: 'Mở bản đồ và khởi hành', exact: true }).click();
  await page.locator('.driver-leaflet-map').waitFor();
  assert.equal(new URL(page.url()).pathname, '/driver/trips/7/navigate');
  results.push({ driverModalViewport: `${viewport.width}x${viewport.height}`, sameAdminPresentation: true,
    contentSized: true, noHorizontalOverflow: true, longListScrollsWithVisibleFooter: true, escapeAndFocusRestore: true, mapLink: true });
  await ctx.close();
}
try {
  const adminDetail = await context('ADMIN', { width: 1440, height: 1000 });
  await adminDetail.page.goto(`${base}/trips`);
  await adminDetail.page.getByRole('button', { name: 'Mở chi tiết chuyến 7, Tuyến thử nghiệm', exact: true }).click();
  await adminDetail.page.locator('.trip-detail-panel .trip-summary-vehicle').waitFor();
  const adminPresentation = await presentation(adminDetail.page, '.trip-detail-panel');
  await adminDetail.page.screenshot({ path: `${output}/admin-trip-detail.png` });
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 786, height: 900 }, { width: 390, height: 844 }, { width: 320, height: 568 }])
    await verifyModal(viewport, adminPresentation);
  await adminDetail.ctx.close();
  const admin = await context('ADMIN', { width: 1440, height: 1000 });
  await admin.page.goto(`${base}/operations?mode=simulation&tripId=7`);
  await admin.page.locator('.leaflet-container').waitFor();
  const mobile = await context('DRIVER', { width: 390, height: 844 });
  await mobile.page.goto(`${base}/driver/trips/7/navigate`);
  await mobile.page.getByRole('button', { name: 'Bắt đầu chuyến', exact: true }).waitFor();
  assert.equal(await mobile.page.locator('.driver-leaflet-map').count(), 1);
  await mobile.page.locator('.live-vehicle-glyph').waitFor();
  assert.equal(await mobile.page.locator('.route-stop-map-marker').count(), 2);
  assert.equal(await mobile.page.locator('.operational-stop-label').count(), 2);
  const stationLabel = await mobile.page.locator('.operational-stop-label').first().boundingBox();
  assert(stationLabel && stationLabel.width >= 80 && stationLabel.height <= 40);
  assert.equal(await mobile.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  assert.equal(await mobile.page.locator('.driver-navigation-header h1').evaluate(el => getComputedStyle(el).color), 'rgb(255, 255, 255)');
  const zoom = mobile.page.locator('.driver-map-host .leaflet-control-zoom-in');
  assert.equal(await zoom.evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(255, 255, 255)');
  assert.equal(await zoom.evaluate(el => getComputedStyle(el).color), 'rgb(51, 65, 85)');
  const zoomBox = await zoom.boundingBox();
  assert(zoomBox && zoomBox.width >= 44 && zoomBox.height >= 44);
  await mobile.page.screenshot({ path: `${output}/mobile-before-start.png` });
  await mobile.page.getByRole('button', { name: 'Bắt đầu chuyến', exact: true }).click();
  await mobile.page.getByRole('button', { name: 'Đổi đường', exact: true }).waitFor();
  assert.equal(counters.start, 1);
  await mobile.page.getByText('1/2 trạm đã check-in', { exact: true }).waitFor();
  assert.equal(await mobile.page.locator('[role="progressbar"]').getAttribute('aria-valuenow'), '25');
  assert(await mobile.page.locator('.driver-navigation-summary').textContent().then(text => text.includes('20 km/h') && text.includes('1p 15s')));
  await mobile.page.locator('.driver-trip-stops summary').click();
  await mobile.page.getByRole('button', { name: 'Xem trạm 2: Trạm 2', exact: true }).click();
  await mobile.page.locator('.simulation-stop-popup').waitFor();
  const driverPopup = await mobile.page.locator('.simulation-stop-popup').textContent();
  assert(driverPopup.includes('Địa chỉ Trạm 2') && driverPopup.includes('50 m') && driverPopup.includes('Trạm kế tiếp'));
  const driverPopupNode = await mobile.page.locator('.simulation-stop-popup').elementHandle();
  await mobile.page.waitForTimeout(1200);
  assert.equal(await driverPopupNode.evaluate(el => el.isConnected), true);
  const popupBox = await mobile.page.locator('.simulation-stop-info-popup').boundingBox();
  const guidanceBox = await mobile.page.locator('.driver-guidance').boundingBox();
  const mapBox = await mobile.page.locator('.driver-map-host').boundingBox();
  assert(popupBox && guidanceBox && mapBox && popupBox.y >= guidanceBox.y + guidanceBox.height && popupBox.y + popupBox.height <= mapBox.y + mapBox.height);
  await mobile.page.screenshot({ path: `${output}/mobile-station-popup.png` });
  await mobile.page.locator('.leaflet-popup-close-button').click();
  await mobile.page.locator('.driver-trip-stops summary').click();
  await mobile.page.getByRole('button', { name: 'Xem toàn tuyến', exact: true }).click();
  const driverGlyph = await mobile.page.locator('.live-vehicle-glyph').innerHTML();
  await admin.page.locator('.live-vehicle-glyph').first().waitFor();
  assert.equal(await admin.page.locator('.live-vehicle-glyph').first().innerHTML(), driverGlyph);
  await admin.page.locator('.route-stop-map-marker.end').click();
  await admin.page.locator('.simulation-stop-popup').waitFor();
  assert.equal(await admin.page.locator('.simulation-stop-popup').textContent(), driverPopup);
  const driverStop = mobile.page.locator('.route-stop-map-marker.end');
  const adminStop = admin.page.locator('.route-stop-map-marker.end');
  assert.equal(await driverStop.evaluate(el => getComputedStyle(el).getPropertyValue('--route-stop-accent').trim()),
    await adminStop.evaluate(el => getComputedStyle(el).getPropertyValue('--route-stop-accent').trim()));
  await admin.page.locator('.leaflet-popup-close-button').click();
  results.push({ commonVehicleIcon: true, numberedStationIcons: 2, permanentStationLabels: 2, sameStationPopupAndColors: true, popupSurvivesPolling: true, speedAndEtaAndCheckIns: true });
  await mobile.page.getByRole('button', { name: 'Đổi đường', exact: true }).click();
  await mobile.page.getByRole('radio').nth(1).check();
  await mobile.page.getByText('Đường màu cam: đang xem thử, chưa áp dụng').waitFor();
  assert.equal(state.revision, null); assert.equal(counters.apply, 0);
  await mobile.page.getByRole('button', { name: 'Xác nhận đổi đường', exact: true }).scrollIntoViewIfNeeded();
  await mobile.page.screenshot({ path: `${output}/mobile-preview.png` });
  await mobile.page.getByRole('button', { name: 'Xác nhận đổi đường', exact: true }).click();
  await mobile.page.getByText('Rẽ trái theo lộ trình mới', { exact: true }).waitFor();
  await admin.page.getByText('Tài xế đã đổi lộ trình chuyến #7. Tài xế thử nghiệm đã chọn đường mới.', { exact: true }).waitFor({ timeout: 15000 });
  assert.equal(counters.apply, 1); assert.equal(counters.driverGlobalReads, 0);
  await mobile.page.screenshot({ path: `${output}/mobile-running.png` });
  await admin.page.screenshot({ path: `${output}/admin-synchronized.png` });
  results.push({ viewport: '390x844', noHorizontalOverflow: true, startOnce: true, previewBeforeApply: true, adminNotified: true, driverGlobalReads: 0 });
  const desktop = await context('DRIVER', { width: 1440, height: 1000 });
  await desktop.page.goto(`${base}/driver/trips/7/navigate`);
  await desktop.page.getByText('Rẽ trái theo lộ trình mới', { exact: true }).waitFor();
  assert.equal(await desktop.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  const sidebar = await desktop.page.locator('.driver-navigation-sidebar').boundingBox();
  assert(sidebar && sidebar.width >= 300);
  await desktop.page.getByRole('button', { name: 'Theo dõi vị trí xe', exact: true }).click();
  await desktop.page.locator('.driver-trip-stops summary').click();
  await desktop.page.screenshot({ path: `${output}/desktop-running.png` });
  results.push({ viewport: '1440x1000', sidebarWidth: sidebar.width, synchronizedRevision: state.revision });
  const beforeClose = mobile.counters.navigation;
  await mobile.page.getByRole('link', { name: 'Quay lại chuyến của tôi', exact: true }).click();
  await mobile.page.getByText('Xin chào, Tài xế thử nghiệm', { exact: true }).waitFor();
  assert.equal(state.status, 'IN_PROGRESS');
  await desktop.page.waitForTimeout(1500);
  assert.equal(mobile.counters.navigation, beforeClose);
  results.push({ closingDriverMapDoesNotStopSimulation: true, navigationRequests: beforeClose });
  assert.deepEqual(failures, []);
  await writeFile(`${output}/result.json`, JSON.stringify({ results, counters, failures, fixtureOnly: true }, null, 2));
  console.log(JSON.stringify({ results, counters, failures, output }, null, 2));
} finally { await browser.close(); }
