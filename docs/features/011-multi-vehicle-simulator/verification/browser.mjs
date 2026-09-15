import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { startFleetFixture } from './fixture-server.mjs';
import assert from 'node:assert/strict';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output=fileURLToPath(new URL('../artifacts/',import.meta.url));await mkdir(output,{recursive:true});
const fixture=await startFleetFixture(),browser=await chromium.launch({channel:'msedge',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});page.setDefaultTimeout(12000);
const checks=[],errors=[];page.on('pageerror',error=>errors.push(error.message));
await page.route('**/*',route=>{const url=new URL(route.request().url());if(url.pathname.startsWith('/api/'))return route.continue({url:fixture.api+url.pathname.replace(/^\/api\/v1/,'')+url.search});
  return ['127.0.0.1','localhost'].includes(url.hostname)?route.continue():route.abort();});
const mode=()=>page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name:'Mô phỏng',exact:true}).click();
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function check(name,work){await work();checks.push(name);console.log('PASS',name);}
async function selected(id){await page.waitForFunction(id=>document.querySelector('[aria-label="Chọn chuyến mô phỏng"]')?.value===String(id),id);}
async function choose(id){const picker=page.locator('.simulation-fleet-picker');if(!await picker.getAttribute('open')){
  if(await picker.evaluate(element=>!element.open))await picker.locator('summary').click();
}await page.locator(`[data-simulation-trip="${id}"]`).click();}
try{
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});await page.locator('.simulation-connection[data-state=live]').waitFor();
  await check('opening simulator stages three vehicles at start without commands',async()=>{
    await mode();await page.locator('.simulation-waiting-marker[data-waiting-count="2"]').waitFor();
    assert.equal(await page.locator('.simulation-waiting-marker').count(),2);
    assert.equal(await page.locator('[data-simulation-trip]').count(),3);assert.equal(fixture.writes.length,0);
    assert.equal(fixture.snapshot().positions.length,0);assert.ok(fixture.snapshot().checkIns.every(item=>item.visits.length===0));
    await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();await page.screenshot({path:output+'waiting-desktop.png'});
  });
  await check('same-station popup selects vehicle A without starting it',async()=>{
    await page.locator('.simulation-waiting-marker[data-waiting-count="2"]').click();
    await page.locator('[data-waiting-trip="1"]').click();await selected(1);
    await page.locator('.simulation-ready').filter({hasText:'0 km/h'}).waitFor();assert.equal(fixture.writes.length,0);
  });
  await check('start A replaces its waiting preview with telemetry',async()=>{
    await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
    await page.locator('.live-vehicle-marker[data-vehicle-id="1"]').waitFor();
    assert.equal(fixture.runs.get(1).status,'RUNNING');
    await page.waitForFunction(()=>document.querySelectorAll('.simulation-waiting-marker[data-waiting-count="1"]').length===2);
  });
  await check('start B while A keeps moving; selecting does not pause A',async()=>{
    const before=fixture.runs.get(1).elapsedSeconds,writes=fixture.writes.length;
    await choose(2);await selected(2);assert.equal(fixture.writes.length,writes);
    await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
    await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').waitFor();await wait(500);
    assert.ok(fixture.runs.get(1).elapsedSeconds>before);assert.equal(fixture.runs.get(1).status,'RUNNING');assert.equal(fixture.runs.get(2).status,'RUNNING');
  });
  await check('clicking running vehicle opens its own controls; pause A leaves B running',async()=>{
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();
    await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').click();
    await page.locator('[data-simulation-vehicle="1"]').click();await selected(1);
    await page.getByRole('button',{name:'Tạm dừng mô phỏng',exact:true}).click();
    const a=fixture.runs.get(1).elapsedSeconds,b=fixture.runs.get(2).elapsedSeconds;await wait(750);
    assert.equal(fixture.runs.get(1).elapsedSeconds,a);assert.ok(fixture.runs.get(2).elapsedSeconds>b);
  });
  await check('multipliers are per vehicle and reload keeps both runs',async()=>{
    await page.getByLabel('Tốc độ 5x',{exact:true}).click();assert.equal(fixture.runs.get(1).multiplier,5);assert.equal(fixture.runs.get(2).multiplier,1);
    await page.getByRole('button',{name:'Tiếp tục mô phỏng',exact:true}).click();
    await page.reload({waitUntil:'domcontentloaded'});await mode();
    await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').waitFor();assert.equal(fixture.runs.get(1).status,'RUNNING');
    await choose(1);await page.getByLabel('Tốc độ 5x',{exact:true}).waitFor();
    assert.equal(await page.getByLabel('Tốc độ 5x',{exact:true}).getAttribute('aria-pressed'),'true');
    await page.locator('.simulator-content').evaluate(element=>element.parentElement.scrollTop=0);await page.screenshot({path:output+'running-desktop.png'});
  });
  await check('leaving mode removes waiting layer but does not stop running vehicles',async()=>{
    const writes=fixture.writes.length;
    await page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name:'Theo dõi',exact:true}).click();
    assert.equal(await page.locator('.simulation-waiting-marker').count(),0);assert.equal(fixture.writes.length,writes);
    assert.equal(fixture.runs.get(1).status,'RUNNING');assert.equal(fixture.runs.get(2).status,'RUNNING');
  });
  await check('failed waiting-location load can retry without moving or starting vehicles',async()=>{
    fixture.failTrip(3);await page.reload({waitUntil:'domcontentloaded'});await mode();
    await page.getByRole('button',{name:'Thử lại vị trí chờ',exact:true}).waitFor();assert.equal(await page.locator('.simulation-waiting-marker').count(),0);
    fixture.failTrip(null);await page.getByRole('button',{name:'Thử lại vị trí chờ',exact:true}).click();await page.locator('.simulation-waiting-marker').waitFor();
    assert.equal(fixture.runs.has(3),false);
  });
  await check('mobile 320px can choose a waiting vehicle and access its controls',async()=>{
    await page.setViewportSize({width:320,height:720});await choose(3);await selected(3);
    await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).scrollIntoViewIfNeeded();
    assert.equal(await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).isEnabled(),true);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:output+'mobile-320.png'});
  });
  assert.deepEqual(errors,[]);await writeFile(output+'results.json',JSON.stringify({timestamp:new Date().toISOString(),mode:'multi-vehicle-fixture',checks,pageErrors:errors,commands:fixture.writes},null,2));
}catch(error){await page.screenshot({path:output+'failure.png'});throw error;}finally{await browser.close();await fixture.close();}
