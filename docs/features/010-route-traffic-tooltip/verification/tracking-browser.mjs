// Isolated API/SSE fixtures; never changes the user's database or calls HERE.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { startFixtureServer } from '../../006-telemetry-simulator/verification/fixture-server.mjs';
import assert from 'node:assert/strict';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output=fileURLToPath(new URL('../artifacts/vehicle-tracking/',import.meta.url));
await mkdir(output,{recursive:true});
const fixture=await startFixtureServer();
const browser=await chromium.launch({channel:'msedge',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});
page.setDefaultTimeout(12000);
const checks=[],errors=[];let mode='live',flowRequests=0,etaRequests=0,delayTripEta=false,delayedEtaStarted=false;
page.on('pageerror',error=>errors.push(error.message));
await page.route('**/*',async route=>{
  const url=new URL(route.request().url()),path=url.pathname;
  if(path.startsWith('/api/')) {
    if(path.endsWith('/eta')) {
      etaRequests++;
      const tripId=Number(path.split('/')[4]);
      if(delayTripEta && tripId===1){delayedEtaStarted=true;await new Promise(resolve=>setTimeout(resolve,1200));}
      if(mode==='error') return route.fulfill({status:503,json:{detail:'Fixture ETA unavailable'}});
      const stamp=new Date().toISOString(),stale=new Date(Date.now()-120000).toISOString();
      const source=mode==='fallback'?'ROUTE_SNAPSHOT':mode==='stale'?'HERE_LAST_KNOWN':'HERE_LIVE';
      const blocked=mode==='blocked',countdown=tripId===2?180:mode==='jam'?1200:600;
      return route.fulfill({json:{tripId,routeId:1,calculatedAt:stamp,source,status:blocked?'BLOCKED':mode==='stale'?'STALE':'AVAILABLE',
        nextStopSequence:2,baselineRemainingSeconds:500,totalRemainingSeconds:countdown,
        trafficObservedAt:mode==='fallback'?null:mode==='stale'?stale:stamp,trafficFetchedAt:mode==='fallback'?null:mode==='stale'?stale:stamp,
        stops:[{sequenceNumber:2,stationName:tripId===2?'Trạm của chuyến mới':'B · SIMULATOR',state:'NEXT',etaSeconds:blocked?null:countdown,etaAt:blocked?null:new Date(Date.now()+countdown*1000).toISOString(),source}],
        affectedSegments:mode==='jam'?[{kind:'FLOW',destinationStopSequence:2,jamFactor:9,traversability:'open'},
          {kind:'INCIDENT',destinationStopSequence:2,traversability:'accident'},{kind:'INCIDENT',destinationStopSequence:2,traversability:'construction'}]:[],
        warning:mode==='fallback'?'Traffic unavailable; using route snapshot':null}}).catch(()=>{});
    }
    if(path.endsWith('/traffic/flow')) {flowRequests++;return route.fulfill({json:{source:'HERE_LIVE',status:'AVAILABLE',results:[]}});}
    if(path.endsWith('/traffic/incidents')) return route.fulfill({json:{source:'HERE_LIVE',status:'AVAILABLE',results:[]}});
    return route.continue({url:fixture.api+path.replace(/^\/api\/v1/,'')+url.search});
  }
  if(!['localhost','127.0.0.1'].includes(url.hostname)) return route.abort();
  return route.continue();
});
const panel=()=>page.getByRole('region',{name:'Vị trí, vận tốc và ETA mô phỏng',exact:true});
const card=()=>page.getByRole('region',{name:'Vị trí, vận tốc và ETA xe',exact:true});
async function check(name,work){await work();checks.push(name);console.log('PASS',name);}
async function etaText(text){await page.waitForFunction(value=>document.querySelector('[data-testid="simulation-eta"]')?.textContent.includes(value),text);}
async function refreshMode(value){mode=value;await page.clock.fastForward(10100);await page.waitForTimeout(150);}
async function markerReachable() {
  await page.waitForFunction(()=>{
    const marker=document.querySelector('.live-vehicle-marker[data-vehicle-id="1"]');
    if(!marker)return false;
    const rect=marker.getBoundingClientRect(),target=document.elementFromPoint(rect.x+rect.width/2,rect.y+rect.height/2);
    return !!target && marker.contains(target);
  });
}
try {
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
  await page.locator('.simulation-connection[data-state=live]').waitFor();
  await check('no all-road inspection or viewport flow requests',async()=>{
    assert.equal(await page.locator('[data-traffic-road]').count(),0);assert.equal(flowRequests,0);assert.equal(etaRequests,0);
  });
  await page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name:'Mô phỏng',exact:true}).click();
  await page.getByLabel('Chọn chuyến mô phỏng').selectOption('1');
  await page.getByText('Đang tải tuyến mô phỏng…').waitFor({state:'hidden'});
  await check('waiting trip shows stationary start preview before playback',async()=>{
    // Feature 011 intentionally waits for play before polling traffic ETA.
    await page.locator('.simulation-ready').filter({hasText:'0 km/h'}).waitFor();
    assert.equal(etaRequests,0);
  });
  await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
  await check('SSE updates vehicle location and simulator speed',async()=>{
    await page.getByTestId('simulation-speed').filter({hasText:'28.1'}).waitFor();
    await etaText('10 phút');assert.ok((await panel().innerText()).includes('B · SIMULATOR'));
    const before=await panel().locator('.trip-traffic-position').innerText();
    await page.waitForFunction(value=>document.querySelector('.simulator-content .trip-traffic-position')?.textContent!==value,before);
    assert.equal(await page.locator('.live-vehicle-marker[data-vehicle-id="1"]').count(),1);
    await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
    await page.locator('.simulator-content').evaluate(element=>element.parentElement.scrollTop=0);
    await page.screenshot({path:output+'simulator-desktop.png'});
  });
  await page.getByRole('button',{name:'Tạm dừng mô phỏng',exact:true}).click();
  await page.getByTestId('simulation-speed').filter({hasText:'0.0'}).waitFor();
  await page.clock.install();
  await check('congestion, accident and construction affect the displayed trip ETA',async()=>{
    await refreshMode('jam');await etaText('20 phút');
    for(const expected of ['Ùn tắc nghiêm trọng','Tai nạn','Công trường / thi công']) assert.ok((await panel().innerText()).includes(expected));
  });
  await check('blocked road never displays the baseline countdown',async()=>{
    await refreshMode('blocked');await etaText('Đường bị chặn');
    assert.ok((await panel().innerText()).includes('Chưa thể xác định thời gian đến trạm'));
  });
  await check('stale and route snapshot states are explicit',async()=>{
    await refreshMode('stale');await etaText('10 phút');
    assert.ok((await panel().innerText()).includes('HERE · dữ liệu gần nhất'));
    await refreshMode('fallback');await panel().getByText('Ước tính theo tuyến đã lưu',{exact:true}).waitFor();
  });
  await check('ETA error is visible and retry recovers',async()=>{
    await refreshMode('error');await panel().getByText('Chưa làm mới được ETA.',{exact:false}).waitFor();
    mode='live';await panel().getByRole('button',{name:'Thử lại',exact:true}).click();
    await panel().getByText('HERE Traffic',{exact:true}).waitFor();
  });
  await check('locate vehicle opens speed, stop ETA and follow controls on map',async()=>{
    await page.getByRole('button',{name:'Xem vị trí xe và tuyến đang chạy',exact:true}).click();
    await card().waitFor();await card().getByText('B · SIMULATOR',{exact:true}).waitFor();
    assert.ok((await card().innerText()).includes('10 phút'));
    assert.equal(await page.getByRole('button',{name:'Bỏ theo xe',exact:true}).getAttribute('aria-pressed'),'true');
    await markerReachable();
    await page.screenshot({path:output+'vehicle-desktop.png'});
  });
  await check('vehicle card fits mobile viewport and selection can close',async()=>{
    await page.setViewportSize({width:320,height:720});await page.waitForTimeout(250);
    const rect=await page.locator('.live-follow').boundingBox();
    assert.ok(rect.x>=0&&rect.x+rect.width<=320&&rect.y+rect.height<=720);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await markerReachable();
    await card().locator('summary').click();await markerReachable();
    await page.screenshot({path:output+'vehicle-mobile.png'});
    await page.getByRole('button',{name:'Bỏ chọn xe',exact:true}).click();await card().waitFor({state:'hidden'});
  });
  await check('GPS vehicle uses its telemetry speed rather than the simulator frame',async()=>{
    await page.setViewportSize({width:1440,height:900});
    fixture.snapshot().positions[0].speedKmh=17.4;fixture.gpsAge(0);
    await page.waitForTimeout(300);
    await page.locator('.live-vehicle-marker[data-vehicle-id="1"]').click();
    await card().getByTestId('vehicle-speed').filter({hasText:'17.4'}).waitFor();
    assert.ok((await page.locator('.live-follow-actions').innerText()).includes('GPS'));
    await page.getByRole('button',{name:'Bỏ chọn xe',exact:true}).click();
  });
  await check('switching trips ignores a delayed ETA for the previous trip',async()=>{
    // Create a second trip only in the isolated fixture via the normal replay flow.
    delayTripEta=true;await page.clock.fastForward(10100);
    await page.waitForTimeout(100);assert.equal(delayedEtaStarted,true);
    await page.getByRole('button',{name:'Chạy lại',exact:true}).click();
    await page.getByRole('button',{name:'Tạo chuyến chạy lại',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('[aria-label="Chọn chuyến mô phỏng"]')?.value==='2');
    await etaText('3 phút');await page.waitForTimeout(1500);
    assert.ok((await panel().innerText()).includes('Trạm của chuyến mới'));
    assert.ok(!(await panel().innerText()).includes('B · SIMULATOR'));
    await etaText('3 phút');delayTripEta=false;
  });
  await check('clearing trip selection removes telemetry and stops its ETA polling',async()=>{
    await page.getByLabel('Chọn chuyến mô phỏng').selectOption('');
    await panel().waitFor({state:'hidden'});
    const before=etaRequests;await page.clock.fastForward(20100);await page.waitForTimeout(250);
    assert.equal(etaRequests,before);
  });
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({timestamp:new Date().toISOString(),mode:'vehicle-tracking-fixture',checks,pageErrors:errors,flowRequests,etaRequests},null,2));
} catch(error){await page.screenshot({path:output+'failure.png'});throw error;}
finally {await browser.close();await fixture.close();}
