// UI fixtures only. Every /api/ request is intercepted; no application data is written.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../artifacts/regression-005/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ ...(process.env.VERIFICATION_BROWSER_PATH
  ? { executablePath: process.env.VERIFICATION_BROWSER_PATH } : { channel: 'msedge' }), headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: 'Asia/Ho_Chi_Minh' });
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

let vehicles = [];
let trips = [];
let createdTripInput;
let slowDetailId = null;
let slowPost = false;
let apiError = false;
let conflictStart = false;
let unknownRequests = [];
let writes = [];
let nextId = 1;
function tripDetail(summary) {
  return {trip:summary,route:detail,stops:detail.stops.map(stop=>({...stop,checkinRadiusMeters:50,
    plannedArrivalAt:new Date(Date.parse(summary.scheduledDepartureAt)+stop.arrivalOffsetSeconds*1000).toISOString(),
    plannedDepartureAt:new Date(Date.parse(summary.scheduledDepartureAt)+stop.departureOffsetSeconds*1000).toISOString()}))};
}
await context.route('**/api/**', async route=>{
  const request=route.request(),path=new URL(request.url()).pathname,method=request.method();
  const json=(value,status=200)=>route.fulfill({status,json:value});
  if(path.endsWith('/telemetry/snapshot')) return json({serverTime:new Date().toISOString(),positions:[],simulations:[],trips:[],checkIns:[]});
  if(path.endsWith('/telemetry/stream')) return route.fulfill({contentType:'text/event-stream',body:': fixture heartbeat\n\n'});
  if(method!=='GET') writes.push({method,path,body:request.postData()});
  if(apiError && (path.endsWith('/vehicles')||path.endsWith('/trips'))) return json({detail:'Không thể tải đội xe (fixture).'},503);
  if(path.endsWith('/stations') && method==='GET') return json(stations);
  if(path.endsWith('/routes') && method==='GET') return json(routes);
  if(path.includes('/routes/') && method==='GET') return json(detail);
  if(path.endsWith('/vehicles') && method==='GET') return json(vehicles);
  if(path.endsWith('/vehicles') && method==='POST') {
    const data=request.postDataJSON(),plateNumber=data.plateNumber.toUpperCase().replace(/[ .-]/g,'');
    if(vehicles.some(vehicle=>vehicle.plateNumber===plateNumber)) return json({detail:'Biển số đã tồn tại.'},409);
    if(slowPost) await new Promise(resolve=>setTimeout(resolve,800));
    const vehicle={...data,id:nextId++,plateNumber,active:true,createdAt:stamp,updatedAt:stamp};
    vehicles.push(vehicle); return json(vehicle,201);
  }
  if(path.includes('/vehicles/') && method==='PUT') {
    const id=Number(path.split('/').pop()),data=request.postDataJSON();
    vehicles=vehicles.map(vehicle=>vehicle.id===id?{...vehicle,...data,plateNumber:data.plateNumber.toUpperCase().replace(/[ .-]/g,'')}:vehicle);
    return json(vehicles.find(vehicle=>vehicle.id===id));
  }
  if(path.includes('/vehicles/') && method==='DELETE') {
    const id=Number(path.split('/').pop());
    if(trips.some(trip=>trip.vehicleId===id&&['SCHEDULED','IN_PROGRESS'].includes(trip.status))) return json({detail:'Hãy hoàn thành hoặc hủy các chuyến chưa kết thúc trước khi ngừng sử dụng xe.'},409);
    vehicles=vehicles.map(vehicle=>vehicle.id===id?{...vehicle,active:false}:vehicle);
    return route.fulfill({status:204});
  }
  if(path.endsWith('/trips') && method==='GET') return json(trips);
  if(path.endsWith('/trips') && method==='POST') {
    createdTripInput=request.postDataJSON();
    const vehicle=vehicles.find(vehicle=>vehicle.id===createdTripInput.vehicleId);
    const trip={...createdTripInput,id:nextId++,vehiclePlateNumber:vehicle.plateNumber,routeName:detail.name,status:'SCHEDULED',
      plannedEndAt:new Date(Date.parse(createdTripInput.scheduledDepartureAt)+detail.estimatedTripDurationSeconds*1000).toISOString(),startedAt:null,endedAt:null,createdAt:stamp};
    trips.unshift(trip); return json(tripDetail(trip),201);
  }
  if(path.includes('/trips/') && method==='GET') {
    const id=Number(path.split('/').pop());
    if(id===slowDetailId) await new Promise(resolve=>setTimeout(resolve,1000));
    return json(tripDetail(trips.find(trip=>trip.id===id))).catch(()=>{});
  }
  if(path.includes('/trips/') && method==='POST') {
    const parts=path.split('/'),action=parts.pop(),id=Number(parts.pop());
    if(action==='start'&&conflictStart) return json({detail:'Xe đang chạy một chuyến khác.'},409);
    const trip=trips.find(trip=>trip.id===id);
    if(action==='start') {trip.status='IN_PROGRESS';trip.startedAt=new Date().toISOString();}
    else {trip.status=action==='complete'?'COMPLETED':'CANCELLED';trip.endedAt=new Date().toISOString();}
    return json(tripDetail(trip));
  }
  unknownRequests.push(path); return route.abort();
});
const nav=name=>page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name,exact:true}).click();
const tab=name=>page.locator('.fleet-tabs').getByRole('button',{name}).click();
const capture=async name=>{await page.waitForTimeout(200);await page.screenshot({path:output+name+'-fixture.png'});};
async function checkLayout(label) {
  const result=await page.evaluate(()=>{
    const map=document.querySelector('.map-canvas').getBoundingClientRect();
    return {map:[map.x,map.y,map.width,map.height],viewport:[0,0,innerWidth,innerHeight],
      overflow:document.documentElement.scrollWidth>innerWidth || [...document.querySelectorAll('.fleet-form-body,.fleet-detail-body,.fleet-list-body')].filter(el=>el.checkVisibility()).some(el=>el.scrollWidth>el.clientWidth+1)};
  });
  assert.deepEqual(result.map,result.viewport,label+' full map');
  assert.equal(result.overflow,false,label+' horizontal overflow');checks.push(label);
}
async function newTrip(departure) {
  await page.getByRole('button',{name:'Tạo chuyến mới',exact:true}).click();
  await page.getByLabel('Xe thực hiện *',{exact:true}).selectOption(String(vehicles[0].id));
  await page.getByLabel('Tuyến đường *',{exact:true}).selectOption('1');
  await page.getByLabel('Giờ xuất phát *',{exact:true}).fill(departure);
  await page.getByRole('button',{name:'Tạo chuyến',exact:true}).click();
  await page.locator('.trip-timeline').waitFor();
}
try {
  await page.goto('http://127.0.0.1:5173', {waitUntil:'domcontentloaded'});
  await page.getByText('Chưa có xe trong danh mục').waitFor();
  await checkLayout('Desktop empty fleet');
  await tab('Chuyến đi');
  assert.equal(await page.getByRole('button',{name:'Tạo chuyến mới',exact:true}).isDisabled(),true);
  await tab('Đội xe');
  await page.getByRole('button',{name:'Thêm xe mới',exact:true}).click();
  await page.getByLabel('Biển số *',{exact:true}).fill('51b-123.45');
  await page.getByLabel('Tên xe *',{exact:true}).fill('Xe buýt 01');
  await page.getByLabel('Mô tả',{exact:true}).fill('Xe fixture kiểm tra giao diện');
  await nav('Tuyến & trạm');await nav('Theo dõi');
  assert.equal(await page.getByLabel('Tên xe *',{exact:true}).inputValue(),'Xe buýt 01');
  await page.getByRole('button',{name:'Đóng biểu mẫu xe',exact:true}).click();
  await page.getByRole('dialog').waitFor();await page.keyboard.press('Escape');
  slowPost=true;
  await page.getByRole('button',{name:'Thêm xe',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Đóng biểu mẫu xe',exact:true}).isDisabled(),true);
  await nav('Tuyến & trạm');await nav('Theo dõi');
  await page.locator('.fleet-vehicle-card').first().waitFor();slowPost=false;
  assert.equal(vehicles[0].plateNumber,'51B12345');
  assert.equal(writes.filter(write=>write.path.endsWith('/vehicles')&&write.method==='POST').length,1);
  assert.equal(await page.locator('.vehicle-map-marker').count(),0);
  await capture('fleet-desktop');
  checks.push('Vehicle creation, normalization, draft persistence, discard Escape, no duplicate submit/no fake position');
  await page.getByRole('button',{name:'Sửa xe 51B12345',exact:true}).click();
  await page.getByLabel('Tên xe *',{exact:true}).fill('Xe buýt 01 đã sửa');
  await page.getByRole('button',{name:'Lưu xe',exact:true}).click();
  await page.getByText('Xe buýt 01 đã sửa',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Thêm xe mới',exact:true}).click();
  await page.getByLabel('Biển số *',{exact:true}).fill('51B 12345');
  await page.getByLabel('Tên xe *',{exact:true}).fill('Xe trùng');
  await page.getByRole('button',{name:'Thêm xe',exact:true}).click();
  await page.getByText('Biển số đã tồn tại.',{exact:true}).waitFor();
  assert.equal(await page.getByLabel('Tên xe *',{exact:true}).inputValue(),'Xe trùng');
  await page.getByRole('button',{name:'Đóng biểu mẫu xe',exact:true}).click();
  await page.getByRole('button',{name:'Bỏ thay đổi',exact:true}).click();
  await page.locator('.fleet-vehicle-select').first().click();
  assert.ok(await page.locator('.fleet-filter-chip').isVisible());
  await page.getByRole('button',{name:'Tạo chuyến mới',exact:true}).click();
  assert.equal(await page.getByLabel('Xe thực hiện *',{exact:true}).inputValue(),String(vehicles[0].id));
  await page.getByLabel('Tuyến đường *',{exact:true}).selectOption('1');
  await page.getByLabel('Giờ xuất phát *',{exact:true}).fill('2026-09-15T23:58');
  await page.getByRole('button',{name:'Quản lý tuyến',exact:true}).click();await nav('Theo dõi');
  assert.equal(await page.getByLabel('Giờ xuất phát *',{exact:true}).inputValue(),'2026-09-15T23:58');
  await capture('trip-editor-desktop');
  await page.getByRole('button',{name:'Tạo chuyến',exact:true}).click();
  await page.locator('.trip-timeline').waitFor();
  assert.equal(createdTripInput.scheduledDepartureAt,'2026-09-15T16:58:00.000Z');
  assert.ok((await page.locator('.trip-timeline').innerText()).includes('16/09/2026'));
  assert.equal(await page.locator('.route-stop-map-marker').count(),4);
  assert.equal(await page.locator('.vehicle-map-marker').count(),0);
  const firstTripId=trips[0].id;
  const baseline=trips[0].scheduledDepartureAt;
  await capture('trip-detail-desktop');
  checks.push('Vehicle update/duplicate conflict, vehicle trip filter, trip draft persistence, UTC/day rollover and route map');
  await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();await tab('Đội xe');
  await page.getByRole('button',{name:'Ngừng sử dụng xe 51B12345',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận ngừng sử dụng xe',exact:true}).click();
  await page.getByRole('dialog').getByText('Hãy hoàn thành hoặc hủy các chuyến chưa kết thúc trước khi ngừng sử dụng xe.').waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'Tải lại đội xe và chuyến',exact:true}).click();
  await tab('Chuyến đi');
  await page.locator('.fleet-trip-card').first().click();
  await page.locator('.trip-timeline').waitFor();
  conflictStart=true;await page.getByRole('button',{name:'Khởi hành',exact:true}).click();
  await page.locator('.fleet-error:visible').filter({hasText:'Xe đang chạy một chuyến khác.'}).waitFor();
  conflictStart=false;await page.getByRole('button',{name:'Tải lại trạng thái chuyến',exact:true}).click();
  await page.getByRole('button',{name:'Khởi hành',exact:true}).click();
  await page.getByRole('button',{name:'Hoàn thành',exact:true}).waitFor();
  assert.equal(trips.find(trip=>trip.id===firstTripId).scheduledDepartureAt,baseline);
  await page.getByRole('button',{name:'Hoàn thành',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận hoàn thành',exact:true}).click();
  await page.locator('.trip-summary .trip-status.completed').waitFor();
  await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();
  await newTrip('2026-09-16T08:00');
  await page.getByRole('button',{name:'Hủy chuyến',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận hủy chuyến',exact:true}).click();
  await page.locator('.trip-summary .trip-status.cancelled').waitFor();
  await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();
  checks.push('Deactivate conflict, start conflict/reload, start/complete/cancel, immutable baseline');
  slowDetailId=firstTripId;
  await page.locator('.fleet-trip-card').filter({hasText:'#'+firstTripId+' ·'}).click();
  await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();
  await page.locator('.fleet-trip-card').filter({hasText:'#'+trips[0].id+' ·'}).click();
  await page.locator('.trip-timeline').waitFor();await page.waitForTimeout(1100);
  assert.equal(await page.locator('.fleet-heading h2:visible').textContent(),'Chuyến #'+trips[0].id);
  slowDetailId=null;
  checks.push('Stale trip GET cannot replace newer selection');
  for(const width of [390,320]) {
    await page.setViewportSize({width,height:844});
    await checkLayout('Trip detail '+width);
    await page.getByRole('button',{name:'Mở rộng bảng',exact:true}).click();
    await capture('trip-detail-'+width);
    await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();
    await page.getByRole('button',{name:'Tạo chuyến mới',exact:true}).click();
    await page.getByLabel('Xe thực hiện *',{exact:true}).selectOption(String(vehicles[0].id));
    await page.getByLabel('Tuyến đường *',{exact:true}).selectOption('1');
    await checkLayout('Trip editor '+width);
    await capture('trip-editor-'+width);
    await page.getByRole('button',{name:'Đóng biểu mẫu chuyến',exact:true}).click();
    await page.getByRole('button',{name:'Bỏ bản nháp',exact:true}).click();
    await page.locator('.fleet-trip-card').first().click();await page.locator('.trip-timeline').waitFor();
    await page.getByRole('button',{name:'Thu chiều cao bảng',exact:true}).click();
  }
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'Đóng chi tiết chuyến',exact:true}).click();await tab('Đội xe');
  await page.getByRole('button',{name:'Ngừng sử dụng xe 51B12345',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận ngừng sử dụng xe',exact:true}).click();
  await page.getByRole('dialog').waitFor({state:'hidden'});
  await page.getByRole('combobox',{name:'Lọc xe',exact:true}).selectOption('inactive');
  await page.locator('.fleet-vehicle-card').first().waitFor();
  assert.equal(vehicles[0].active,false);
  await page.locator('.fleet-vehicle-select').first().click();
  assert.equal(await page.locator('.fleet-trip-card').count(),2);
  assert.equal(await page.getByRole('button',{name:'Tạo chuyến mới',exact:true}).isDisabled(),true);
  checks.push('Soft deactivate after terminal trips and retained history');
  apiError=true;await page.reload({waitUntil:'domcontentloaded'});
  await page.locator('.fleet-error:visible').filter({hasText:'Không thể tải đội xe (fixture).'}).waitFor();
  apiError=false;await page.getByRole('button',{name:'Thử lại',exact:true}).click();
  await page.getByRole('combobox',{name:'Lọc xe',exact:true}).selectOption('all');
  await page.locator('.fleet-vehicle-card').first().waitFor();
  checks.push('Initial API error/retry without fake empty or telemetry');
  assert.deepEqual(errors,[]);assert.deepEqual(unknownRequests,[]);
  await writeFile(output+'results.json',JSON.stringify({checks,pageErrors:errors,api:'Intercepted fixtures only; no live data writes',date:new Date().toISOString()},null,2));
  console.log(JSON.stringify({checks,pageErrors:errors},null,2));
} catch(error) { await capture('failure');console.error(error);process.exitCode=1; }
finally {await browser.close();}
