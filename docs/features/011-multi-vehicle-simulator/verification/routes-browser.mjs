// Regression for two moving vehicles whose routes share a start then diverge.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { startFleetFixture } from './fixture-server.mjs';
import assert from 'node:assert/strict';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output=fileURLToPath(new URL('../artifacts/routes/',import.meta.url));await mkdir(output,{recursive:true});
const fixture=await startFleetFixture({distinctRoutes:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});page.setDefaultTimeout(12000);
const checks=[],errors=[];page.on('pageerror',error=>errors.push(error.message));
await page.route('**/*',route=>{const url=new URL(route.request().url());
  if(url.pathname.startsWith('/api/'))return route.continue({url:fixture.api+url.pathname.replace(/^\/api\/v1/,'')+url.search});
  return ['127.0.0.1','localhost'].includes(url.hostname)?route.continue():route.abort();});
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const paths=()=>page.locator('[data-simulation-route-trip]');
const path=id=>page.locator(`[data-simulation-route-trip="${id}"]`);
const nav=name=>page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name,exact:true}).click();
async function check(name,work){await work();checks.push(name);console.log('PASS',name);}
async function count(n){await page.waitForFunction(n=>document.querySelectorAll('[data-simulation-route-trip]').length===n,n);}
async function ready(){await page.waitForFunction(()=>!document.querySelector('.simulation-fleet-list')?.textContent.includes('Đang tải lộ trình đội xe'));}
async function choose(id){const picker=page.locator('.simulation-fleet-picker');if(await picker.evaluate(e=>!e.open))await picker.locator('summary').click();
  await page.locator(`[data-simulation-trip="${id}"]`).click();await selected(id);}
async function selected(id){await page.waitForFunction(id=>document.querySelector('[aria-label="Chọn chuyến mô phỏng"]')?.value===String(id),id);}
async function clickRoute(id,fraction){
  const point=await path(id).evaluate((element,fraction)=>{
    const p=element.getPointAtLength(element.getTotalLength()*fraction),screen=new DOMPoint(p.x,p.y).matrixTransform(element.getScreenCTM());
    return {x:screen.x,y:screen.y};
  },fraction);
  await page.mouse.click(point.x,point.y);
}
try{
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
  await page.locator('.simulation-connection[data-state=live]').waitFor();await nav('Mô phỏng');
  await check('all three fleet routes appear before selection, with distinct geometry and no commands',async()=>{
    await count(3);await ready();assert.equal(fixture.writes.length,0);
    assert.notEqual(await path(1).getAttribute('d'),await path(2).getAttribute('d'));
    assert.notEqual(await path(1).getAttribute('stroke'),await path(2).getAttribute('stroke'));
    await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();await wait(400);
  });
  await check('start A and B on different routes; both paths persist when changing selection',async()=>{
    await choose(1);await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
    await page.locator('.live-vehicle-marker[data-vehicle-id="1"]').waitFor();
    await choose(2);await page.getByRole('button',{name:'Bắt đầu mô phỏng',exact:true}).click();
    await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').waitFor();await count(3);
    assert.equal(await path(2).getAttribute('stroke'),'#22d3ee');
    assert.notEqual(await path(1).getAttribute('stroke'),'#22d3ee');
    assert.equal(fixture.runs.get(1).status,'RUNNING');assert.equal(fixture.runs.get(2).status,'RUNNING');
  });
  await check('fit all contains full paths, clicking another route selects its vehicle without stopping either run',async()=>{
    await page.getByRole('button',{name:'Bỏ chọn xe',exact:true}).click();
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();await wait(500);
    const boxes=await paths().evaluateAll(elements=>elements.map(e=>{const b=e.getBoundingClientRect();return {x:b.x,y:b.y,right:b.right,bottom:b.bottom};}));
    assert.ok(boxes.every(b=>b.x>=0&&b.y>=70&&b.right<=1440&&b.bottom<=900));
    const writes=fixture.writes.length;
    await clickRoute(1,.7);await selected(1);await count(3);
    assert.equal(await path(1).getAttribute('stroke'),'#22d3ee');assert.equal(fixture.writes.length,writes);
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();await wait(400);
    await page.screenshot({path:output+'two-running-routes.png'});
  });
  await check('shared road segment opens a chooser for both vehicles; route is keyboard accessible',async()=>{
    await page.getByRole('button',{name:'Bỏ chọn xe',exact:true}).click();
    await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();await wait(400);
    await clickRoute(1,.1);await page.locator('[data-simulation-route-choice="2"]').waitFor();
    assert.equal(await page.locator('[data-simulation-route-choice]').count(),2);
    await page.locator('[data-simulation-route-choice="2"]').click();await selected(2);
    await path(1).focus();await page.keyboard.press('Enter');await selected(1);await count(3);
  });
  await check('SSE ticks reuse route paths and cached route requests; reload restores every route',async()=>{
    await ready();await wait(400);
    const gets=fixture.detailGets.length;
    await path(2).evaluate(e=>{e.dataset.stabilityProbe='kept';});await wait(900);
    assert.equal(await path(2).getAttribute('data-stability-probe'),'kept');assert.equal(fixture.detailGets.length,gets);
    await page.reload({waitUntil:'domcontentloaded'});await nav('Mô phỏng');await count(3);
    assert.equal(fixture.runs.get(1).status,'RUNNING');assert.equal(fixture.runs.get(2).status,'RUNNING');
  });
  await check('route overlay toggle and leaving simulator clean up all fleet paths',async()=>{
    const writes=fixture.writes.length;
    await page.getByLabel('Lớp bản đồ',{exact:true}).click();
    await page.getByRole('checkbox',{name:'Tuyến & điểm nháp',exact:true}).uncheck();await count(0);
    await page.getByRole('checkbox',{name:'Tuyến & điểm nháp',exact:true}).check();await count(3);
    await nav('Theo dõi');await count(0);await nav('Mô phỏng');await count(3);assert.equal(fixture.writes.length,writes);
  });
  await check('one route request failure preserves other running routes and can retry',async()=>{
    fixture.failTrip(2);await page.reload({waitUntil:'domcontentloaded'});await nav('Mô phỏng');await ready();await count(2);
    await page.getByRole('button',{name:'Thử tải lại tuyến',exact:true}).waitFor();assert.equal(await path(1).count(),1);
    fixture.failTrip(null);await page.getByRole('button',{name:'Thử tải lại tuyến',exact:true}).click();await count(3);
  });
  await check('invalid geometry rejects only that route, retains its waiting marker, retry restores the path',async()=>{
    fixture.invalidRoute(3);await page.reload({waitUntil:'domcontentloaded'});await nav('Mô phỏng');await ready();await count(2);
    await page.locator('.simulation-waiting-marker').waitFor();
    fixture.invalidRoute(null);await page.getByRole('button',{name:'Thử tải lại tuyến',exact:true}).click();await count(3);
  });
  await check('mobile selects individual vehicles while retaining all paths without horizontal overflow',async()=>{
    await page.setViewportSize({width:320,height:720});await choose(2);await count(3);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:output+'mobile-routes.png'});
  });
  await check('completed trip route leaves fleet without hiding another running vehicle',async()=>{
    fixture.completeTrip(2);await count(2);assert.equal(await path(2).count(),0);assert.equal(await path(1).count(),1);
    await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').waitFor({state:'detached'});
    assert.equal(await page.locator('.live-vehicle-marker[data-vehicle-id="1"]').count(),1);
    assert.equal(fixture.runs.get(1).status,'RUNNING');
    await nav('Theo dõi');await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').waitFor();
  });
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({timestamp:new Date().toISOString(),mode:'distinct-route-fleet-fixture',checks,pageErrors:errors,commands:fixture.writes},null,2));
}catch(error){await page.screenshot({path:output+'failure.png'});throw error;}
finally{await browser.close();await fixture.close();}
