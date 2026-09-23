// UI-only verification. All API responses are intercepted fixtures, never operational data.
// Usage: UI_PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node ui-smoke.mjs
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const { chromium } = await import(process.env.UI_PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5174';
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-ui-031';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh' });
const page = await context.newPage();
const failures = [], shots = [], checks = [], runtimeErrors = [];
let role = 'ADMIN', mode = 'data';
const stamp = '2026-09-22T01:00:00Z';
const drivers = ['Nguyễn Văn An', 'Trần Minh Hoàng', 'Lê Thị Thanh', 'Phạm Quốc Bảo'].map((fullName,i) => ({ id:i+1, fullName, phoneNumber:`090123456${i}`, licenseNumber:`B2-12000${i}`, active:i !== 3, createdAt:stamp, updatedAt:stamp }));
const vehicles = ['Xe tuyến trung tâm','Xe tuyến sân bay','Xe trung chuyển','Xe dự phòng'].map((name,i) => ({ id:i+1, name, plateNumber:`51B1234${i}`, description:'Phương tiện kiểm thử giao diện', vehicleType:'CAR', active:i !== 3, driver:i<2 ? drivers[i] : null, createdAt:stamp, updatedAt:stamp }));
const trips = Array.from({length:12},(_,i) => ({ id:100+i, attemptNumber:1, vehicleId:(i%3)+1, vehiclePlateNumber:vehicles[i%3].plateNumber, vehicleType:'CAR', routeId:1, routeName:['Bến Thành → Suối Tiên','Sân bay → Trung tâm','Thủ Đức → Quận 1'][i%3], status:['SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'][i%4], scheduledDepartureAt:stamp, plannedEndAt:'2026-09-22T03:00:00Z', startedAt:i%4 ? stamp : null, endedAt:null, createdAt:stamp, driver:drivers[i%3] }));
const routes = [{id:1,name:'Bến Thành → Suối Tiên',active:true,transportMode:'CAR',routingProvider:'HERE',startStationName:'Bến Thành',endStationName:'Suối Tiên',stopCount:3,totalDistanceMeters:24500,estimatedTravelDurationSeconds:3600,totalDwellDurationSeconds:600,estimatedTripDurationSeconds:4200,calculatedAt:stamp,createdAt:stamp}];
const stops = ['Bến Thành','Thảo Điền','Suối Tiên'].map((stationName,i) => ({sequenceNumber:i+1,stationId:i+1,stationName,latitude:10.77+i*.01,longitude:106.7+i*.01,checkinRadiusMeters:100,dwellDurationSeconds:60,arrivalOffsetSeconds:i*600,departureOffsetSeconds:i*600+60,plannedArrivalAt:stamp,plannedDepartureAt:stamp}));
const schedules = [true,false,true].map((enabled,i) => ({id:i+1,name:['Ca sáng ngày thường','Ca chiều cuối tuần','Tuyến sân bay'][i],routeId:1,routeName:routes[0].name,vehicleId:1,vehiclePlate:vehicles[0].plateNumber,driverId:1,driverName:drivers[0].fullName,frequency:'WEEKLY',scheduledDate:null,weekdaysMask:31,departureTime:'08:00:00',timezone:'Asia/Ho_Chi_Minh',effectiveFrom:'2026-09-01',effectiveUntil:null,enabled,nextRunAt:stamp,lastRunAt:stamp,lastRunStatus:'SUCCESS',lastRunMessage:null}));
const alerts = [0,1,2].map(i => ({id:i+1,tripId:100,vehicleId:1,vehiclePlateNumber:vehicles[0].plateNumber,revisionId:null,type:i ? 'REROUTE_UNAVAILABLE':'OFF_ROUTE_DETECTED',severity:i ? 'MAJOR':'CRITICAL',title:i ? 'Tuyến đường cần được kiểm tra':'Phương tiện lệch khỏi tuyến được giao',reason:'Cần kiểm tra hành trình và liên hệ tài xế.',incidentId:null,affectedStopSequences:'',baselineEtaSeconds:null,revisedEtaSeconds:null,createdAt:stamp,readAt:i===2 ? stamp:null,measuredDistanceMeters:450,thresholdDistanceMeters:200,breachDurationSeconds:90}));
const user = () => ({accountId:1,username:'ui.fixture',role,active:true,driverId:role==='DRIVER'?1:null,driverName:role==='DRIVER'?drivers[0].fullName:null});
await context.route('**/*', async route => {
  const url = new URL(route.request().url());
  if (!url.pathname.includes('/api/v1/')) {
    if (url.origin === new URL(base).origin) return route.continue();
    return route.abort(); // No provider/font network traffic is needed for UI fixtures.
  }
  const path = url.pathname.split('/api/v1')[1];
  const json = (body,status=200) => route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if (path === '/auth/me') return role==='GUEST' ? json({detail:'Fixture guest'},401):json(user());
  if (path === '/auth/csrf') return json({});
  if (path === '/auth/register-admin') return json({id:99,username:'ui.new',role:'ADMIN',active:true});
  if (path === '/auth/login') { role='ADMIN'; return json(user()); }
  if (mode==='error') return json({detail:'Dữ liệu kiểm thử: không thể tải, vui lòng thử lại.'},503);
  const list = value => mode==='empty' || mode==='map' ? [] : value;
  if (path === '/telemetry/snapshot') return json({serverTime:stamp,positions:[],simulations:[],trips:[],checkIns:[],notifications:[]});
  if (path === '/telemetry/stream') return route.fulfill({status:200,contentType:'text/event-stream',body:'event: snapshot\ndata: '+JSON.stringify({serverTime:stamp,positions:[],simulations:[],trips:[],checkIns:[],notifications:[]})+'\n\n'});
  if (path === '/dashboard/summary') return json({serverTime:stamp,activeVehicleCount:18,activeDriverCount:16,tripsInProgress:8,scheduledTrips:12,completedTrips:124,cancelledTrips:2,overdueTrips:3,offRouteVehicleCount:1,unreadAlertCount:2,pendingAlerts:list(alerts)});
  if (path === '/vehicles') return json(list(vehicles));
  if (path === '/drivers') return json(list(drivers));
  if (path === '/trips' || path === '/driver/trips') return json(list(trips));
  if (/^\/(driver\/)?trips\/\d+$/.test(path)) return json({trip:trips.find(t=>t.id===Number(path.split('/').at(-1)))??trips[0],stops,route:{...routes[0],stops,sections:[]}});
  if (path.endsWith('/check-ins')) return json({tripId:100,revision:0,visits:[],nextStopSequence:1,awaitingExit:false});
  if (path.endsWith('/eta')) return json({tripId:100,status:'OK',source:'BASE_ROUTE',totalRemainingSeconds:3600,stops:[]});
  if (path === '/routes') return json(list(routes));
  if (path === '/schedules' || path === '/driver/schedules') return json(list(schedules));
  if (path.startsWith('/notifications')) return json(list(alerts));
  if (path === '/users') return json(list([{id:1,username:'quantri',role:'ADMIN',active:true,driverId:null,driverName:null,driverLicenseNumber:null},{id:2,username:'nguyen.an',role:'DRIVER',active:true,driverId:1,driverName:drivers[0].fullName,driverLicenseNumber:drivers[0].licenseNumber}]));
  if (path === '/reports/operations') return json({from:'2026-09-01',to:'2026-09-22',generatedAt:stamp,tripCount:mode==='empty'?0:142,completedTripCount:124,totalDistanceMeters:3456000,totalRunningSeconds:234567,onTimeRatePercent:94.2,lateTripCount:8,offRouteEventCount:4,overspeedEventCount:2,speedLimitKmh:60});
  return json([]);
});
page.on('pageerror', error => runtimeErrors.push(error.message));
async function visit(path) {
  await page.goto(base+path);
  await page.waitForSelector('.business-ui');
  await page.waitForTimeout(180);
}
async function snapshot(name) {
  const file = `${output}/${name}.png`;
  await page.screenshot({path:file,fullPage:true}); shots.push(file);
}
async function overflow(name) {
  const result = await page.evaluate(() => {
    const containers = [...document.querySelectorAll('.business-content,.auth-page,.driver-portal,dialog[open]')];
    return containers.filter(el=>el.scrollWidth>el.clientWidth+2).map(el=>({class:el.className,client:el.clientWidth,scroll:el.scrollWidth}));
  });
  if (result.length) failures.push({name,overflow:result});
}
try {
  for (const width of [1440,768,390]) {
    await page.setViewportSize({width,height:width===390?844:1000});
    for (const path of ['/dashboard','/vehicles','/drivers','/trips','/schedules','/alerts','/reports','/users']) {
      role='ADMIN';mode='data'; await visit(path); await overflow(`${path}@${width}`);
      await snapshot(`${path.slice(1)}-${width}`);
      checks.push(`Rendered ${path} at ${width}px`);
    }
    role='DRIVER';
    for(const path of ['/driver/today','/driver/schedules']) { await visit(path); await overflow(`${path}@${width}`); await snapshot(`${path.replaceAll('/','-').slice(1)}-${width}`); }
    const canScroll=await page.locator('.driver-portal').evaluate(el=>{el.scrollTop=el.scrollHeight;return el.scrollHeight<=el.clientHeight||el.scrollTop>0;});
    assert(canScroll,'Driver portal scroll owner');
    role='GUEST';
    for(const path of ['/login','/register']) { await visit(path); await overflow(`${path}@${width}`); await snapshot(`${path.slice(1)}-${width}`); }
  }
  role='ADMIN'; await page.setViewportSize({width:1440,height:1000});
  await visit('/vehicles');
  await page.getByRole('textbox',{name:'Tìm xe',exact:true}).fill('51B12340');
  assert.equal(await page.locator('.management-table tbody tr').count(),1);
  await page.getByRole('button',{name:'Sửa xe 51B12340',exact:true}).click();
  await page.locator('#vehicle-form').waitFor(); await overflow('vehicle-editor'); await snapshot('vehicle-editor');
  await page.getByRole('button',{name:'Đóng biểu mẫu xe'}).click();
  await page.getByRole('button',{name:'Ngừng sử dụng xe 51B12340',exact:true}).click();
  await page.locator('dialog[open]').waitFor(); await snapshot('vehicle-confirm'); await page.keyboard.press('Escape');
  await visit('/drivers'); await page.getByRole('button',{name:'Thêm tài xế',exact:true}).click();
  await page.locator('#driver-form').waitFor(); await snapshot('driver-editor');
  await visit('/trips'); await page.getByRole('button',{name:'Tạo chuyến đi',exact:true}).click();
  await page.locator('#trip-form').waitFor(); await snapshot('trip-editor');
  await visit('/trips'); await page.getByRole('button',{name:/Mở chi tiết chuyến 100,/}).click();
  await page.getByText('Lịch trình dự kiến và thực tế').waitFor(); await snapshot('trip-detail');
  for (const width of [1440,390]) {
    await page.setViewportSize({width,height:844}); await visit('/schedules');
    const trigger=page.getByRole('button',{name:'Tạo lịch chạy',exact:true}).first();
    await trigger.click(); await page.locator('dialog[open]').waitFor(); await overflow(`schedule-editor@${width}`); await snapshot(`schedule-editor-${width}`);
    const focusInside = await page.evaluate(()=>document.querySelector('dialog[open]')?.contains(document.activeElement));assert(focusInside);
    await trigger.focus();
    assert(await page.evaluate(()=>document.querySelector('dialog[open]')?.contains(document.activeElement)),'Background is inert while modal is open');
    const finalInput=page.getByLabel('Hiệu lực đến', {exact:false});
    await finalInput.fill('2026-12-31');
    await finalInput.scrollIntoViewIfNeeded();
    const visibleInput=await finalInput.evaluate(el=>{const r=el.getBoundingClientRect();return r.top>=0 && r.bottom<=window.innerHeight;});
    assert(visibleInput,`Schedule last field reachable at ${width}px`);
    const submit=page.locator('dialog[open] footer button').last();
    await submit.focus();
    const visibleSubmit=await submit.evaluate(el=>{const r=el.getBoundingClientRect();return r.top>=0 && r.bottom<=window.innerHeight;});
    assert(visibleSubmit,`Schedule footer visible at ${width}px`);
    await page.keyboard.press('Tab');
    // Chromium may visit browser chrome (document.activeElement === body) at the
    // boundary. The next page focus must stay within the modal, not behind it.
    if(await page.evaluate(()=>document.activeElement===document.body)) await page.keyboard.press('Tab');
    assert(await page.evaluate(()=>document.querySelector('dialog[open]')?.contains(document.activeElement)),'Dialog contains forward page focus');
    await page.keyboard.press('Shift+Tab');
    if(await page.evaluate(()=>document.activeElement===document.body)) await page.keyboard.press('Shift+Tab');
    assert(await page.evaluate(()=>document.querySelector('dialog[open]')?.contains(document.activeElement)),'Dialog contains reverse page focus');
    await snapshot(`schedule-editor-end-${width}`);
    await page.keyboard.press('Escape'); assert.equal(await page.locator('dialog[open]').count(),0); assert(await trigger.evaluate(el=>el===document.activeElement));
    checks.push(`Schedule dialog scroll/footer/Tab/Escape/focus restoration at ${width}px`);
  }
  await visit('/vehicles');
  await page.getByRole('button',{name:'Mở điều hướng'}).click(); await snapshot('mobile-navigation');
  await page.keyboard.press('Escape'); assert.equal(await page.locator('.business-shell').getAttribute('data-navigation-open'),'false');
  assert(await page.getByRole('button',{name:'Mở điều hướng'}).evaluate(el=>el===document.activeElement));
  checks.push('Mobile navigation Escape/focus restoration');
  for (const state of ['empty','error']) {
    mode=state; await page.setViewportSize({width:1440,height:1000});
    for (const path of ['/dashboard','/vehicles','/schedules','/alerts','/reports','/users']) {
      await visit(path); await overflow(`${path}:${state}`); await snapshot(`${path.slice(1)}-${state}`);
      if(state==='error') assert(await page.locator('[role=alert]').count()>0,`${path} renders error`);
    }
  }
  mode='data'; role='DRIVER'; await page.setViewportSize({width:390,height:844}); await visit('/driver/today');
  assert(await page.locator('.driver-status').first().isVisible(),'Mobile driver trip status visible');
  await page.locator('.driver-trip-card').first().click(); await page.locator('dialog[open]').waitFor(); await snapshot('driver-detail-mobile'); await overflow('driver-detail'); await page.keyboard.press('Escape');
  role='GUEST'; await page.setViewportSize({width:390,height:600}); await visit('/register');
  await page.getByRole('textbox',{name:'Tên đăng nhập'}).fill('ui.new');
  await page.getByLabel('Mật khẩu (tối thiểu 12 ký tự)',{exact:true}).fill('fixture-password-123');
  await page.getByLabel('Xác nhận mật khẩu',{exact:true}).fill('fixture-mismatch-123');
  await page.getByRole('button',{name:'Tạo tài khoản admin'}).click();
  await page.getByRole('alert').waitFor(); await snapshot('register-validation-mobile');
  await page.getByLabel('Xác nhận mật khẩu',{exact:true}).fill('fixture-password-123');
  await page.getByRole('button',{name:'Tạo tài khoản admin'}).click();
  await page.getByRole('status').waitFor(); await snapshot('register-success-mobile');
  checks.push('Registration validation and success using intercepted fixture API');
  role='ADMIN'; mode='map';
  for (const width of [1440,390]) {
    await page.setViewportSize({width,height:844});
    for (const path of ['/operations','/routes','/stations']) {
      await page.goto(base+path); await page.locator('.business-shell[data-map-focus=true]').waitFor();
      await page.waitForTimeout(250);
      assert.equal(await page.locator('.business-shell.business-ui').count(),0);
      const identical = await page.evaluate(() => {
        const style = [...document.querySelectorAll('style[data-vite-dev-id]')].find(el=>el.dataset.viteDevId.endsWith('/src/ui-refresh.css'));
        if (!style?.sheet) throw new Error('Refresh stylesheet missing');
        const nodes = [...document.querySelectorAll('.business-shell,.business-sidebar,.business-brand,.business-navigation a,.business-topbar,.business-content,.application-shell,.map-viewport,.map-canvas,.fleet-workspace,.fleet-search,.fleet-heading,.fleet-footer')];
        const props = ['display','color','backgroundColor','borderColor','width','height','padding','fontSize','boxShadow','gridTemplateColumns','position'];
        const collect = () => nodes.map(el=>{const s=getComputedStyle(el);return props.map(p=>s[p]);});
        const before=collect(); style.sheet.disabled=true; const after=collect(); style.sheet.disabled=false;
        return JSON.stringify(before)===JSON.stringify(after);
      });
      assert(identical,`Map computed style isolation ${path}@${width}`);
      checks.push(`Map theme isolation ${path}@${width}`);
    }
  }
} catch(error) { failures.push({message:error.message}); }
finally {
  const result={fixtureOnly:true,script:fileURLToPath(import.meta.url),checks,screenshots:shots,runtimeErrors,failures};
  await writeFile(`${output}/results.json`,JSON.stringify(result,null,2));
  console.log(JSON.stringify({checks:checks.length,screenshots:shots.length,runtimeErrors,failures,output},null,2));
  await browser.close();
}
assert.equal(failures.length,0,'UI verification failures');
assert.equal(runtimeErrors.length,0,'React runtime errors');
