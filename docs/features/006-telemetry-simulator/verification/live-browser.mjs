// With VERIFICATION_API: real Spring/test PostgreSQL; otherwise starts explicitly labelled API fixtures.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { startFixtureServer } from './fixture-server.mjs';
import assert from 'node:assert/strict';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const fixture=process.env.VERIFICATION_API?null:await startFixtureServer();
const api=process.env.VERIFICATION_API||fixture.api;
let tripId=Number(process.env.VERIFICATION_TRIP||fixture.tripId);
const initialTrip=await fetch(api+'/trips/'+tripId).then(response=>response.json());
const markerSelector='.live-vehicle-marker[data-vehicle-id="'+initialTrip.trip.vehicleId+'"]';
const mode=fixture?'fixture':'live-test-database';
const output=fileURLToPath(new URL('../artifacts/'+mode+'/',import.meta.url));
await mkdir(output,{recursive:true});
const browser=await chromium.launch({...(process.env.VERIFICATION_BROWSER_PATH
  ? {executablePath:process.env.VERIFICATION_BROWSER_PATH} : {channel:'msedge'}),headless:true});
const context=await browser.newContext({viewport:{width:1440,height:900}});
const errors=[],checks=[];
await context.route('**/api/**',route=>{
  const path=new URL(route.request().url()).pathname;
  return route.continue({url:api+path.replace(/^\/api\/v1/,'')});
});
const pages=[await context.newPage(),await context.newPage()];
for(const page of pages){page.setDefaultTimeout(12000);page.on('pageerror',error=>errors.push(error.message));}
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function poll(check,description,limit=12000){const start=Date.now();while(Date.now()-start<limit){if(await check())return;await wait(100);}throw new Error(description);}
const runValue=page=>page.getByTestId('simulation-elapsed').textContent().then(Number);
const select=async(page,id)=>{await page.getByRole('button',{name:'Mô phỏng',exact:true}).first().click();await page.getByLabel('Chọn chuyến mô phỏng').selectOption(String(id));await page.getByText('Đang tải tuyến mô phỏng…').waitFor({state:'hidden'});};
try {
  for(const page of pages){await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});await page.locator('.simulation-connection[data-state=live]').waitFor();await select(page,tripId);}
  await pages[0].getByText(/Đã ghi nhận 0\/3 điểm dừng/).waitFor();
  const checkInReadModel=await fetch(api+'/trips/'+tripId+'/check-ins').then(r=>r.json());
  assert.equal(checkInReadModel.tripId,tripId);assert.equal(checkInReadModel.revision,0);assert.deepEqual(checkInReadModel.visits,[]);
  checks.push('Check-in read model is visible with explicit empty state');
  assert.equal(await pages[0].locator(markerSelector).count(),0);
  await pages[0].getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
  await pages[1].getByTestId('simulation-status').filter({hasText:'Đang mô phỏng'}).waitFor();
  await poll(async()=>await runValue(pages[1])>=1,'Run did not advance in second tab');
  await pages[0].getByRole('button',{name:'Tạm dừng mô phỏng',exact:true}).click();
  await pages[1].getByTestId('simulation-status').filter({hasText:'Đã tạm dừng'}).waitFor();
  await wait(1200);
  const paused=await runValue(pages[0]);
  assert.equal(await runValue(pages[1]),paused);
  assert.ok(paused>0);
  assert.equal(await pages[0].locator(markerSelector).count(),1);
  await wait(1200);assert.equal(await runValue(pages[0]),paused);
  checks.push('Two tabs receive backend movement, identical paused clock and markers');
  await pages[0].getByLabel('Tốc độ 5x',{exact:true}).click();
  await poll(async()=>await pages[1].getByLabel('Tốc độ 5x',{exact:true}).getAttribute('aria-pressed')==='true','Multiplier did not sync');
  await pages[0].getByRole('button',{name:'Tiếp tục mô phỏng',exact:true}).click();
  await poll(async()=>await runValue(pages[1])>=paused+5,'5x progress missing');
  await pages[1].getByRole('button',{name:'Tạm dừng mô phỏng',exact:true}).click();
  await pages[0].getByTestId('simulation-status').filter({hasText:'Đã tạm dừng'}).waitFor();
  const sample=await fetch(api+'/telemetry/snapshot').then(r=>r.json());
  assert.equal(sample.positions.find(p=>p.tripId===tripId).source,'SIMULATOR');
  const oldElapsed=await runValue(pages[0]);
  await pages[1].reload({waitUntil:'domcontentloaded'});await pages[1].locator('.simulation-connection[data-state=live]').waitFor();await select(pages[1],tripId);
  assert.equal(await runValue(pages[1]),oldElapsed);
  checks.push('5x controls synchronized, source SIMULATOR and reload resync');
  await pages[0].locator(markerSelector).click();
  await pages[0].getByRole('button',{name:'Theo xe',exact:true}).click();
  assert.equal(await pages[0].getByRole('button',{name:'Bỏ theo xe',exact:true}).getAttribute('aria-pressed'),'true');
  await pages[0].screenshot({path:output+'simulator-desktop.png'});
  checks.push('Vehicle selection and follow');
  await pages[0].getByRole('button',{name:'Chạy lại',exact:true}).click();
  await pages[0].getByRole('button',{name:'Tạo chuyến chạy lại',exact:true}).click();
  await poll(async()=>Number(await pages[0].getByLabel('Chọn chuyến mô phỏng').inputValue())!==tripId,'Reset did not select new trip');
  const previous=tripId;tripId=Number(await pages[0].getByLabel('Chọn chuyến mô phỏng').inputValue());
  await pages[0].getByTestId('simulation-elapsed').waitFor();assert.equal(await runValue(pages[0]),0);
  const resetSnapshot=await fetch(api+'/telemetry/snapshot').then(r=>r.json());
  assert.equal(resetSnapshot.trips.find(t=>t.id===previous).status,'CANCELLED');
  assert.equal(resetSnapshot.trips.find(t=>t.id===tripId).status,'SCHEDULED');
  await pages[1].getByLabel('Chọn chuyến mô phỏng').selectOption(String(tripId));
  checks.push('Reset creates new paused trip and retains terminal trip/run history');
  for(const width of [390,320]) {
    const page=pages[0];await page.setViewportSize({width,height:844});
    await page.getByRole('button',{name:'Mô phỏng',exact:true}).first().click();
    await page.locator('.operations-dock .sheet-expand').first().click();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.locator('.operations-dock').screenshot({path:output+'simulator-'+width+'.png'});
    assert.ok(await page.getByLabel('Bắt đầu mô phỏng',{exact:true}).isEnabled());
    checks.push('Simulator mobile '+width);
  }
  await pages[0].setViewportSize({width:1440,height:900});
  if(fixture) {
    fixture.setCommandError(true);
    await pages[0].getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
    await pages[0].getByText('Xe đang thực hiện chuyến khác (fixture).',{exact:false}).waitFor();
    fixture.setCommandError(false);
    checks.push('409 is displayed and controls recover');
  }
  await pages[0].getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
  await pages[0].getByLabel('Tốc độ 10x',{exact:true}).click();
  await pages[1].getByTestId('simulation-status').filter({hasText:'Đã hoàn thành'}).waitFor({timeout:15000});
  const done=await fetch(api+'/telemetry/snapshot').then(r=>r.json());
  assert.equal(done.trips.find(t=>t.id===tripId).status,'COMPLETED');
  assert.equal(done.positions.find(p=>p.tripId===tripId).speedKmh,0);
  checks.push('10x completion reaches final point and completes trip on both tabs');
  if (!fixture && process.env.VERIFICATION_CHECKINS === 'true') {
    await pages[0].getByText(/Đã ghi nhận 3\/3 điểm dừng/).waitFor();
    await pages[0].getByText(/Lần gần nhất:/).waitFor();
    const checkIns=await fetch(api+'/trips/'+tripId+'/check-ins').then(r=>r.json());
    assert.equal(checkIns.tripId,tripId);
    assert.ok(checkIns.revision>=3);
    assert.deepEqual(checkIns.visits.map(visit=>visit.stopSequence),[1,2,3]);
    assert.deepEqual(checkIns.visits.map(visit=>visit.source),['SIMULATOR','SIMULATOR','SIMULATOR']);
    assert.equal(checkIns.visits[0].evidenceKind,'POINT');
    assert.deepEqual(checkIns.visits.slice(1).map(visit=>visit.evidenceKind),['ROUTE_TRACE','ROUTE_TRACE']);
    checks.push('Automatic check-in history and simulator summary contain ordered visits');
  }
  if(fixture) {
    fixture.gpsAge(20);await wait(1200);
    await pages[0].locator(markerSelector).hover();await pages[0].locator('.leaflet-tooltip').filter({hasText:'Vị trí cũ'}).waitFor();
    fixture.gpsAge(70);await wait(1200);
    await pages[0].locator('.leaflet-tooltip').filter({hasText:'Mất tín hiệu'}).waitFor();
    assert.equal(await pages[0].locator(markerSelector+'.muted').count(),1);
    fixture.holdStream(true);fixture.disconnect();
    await pages[0].locator('.simulation-connection[data-state=reconnecting]').waitFor();
    assert.equal(await pages[0].getByRole('button',{name:'Chạy lại',exact:true}).isDisabled(),true);
    assert.equal(await pages[0].locator(markerSelector).count(),1);
    fixture.holdStream(false);await pages[0].getByRole('button',{name:'Kết nối lại',exact:true}).click();
    await pages[0].locator('.simulation-connection[data-state=live]').waitFor();
    checks.push('GPS stale/offline retains marker; disconnect disables commands; explicit SSE reconnect resyncs');
  }
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({mode,checks,pageErrors:errors,api:'Isolated verification only; no user data writes',date:new Date().toISOString()},null,2));
  console.log(JSON.stringify({mode,checks,pageErrors:errors}));
} catch(error) {
  await pages[0].screenshot({path:output+'failure.png'}).catch(()=>{});
  throw error;
} finally {await context.close();await browser.close();if(fixture)await fixture.close();}
