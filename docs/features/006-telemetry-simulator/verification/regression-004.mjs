// UI fixtures only. Every /api/ request is intercepted; no application data is written.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../artifacts/regression-004/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.setDefaultTimeout(12000);
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
const checks = [];
const stamp = '2026-09-13T06:00:00Z';
let stations = [
  { id: 1, name: 'Bến Thành', address: 'Đường Lê Lợi, Quận 1', latitude: 10.7723, longitude: 106.6981, checkinRadiusMeters: 50 },
  { id: 2, name: 'Nhà hát Thành phố', address: 'Công trường Lam Sơn, Quận 1', latitude: 10.7765, longitude: 106.7031, checkinRadiusMeters: 50 },
  { id: 3, name: 'Hàng Xanh', address: 'Điện Biên Phủ, Bình Thạnh', latitude: 10.8013, longitude: 106.7118, checkinRadiusMeters: 100 },
  { id: 4, name: 'Thảo Điền', address: 'Xa lộ Hà Nội, TP. Thủ Đức', latitude: 10.8005, longitude: 106.733, checkinRadiusMeters: 80 },
].map((s) => ({ ...s, active: true, createdAt: stamp, updatedAt: stamp }));

function fixturePolyline(points) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const encode = (value) => { let result = ''; while (value > 31) { result += alphabet[(value & 31) | 32]; value >>>= 5; } return result + alphabet[value]; };
  let previous = [0, 0];
  return 'BF' + points.map((point) => point.map((value, axis) => {
    const scaled = Math.round(value * 1e5);
    const delta = scaled - previous[axis]; previous[axis] = scaled;
    return encode(delta < 0 ? -delta * 2 - 1 : delta * 2);
  }).join('')).join('');
}
const polyline = fixturePolyline([[10.7723,106.6981],[10.774,106.701],[10.7765,106.7031],[10.784,106.704],[10.8013,106.7118],[10.798,106.723],[10.8005,106.733]]);
const detail = {
  id: 1, name: 'Tuyến 01 · Bến Thành – Thảo Điền', transportMode: 'CAR', routingProvider: 'HERE',
  totalDistanceMeters: 8600, estimatedTravelDurationSeconds: 1500, baseTravelDurationSeconds: 1200,
  totalDwellDurationSeconds: 120, estimatedTripDurationSeconds: 1620,
  estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp,
  stops: stations.map((s, index) => ({ sequenceNumber: index + 1, role: index === 0 ? 'START' : index === 3 ? 'END' : 'STOP', stationId: s.id, stationName: s.name, latitude: s.latitude, longitude: s.longitude, dwellDurationSeconds: index === 1 || index === 2 ? 60 : 0, distanceFromPreviousMeters: index ? 2800 : 0, travelDurationFromPreviousSeconds: index ? 500 : 0, arrivalOffsetSeconds: index * 540, departureOffsetSeconds: index * 540 + (index === 1 || index === 2 ? 60 : 0) })),
  sections: [{ sectionSequence: 1, destinationStopSequence: 4, encodedPolyline: polyline, distanceMeters: 8600, travelDurationSeconds: 1500, baseTravelDurationSeconds: 1200 }],
};
let routes = [{ ...detail, startStationName: 'Bến Thành', endStationName: 'Thảo Điền', stopCount: 4 }];
let mode = 'normal';
let createdRouteRequest;
let postDelay = 0;
let detailDelay = 0;
let postFailure = false;
let corruptRoute = false;
await context.route('**/api/**', async (route) => {
  if(new URL(route.request().url()).pathname.endsWith('/telemetry/snapshot')) return route.fulfill({json:{serverTime:new Date().toISOString(),positions:[],simulations:[],trips:[]}});
  if(new URL(route.request().url()).pathname.endsWith('/telemetry/stream')) return route.fulfill({contentType:'text/event-stream',body:': fixture heartbeat\\n\\n'});

  const request = route.request();
  const path = new URL(request.url()).pathname;
  const method = request.method();
  const json = (value, status = 200) => route.fulfill({ status, json: value });
  if (path.endsWith('/vehicles') || path.endsWith('/trips')) return json([]);
  if (mode === 'error') return json({ detail: 'Không thể kết nối dịch vụ (fixture kiểm tra).' }, 503);
  if (method === 'GET' && path.endsWith('/stations')) return json(mode === 'empty' ? [] : stations);
  if (method === 'POST' && path.endsWith('/stations')) {
    const station = { ...request.postDataJSON(), id: 10, active: true, createdAt: stamp, updatedAt: stamp };
    stations.push(station); return json(station, 201);
  }
  if (method === 'PUT' && path.includes('/stations/')) {
    const id = Number(path.split('/').pop());
    const station = { ...stations.find((s) => s.id === id), ...request.postDataJSON() };
    stations = stations.map((s) => s.id === id ? station : s); return json(station);
  }
  if (method === 'DELETE' && path.includes('/stations/')) {
    stations = stations.filter((s) => s.id !== Number(path.split('/').pop()));
    return route.fulfill({ status: 204 });
  }
  if (method === 'GET' && path.endsWith('/routes')) return json(mode === 'empty' ? [] : routes);
  if (method === 'GET' && path.includes('/routes/')) {
    const id = Number(path.split('/').pop());
    if (detailDelay && id === 1) await new Promise(resolve => setTimeout(resolve, detailDelay));
    return json({ ...detail, id, name: id === 8 ? 'Tuyến 08 · Hàng Xanh' : detail.name,
      sections: corruptRoute ? [{ ...detail.sections[0], encodedPolyline: 'BROKEN%' }] : detail.sections }).catch(() => {});
  }
  if (method === 'POST' && path.endsWith('/routes')) {
    createdRouteRequest = request.postDataJSON();
    if (postDelay) await new Promise(resolve => setTimeout(resolve, postDelay));
    if (postFailure) return json({ detail: 'HERE tạm thời không khả dụng (fixture).' }, 503);
    const created = { ...detail, id: 2, name: createdRouteRequest.name };
    routes = [{ ...routes[0], ...created }, ...routes]; return json(created, 201);
  }
  return route.abort();
});


const nav = name => page.getByRole('navigation', {name:'Chế độ vận hành'}).getByRole('button', {name,exact:true}).click();
const planTab = name => page.locator('.planning-tabs').getByRole('button', {name,exact:false}).click();
const closeRoute = () => page.locator('.route-drawer-footer').getByRole('button',{name:'Đóng',exact:true}).click();
const stopIds = () => page.locator('.sortable-stop').evaluateAll(items => items.map(item => Number(item.dataset.stationId)));
const screenshot = async name => { await page.waitForTimeout(300); await page.screenshot({path: output + name + '-fixture.png'}); };
async function layout(label) {
  const result = await page.evaluate(() => {
    const rect = document.querySelector('.map-canvas').getBoundingClientRect();
    const overlays = [...document.querySelectorAll('.context-drawer,.operations-dock')].filter(el => el.checkVisibility()).map(el => {
      const r = el.getBoundingClientRect(); return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height};
    });
    return {width:innerWidth,height:innerHeight,map:{x:rect.x,y:rect.y,width:rect.width,height:rect.height},overlays,overflow:document.documentElement.scrollWidth > innerWidth,
      horizontalOverflow:[...document.querySelectorAll('.route-drawer-body,.station-form,.context-drawer')].filter(el=>el.checkVisibility()).some(el=>el.scrollWidth>el.clientWidth+1)};
  });
  assert.deepEqual(result.map,{x:0,y:0,width:result.width,height:result.height},label+' full map');
  assert.equal(result.overflow,false,label+' body overflow');
  assert.equal(result.horizontalOverflow,false,label+' panel overflow');
  if(result.width<1280 || result.height<=650) assert.ok(result.overlays.length<=1,label+' single panel');
  for(const box of result.overlays) assert.ok(box.left>=0 && box.right<=result.width && box.top>=60 && box.bottom<=result.height,label+' contained panel');
  checks.push(label);
}
async function assertMarkersVisible(label) {
  await page.getByRole('button',{name:'Vừa khung lộ trình',exact:true}).click();
  await page.waitForTimeout(350);
  const result=await page.evaluate(()=>{
    const blockers=[...document.querySelectorAll('[data-map-edge],.canvas-map-tools')].filter(el=>el.checkVisibility()).map(el=>el.getBoundingClientRect());
    const points=[...document.querySelectorAll('.route-stop-map-marker')].map(el=>el.getBoundingClientRect());
    return {count:points.length,points:points.map(p=>({left:p.left,top:p.top,right:p.right,bottom:p.bottom})),blockers:blockers.map(p=>({left:p.left,top:p.top,right:p.right,bottom:p.bottom})),clear:points.every(p=>p.left>=0&&p.right<=innerWidth&&p.top>=0&&p.bottom<=innerHeight&&blockers.every(b=>p.right<=b.left||p.left>=b.right||p.bottom<=b.top||p.top>=b.bottom))};
  });
  assert.ok(result.count>0&&result.clear,label+' markers outside overlays '+JSON.stringify(result));
}
try {
  await page.goto('http://127.0.0.1:5173', {waitUntil:'domcontentloaded'});
  await page.getByText('Chưa có xe trong danh mục').waitFor();
  await layout('Desktop 1440 map-first');
  assert.equal(await page.getByRole('button',{name:'Bắt đầu mô phỏng'}).isDisabled(),true);
  assert.equal(await page.getByRole('button',{name:'Tai nạn',exact:true}).isDisabled(),true);
  assert.equal(await page.locator('.vehicle-map-marker').count(),0);
  await screenshot('desktop-tracking');
  await nav('Tuyến & trạm');
  await planTab('Trạm dừng');
  await page.locator('.station-card').first().waitFor();
  await page.getByRole('textbox',{name:'Tìm kiếm trạm'}).fill('Bến Thành');
  await page.locator('.station-card').first().click();
  await page.getByRole('complementary',{name:'Chi tiết trạm',exact:true}).waitFor();
  await page.getByRole('button',{name:'Đóng panel'}).click();
  assert.equal(await page.getByRole('textbox',{name:'Tìm kiếm trạm'}).inputValue(),'Bến Thành');
  await page.getByRole('textbox',{name:'Tìm kiếm trạm'}).fill('');
  await page.getByRole('button',{name:'Thêm trạm mới',exact:true}).click();
  assert.equal(await page.locator('.context-drawer').isVisible(),false);
  await page.getByRole('button',{name:'Lấy tâm bản đồ',exact:true}).click();
  await page.getByRole('textbox',{name:'Tên trạm đón trả khách'}).fill('Trạm fixture Map-first');
  await nav('Theo dõi');
  await nav('Tuyến & trạm');
  await planTab('Trạm dừng');
  assert.equal(await page.getByRole('textbox',{name:'Tên trạm đón trả khách'}).inputValue(),'Trạm fixture Map-first');
  await page.getByRole('button',{name:'Đóng panel'}).click();
  await page.getByText('Hủy các thay đổi chưa lưu?').waitFor();
  await page.getByRole('button',{name:'Ở lại',exact:true}).click();
  await screenshot('desktop-station-form');
  await page.locator('.station-form button[type=submit]').click();
  await page.getByRole('complementary',{name:'Chi tiết trạm',exact:true}).waitFor();
  await page.getByRole('button',{name:'Chỉnh sửa',exact:true}).click();
  await page.getByRole('textbox',{name:'Tên trạm đón trả khách'}).fill('Trạm fixture đã sửa');
  await page.locator('.station-form button[type=submit]').click();
  await page.getByRole('button',{name:'Ngừng sử dụng',exact:true}).click();
  await page.getByRole('dialog').waitFor();
  await page.keyboard.press('Escape');
  assert.equal(await page.getByRole('dialog').count(),0);
  await page.getByRole('button',{name:'Ngừng sử dụng',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận ngừng sử dụng',exact:true}).click();
  await page.getByRole('complementary',{name:'Danh sách trạm',exact:true}).waitFor();
  assert.equal(stations.length,4);
  checks.push('Station CRUD, dirty confirm, native modal Escape, draft/search retention');
  await planTab('Tuyến đường');
  await page.locator('.route-card').first().click();
  await page.locator('.route-timeline').waitFor();
  await assertMarkersVisible('Desktop route');
  await screenshot('desktop-route');
  const mapIdentity=await page.locator('.map-canvas').evaluate(el=>el._leaflet_id);
  const panePosition=await page.locator('.leaflet-map-pane').evaluate(el=>el.style.transform);
  await nav('Theo dõi'); await nav('Tuyến & trạm');
  assert.equal(await page.locator('.map-canvas').evaluate(el=>el._leaflet_id),mapIdentity);
  assert.equal(await page.locator('.leaflet-map-pane').evaluate(el=>el.style.transform),panePosition);
  assert.equal(await page.locator('.route-stop-map-marker').count(),4);
  checks.push('Persistent Leaflet instance, camera and selected route across modes');
  await closeRoute();
  await page.getByRole('button',{name:'Tạo tuyến',exact:true}).click();
  await page.getByRole('textbox',{name:'Tên tuyến đường'}).fill('Tuyến fixture kéo thả');
  for(let i=0;i<2;i++) await page.getByRole('button',{name:'Thêm điểm dừng đón/trả'}).click();
  await page.getByRole('combobox',{name:'Chọn trạm cho điểm dừng 3',exact:true}).selectOption('3');
  await page.getByRole('combobox',{name:'Chọn trạm cho điểm dừng 4',exact:true}).selectOption('4');
  await page.getByRole('spinbutton',{name:'Thời gian dừng cho điểm 2 (giây)',exact:true}).fill('90');
  await page.getByRole('spinbutton',{name:'Thời gian dừng cho điểm 3 (giây)',exact:true}).fill('60');
  await page.setViewportSize({width:1440,height:1200});
  await page.locator('.route-drawer-body').evaluate(el=>el.scrollTop=el.scrollHeight);
  await page.waitForTimeout(300);
  await page.getByRole('button',{name:'Sắp xếp điểm 4',exact:true}).dragTo(page.locator('.sortable-stop').first(),{targetPosition:{x:20,y:20}});
  assert.deepEqual(await stopIds(),[4,1,2,3]);
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'Sắp xếp điểm 1',exact:true}).focus();
  await page.keyboard.press('Space'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('Escape');
  assert.deepEqual(await stopIds(),[4,1,2,3]);
  await page.keyboard.press('Space'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter');
  assert.deepEqual(await stopIds(),[1,4,2,3]);
  await page.getByRole('button',{name:'Di chuyển điểm 1 xuống',exact:true}).click();
  assert.deepEqual(await stopIds(),[4,1,2,3]);
  await page.getByRole('button',{name:'Xem điểm 2 trên bản đồ',exact:true}).click();
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  await page.locator('.panel-launchers').getByRole('button',{name:'Tuyến & trạm',exact:true}).click();
  await planTab('Trạm dừng'); await planTab('Tuyến đường');
  assert.deepEqual(await stopIds(),[4,1,2,3]);
  assert.equal(await page.getByRole('textbox',{name:'Tên tuyến đường'}).inputValue(),'Tuyến fixture kéo thả');
  await page.getByRole('combobox',{name:'Chọn trạm cho điểm dừng 2',exact:true}).selectOption('4');
  assert.equal(await page.getByRole('button',{name:'Tính & lưu tuyến mới',exact:true}).isDisabled(),true);
  await page.getByRole('combobox',{name:'Chọn trạm cho điểm dừng 2',exact:true}).selectOption('1');
  await screenshot('desktop-route-editor');
  postFailure=true;
  await page.getByRole('button',{name:'Tính & lưu tuyến mới',exact:true}).click();
  await page.getByText('HERE tạm thời không khả dụng (fixture).').waitFor();
  assert.deepEqual(await stopIds(),[4,1,2,3]);
  postFailure=false; postDelay=1200;
  await page.getByRole('button',{name:'Tính & lưu tuyến mới',exact:true}).click();
  await nav('Theo dõi'); await nav('Tuyến & trạm');
  await page.locator('.route-timeline').waitFor();
  assert.deepEqual(createdRouteRequest.stops.map(stop=>stop.stationId),[4,1,2,3]);
  assert.equal(createdRouteRequest.stops[0].dwellDurationSeconds,0);
  assert.equal(createdRouteRequest.stops.at(-1).dwellDurationSeconds,0);
  assert.equal(createdRouteRequest.stops[2].dwellDurationSeconds,90);
  checks.push('Native drag, keyboard reorder/Escape, buttons, draft retention/markers, validation, failed POST retry and save during mode switch');
  postDelay=0;
  for(const [width,height] of [[1024,900],[768,900],[390,844],[320,720],[844,390]]) {
    await page.setViewportSize({width,height});
    await nav('Tuyến & trạm');
    await layout('Route '+width+'x'+height);
    await assertMarkersVisible('Route '+width+'x'+height);
    await screenshot('route-'+width);
    await nav('Mô phỏng');
    await layout('Simulator '+width+'x'+height);
    assert.equal(await page.locator('.context-drawer').isVisible(),false);
    await screenshot('simulator-'+width);
    await page.locator('.panel-launchers').getByRole('button',{name:'Cảnh báo',exact:true}).click();
    assert.equal(await page.getByRole('region',{name:'Luồng cảnh báo',exact:true}).isVisible(),true);
    assert.equal(await page.getByRole('region',{name:'Điều khiển mô phỏng',exact:true}).isVisible(),false);
  }
  await page.setViewportSize({width:390,height:844});
  await nav('Tuyến & trạm');
  await closeRoute();
  await page.getByRole('button',{name:'Tạo tuyến',exact:true}).click();
  await page.getByRole('textbox',{name:'Tên tuyến đường'}).fill('Bản nháp mobile');
  await page.getByRole('button',{name:'Mở rộng bảng',exact:true}).click();
  await page.getByRole('button',{name:'Thêm điểm dừng đón/trả'}).click();
  await page.getByRole('button',{name:'Di chuyển điểm 1 xuống',exact:true}).click();
  await layout('Mobile expanded route editor');
  await assertMarkersVisible('Mobile expanded route editor');
  await screenshot('mobile-route-editor');
  await page.getByRole('button',{name:'Hủy',exact:true}).click();
  await page.getByRole('button',{name:'Tiếp tục chỉnh sửa',exact:true}).click();
  await page.getByRole('button',{name:'Hủy',exact:true}).click();
  await page.getByRole('button',{name:'Bỏ bản nháp',exact:true}).click();
  await planTab('Trạm dừng');
  await page.getByRole('button',{name:'Thêm trạm mới',exact:true}).click();
  await page.mouse.click(170,270);
  await page.getByRole('complementary',{name:'Biểu mẫu trạm',exact:true}).waitFor();
  assert.ok(await page.getByRole('spinbutton',{name:'Vĩ độ'}).inputValue());
  await page.getByRole('textbox',{name:'Tên trạm đón trả khách'}).fill('Nháp mobile trạm');
  await layout('Mobile station pick restores sheet');
  await screenshot('mobile-station-form');
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  await page.locator('summary[aria-label="Lớp bản đồ"]').click();
  await page.getByRole('button',{name:'Vệ tinh',exact:true}).click();
  await page.getByRole('button',{name:'Ban đêm',exact:true}).click();
  assert.equal(await page.getByRole('checkbox',{name:'Giao thông trực tiếp'}).isDisabled(),true);
  await page.getByRole('checkbox',{name:'Trạm dừng',exact:true}).uncheck();
  await page.locator('summary[aria-label="Lớp bản đồ"]').press('Escape');
  await page.locator('.panel-launchers').getByRole('button',{name:'Tuyến & trạm',exact:true}).click();
  assert.equal(await page.getByRole('textbox',{name:'Tên trạm đón trả khách'}).inputValue(),'Nháp mobile trạm');
  checks.push('Mobile sheet expand, discard, map pick, layer controls and draft retention');
  await page.setViewportSize({width:1440,height:900});
  mode='error'; await page.reload(); await nav('Tuyến & trạm');
  await page.locator('.route-panel').getByText('Không thể kết nối dịch vụ (fixture kiểm tra).').waitFor();
  mode='empty'; await page.getByRole('button',{name:'Thử lại',exact:true}).click();
  await page.getByText('Chưa có tuyến đường nào').waitFor();
  await screenshot('empty-routes');
  mode='normal'; routes=[{...routes[0],id:1,name:detail.name},{...routes[0],id:8,name:'Tuyến 08 · Hàng Xanh'}];
  await page.reload(); await nav('Tuyến & trạm');
  detailDelay=1200;
  await page.locator('.route-card').filter({hasText:detail.name}).click();
  await closeRoute();
  await page.locator('.route-card').filter({hasText:'Tuyến 08'}).click();
  await page.locator('.route-timeline').waitFor(); await page.waitForTimeout(1300);
  assert.equal(await page.locator('.route-drawer-header h3').textContent(),'Tuyến 08 · Hàng Xanh');
  checks.push('Error/retry/empty and stale detail request cannot replace newer route');
  detailDelay=0; corruptRoute=true; await closeRoute();
  await page.locator('.route-card').first().click();
  await page.getByText('Lỗi: Hình học đường đi (polyline) của tuyến bị hỏng, không thể hiển thị lộ trình.').waitFor();
  assert.equal(await page.locator('.route-stop-map-marker').count(),0);
  checks.push('Corrupt polyline rejects entire map geometry');
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({checks,pageErrors:errors,api:'intercepted fixtures only',browser:'headless Microsoft Edge',date:new Date().toISOString()},null,2));
  console.log(JSON.stringify({checks,pageErrors:errors},null,2));
} catch(error) { await screenshot('failure'); console.error(error); process.exitCode=1; }
finally {await browser.close();}
