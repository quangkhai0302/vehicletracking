// HISTORICAL: viewport-road inspection was withdrawn by the user on 2026-09-14.
// Do not use this script to verify current source. Use tracking-browser.mjs.
// Isolated viewport-road fixtures; no real API/provider or database writes.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output = fileURLToPath(new URL('../artifacts/roads-fixture/', import.meta.url));
await mkdir(output,{recursive:true});
const browser = await chromium.launch({channel:'msedge',headless:true});
const page = await browser.newPage({viewport:{width:1440,height:900},hasTouch:true});
page.setDefaultTimeout(12000);
const errors=[],checks=[];
page.on('pageerror',error=>errors.push(error.message));
let mode='live',flowRequests=0,slowRequest=0;
const hostile='<img src=x onerror="window.roadXss=true">';
const envelope=(results,stale=false)=>({source:stale?'HERE_LAST_KNOWN':'HERE_LIVE',status:stale?'STALE':'AVAILABLE',
  observedAt:null,fetchedAt:new Date(Date.now()-(stale?120000:0)).toISOString(),ageSeconds:stale?120:0,warning:null,results});
await page.route('**/*',async route=>{
  const url=new URL(route.request().url()),path=url.pathname;
  const json=(value,status=200)=>route.fulfill({status,json:value}).catch(()=>{});
  if(path.startsWith('/api/')) {
    if(path.endsWith('/telemetry/snapshot')) return json({serverTime:new Date().toISOString(),positions:[],simulations:[],trips:[],checkIns:[],notifications:[]});
    if(path.endsWith('/telemetry/stream')) return route.abort();
    if(path.endsWith('/traffic/flow')) {
      flowRequests++;
      const requestMode=mode,sequence=slowRequest++;
      if(requestMode==='slow'&&sequence===0) await new Promise(resolve=>setTimeout(resolve,1500));
      if(requestMode==='error') return json({detail:'Fixture flow failure'},503);
      const lat=(Number(url.searchParams.get('south'))+Number(url.searchParams.get('north')))/2;
      const lng=(Number(url.searchParams.get('west'))+Number(url.searchParams.get('east')))/2;
      const roads=[0,1].map(i=>({id:`road-${i}`,description:requestMode==='slow'?(sequence===0?'OLD VIEWPORT':'NEW VIEWPORT'):i===0?'Đường độc lập · fixture '+hostile:'Đường thứ hai · fixture',
        points:[[lat+i*.006,lng-.015],[lat+i*.006,lng+.015]],lengthMeters:2400,speedKmh:requestMode==='zero'?0:i===0?20:35,
        freeFlowKmh:40,jamFactor:6,traversability:requestMode==='closed'?'closed':'open',confidence:.9}));
      return json(requestMode==='unavailable'?{...envelope([]),source:'UNAVAILABLE',status:'UNAVAILABLE'}:envelope(requestMode==='empty'?[]:roads,requestMode==='stale'));
    }
    if(path.endsWith('/traffic/incidents')) return mode==='incident-error'?json({detail:'Fixture incident failure'},503):json(envelope([]));
    if(path.includes('/traffic/tiles/')) return route.fulfill({status:200,contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64')});
    return json([]);
  }
  if(!['localhost','127.0.0.1'].includes(url.hostname)) return route.abort();
  return route.continue();
});
const road=()=>page.locator('[data-traffic-road="road-0"]');
const card=()=>page.locator('.route-inspection-card[data-kind="road"]');
async function spot() {
  return road().evaluate(path=>{
    for(const ratio of [.5,.6,.4,.7,.3]) {
      const point=path.getPointAtLength(path.getTotalLength()*ratio),pixel=new DOMPoint(point.x,point.y).matrixTransform(path.getScreenCTM());
      if(document.elementFromPoint(pixel.x,pixel.y)===path) return {x:pixel.x,y:pixel.y};
    }
    throw new Error('No reachable road point');
  });
}
async function hover() {const point=await spot();await page.mouse.move(point.x,point.y);await card().waitFor();return point;}
async function open(waitRoad=true) {
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
  if(waitRoad) await road().waitFor({state:'attached'});
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  await page.waitForTimeout(300);
}
async function text(value){await page.waitForFunction(value=>document.querySelector('.route-inspection-card[data-kind="road"]')?.textContent.includes(value),value);}
async function check(name,work){await work();checks.push(name);console.log('PASS',name);}
try {
  await check('roads load and hover without any saved or selected route',async()=>{
    await open();assert.equal(await page.locator('[data-route-section]').count(),0);
    assert.equal(await page.locator('[data-traffic-road]').count(),2);
    const before=flowRequests;await hover();await text('20 km/h');
    assert.ok((await card().innerText()).includes('Hướng đoạn đường: Đông'));
    assert.ok(!(await card().innerText()).includes('Đoạn tuyến đã lưu'));
    assert.ok((await card().innerText()).includes(hostile));assert.equal(await card().locator('img').count(),0);
    assert.equal(await page.evaluate(()=>window.roadXss),undefined);
    await page.waitForTimeout(500);assert.equal(flowRequests,before,'hover does not fetch per pointer');
    await page.screenshot({path:output+'desktop.png'});
  });
  await check('road click pin, keyboard and Escape',async()=>{
    const point=await spot();await page.mouse.click(point.x,point.y);await page.mouse.move(30,80);
    assert.equal(await card().getAttribute('data-pinned'),'true');await page.keyboard.press('Escape');await card().waitFor({state:'hidden'});
    await road().focus();await page.keyboard.press('Enter');await text('20 km/h');
    await page.getByRole('button',{name:'Đóng thông tin đoạn đường'}).click();await card().waitFor({state:'hidden'});
  });
  for(const [value,expected] of [['stale','Dữ liệu gần nhất'],['closed','Đang bị chặn'],['zero','Dòng xe đang dừng'],['incident-error','Không tải được thông tin sự cố']]) {
    await check('road '+value,async()=>{mode=value;await open();await hover();await text(expected);
      if(value==='zero') assert.ok((await card().innerText()).includes('Chưa ước tính được'));
    });
  }
  for(const value of ['error','empty','unavailable']) {
    await check('road '+value+' removes hit paths and explains missing data',async()=>{
      mode=value;await open(false);await page.waitForFunction(()=>!document.querySelector('.gm-traffic-sub')?.textContent.includes('Đang tải'));
      assert.equal(await page.locator('[data-traffic-road]').count(),0);
      assert.equal(await card().count(),0);
      assert.ok(await page.locator('.gm-traffic-sub').innerText());
    });
  }
  await check('toggling traffic removes pinned road card and restores roads',async()=>{
    mode='live';await open();const point=await hover();await page.mouse.click(point.x,point.y);
    await page.getByLabel('Lớp bản đồ',{exact:true}).click();
    await page.getByRole('checkbox',{name:'Giao thông trực tiếp'}).uncheck();await card().waitFor({state:'hidden'});
    assert.equal(await page.locator('[data-traffic-road]').count(),0);
    await page.getByRole('checkbox',{name:'Giao thông trực tiếp'}).check();await road().waitFor({state:'attached'});
    await page.getByLabel('Lớp bản đồ',{exact:true}).click();await hover();await text('20 km/h');
  });
  await check('late viewport response does not replace new viewport data',async()=>{
    mode='slow';slowRequest=0;await open(false);
    await page.mouse.move(730,480);await page.mouse.down();await page.mouse.move(190,480,{steps:10});await page.mouse.up();
    await page.waitForFunction(()=>document.querySelector('[data-traffic-road]')?.getAttribute('aria-label')?.includes('NEW VIEWPORT'));
    await page.waitForTimeout(1700);await hover();await text('NEW VIEWPORT');
    assert.ok(!(await card().innerText()).includes('OLD VIEWPORT'));
  });
  await check('touch road on 320 px with no route and no invisible control blocker',async()=>{
    mode='live';await page.setViewportSize({width:320,height:720});await open();
    const point=await spot();await page.touchscreen.tap(point.x,point.y);await text('20 km/h');
    assert.equal(await card().getAttribute('data-pinned'),'true');
    const rect=await card().boundingBox();assert.ok(rect.x>=0&&rect.y>=0&&rect.x+rect.width<=320&&rect.y+rect.height<=720);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:output+'mobile-320.png'});
    await page.getByRole('button',{name:'Đóng thông tin đoạn đường'}).click();
  });
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({timestamp:new Date().toISOString(),mode:'roads-browser-fixture',checks,pageErrors:errors,flowRequests},null,2));
} catch(error){await page.screenshot({path:output+'failure.png'});throw error;}
finally{await browser.close();}
