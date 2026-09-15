// UI verification with isolated fixtures. No application DB or external provider is called.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output = fileURLToPath(new URL('../artifacts/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, hasTouch: true });
const page = await context.newPage();
page.setDefaultTimeout(10000);
const errors = [], checks = [];
page.on('pageerror', error => errors.push(error.message));
const stamp = new Date().toISOString();
const points = [[10.772,106.68],[10.772,106.70],[10.792,106.72]];
function encode(points) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const unsigned = value => { let result=''; while(value>31) { result+=alphabet[(value&31)|32]; value>>>=5; } return result+alphabet[value]; };
  const previous=[0,0];
  return 'BF'+points.map(point=>point.map((value,axis)=>{
    const scaled=Math.round(value*1e5), delta=scaled-previous[axis]; previous[axis]=scaled;
    return unsigned(delta<0?-delta*2-1:delta*2);
  }).join('')).join('');
}
const stations = points.map((point, i) => ({ id:i+1, name:`Trạm ${i+1}`, latitude:point[0], longitude:point[1], address:'Fixture', checkinRadiusMeters:50, active:true, createdAt:stamp, updatedAt:stamp }));
const detail = { id:1, name:'Tuyến kiểm tra tooltip', transportMode:'CAR', routingProvider:'HERE', totalDistanceMeters:5000,
  estimatedTravelDurationSeconds:720, baseTravelDurationSeconds:500, totalDwellDurationSeconds:60, estimatedTripDurationSeconds:780,
  calculatedAt:stamp, createdAt:stamp, estimatedDepartureAt:stamp,
  stops:stations.map((station,i)=>({stationId:station.id,stationName:station.name,latitude:station.latitude,longitude:station.longitude,
    sequenceNumber:i+1,role:i===0?'START':i===2?'END':'STOP',dwellDurationSeconds:i===1?60:0,arrivalOffsetSeconds:i*360,
    departureOffsetSeconds:i*360+(i===1?60:0),distanceFromPreviousMeters:i?2500:0,travelDurationFromPreviousSeconds:i?360:0})),
  sections: [0,1].map(i=>({sectionSequence:i+1,destinationStopSequence:i+2,encodedPolyline:encode(points.slice(i,i+2)),
    distanceMeters:i===0?2000:3000,travelDurationSeconds:360,baseTravelDurationSeconds:250})) };
let mode='live', flowRequests=0, delayFirst=false;
const hostile = '<img src=x onerror="window.tooltipXss=true">';
function envelope(results, stale=false) { return {source:stale?'HERE_LAST_KNOWN':'HERE_LIVE', status:stale?'STALE':'AVAILABLE',
  observedAt:null,fetchedAt:new Date(Date.now()-(stale?90000:0)).toISOString(),ageSeconds:stale?90:0,warning:null,results}; }
await context.route('**/*', async route => {
  const url = new URL(route.request().url()), path=url.pathname;
  if (path.startsWith('/api/')) {
    const json=(value,status=200)=>route.fulfill({status,json:value}).catch(()=>{});
    if(path.endsWith('/stations')) return json(stations);
    if(path.endsWith('/routes')) return json([{...detail,startStationName:'Trạm 1',endStationName:'Trạm 3',stopCount:3}]);
    if(path.endsWith('/routes/1')) return json(detail);
    if(path.endsWith('/telemetry/snapshot')) return json({serverTime:new Date().toISOString(),positions:[],simulations:[],trips:[],checkIns:[],notifications:[]});
    if(path.endsWith('/telemetry/stream')) return route.abort();
    if(path.endsWith('/traffic/flow')) {
      flowRequests++;
      const requestMode=mode;
      if(delayFirst && Number(url.searchParams.get('west'))<106.69) await new Promise(resolve=>setTimeout(resolve,800));
      if(requestMode==='error') return json({detail:'Fixture unavailable'},503);
      const flows=[0,1].map(i=>({id:`flow-${i}`,description:i===0?hostile:'Đoạn thứ hai · fixture',points:points.slice(i,i+2),
        lengthMeters:i===0?2000:3000,speedKmh:requestMode==='zero'?0:i===0?20:30,freeFlowKmh:40,jamFactor:6,
        traversability:requestMode==='closed'?'closed':'open',confidence:.9}));
      return json(envelope(requestMode==='empty'?[]:requestMode==='opposite'?flows.map(flow=>({...flow,points:[...flow.points].reverse()})):flows,requestMode==='stale'));
    }
    if(path.endsWith('/traffic/incidents')) return json(envelope([{id:'incident-1',description:'Thi công gần tuyến · fixture',type:'construction',criticality:'minor',
      points:[[10.772,106.68],[10.772,106.695]],center:[10.772,106.6875],status:'ACTIVE'}]));
    if(path.includes('/traffic/tiles/')) return route.fulfill({status:200,contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64')});
    return json([]);
  }
  if(url.hostname!=='127.0.0.1' && url.hostname!=='localhost') return route.abort();
  return route.continue();
});
const hit = i => page.locator(`[data-route-section="${i}"]`);
const card = page.locator('.route-inspection-card');
async function spot(i=1, fraction=.5) {
  return hit(i).evaluate((element,fraction)=>{
    const point=element.getPointAtLength(element.getTotalLength()*fraction);
    const screen=new DOMPoint(point.x,point.y).matrixTransform(element.getScreenCTM());
    return {x:screen.x,y:screen.y};
  },fraction);
}
async function hover(i=1,fraction=.5) { const point=await spot(i,fraction); await page.mouse.move(point.x,point.y); }
async function waitText(text) { await page.waitForFunction(text=>document.querySelector('.route-inspection-card')?.textContent.includes(text),text); }
async function open() {
  await page.goto('http://127.0.0.1:5173', {waitUntil:'domcontentloaded'});
  await page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name:'Tuyến & trạm',exact:true}).click();
  await page.locator('.planning-tabs').getByRole('button',{name:'Tuyến đường',exact:false}).click();
  await page.locator('.route-card').first().click();
  // Hit paths are intentionally transparent; horizontal SVG paths have zero geometric height.
  await hit(1).waitFor({state:'attached'});
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  await page.getByRole('button',{name:'Vừa khung lộ trình',exact:true}).click();
  await page.waitForTimeout(450);
}
async function check(name,work) {
  if(process.env.VERIFICATION_CASE && !name.includes(process.env.VERIFICATION_CASE)) return;
  await work(); checks.push(name); console.log('PASS',name);
}
try {
  await check('hover metrics, scope, freshness and escaped content', async()=>{
    await open(); await hover(); await waitText('20 km/h');
    const text=await card.innerText();
    assert.ok(text.includes('6 phút') && text.includes('+3 phút') && text.includes('2 km'));
    assert.ok(text.includes('Trạm 1 → Trạm 2') && text.includes('HERE') && text.includes(hostile));
    assert.ok(text.includes('Thi công gần tuyến'));
    assert.equal(await card.locator('img').count(),0);
    assert.equal(await page.evaluate(()=>window.tooltipXss),undefined);
    await page.screenshot({path:output+'desktop-fixture.png'});
  });
  await check('same-cell mouse movement reuses request and pin/Escape works',async()=>{
    const before=flowRequests;
    for(const fraction of [.51,.52,.53,.5]) { await hover(1,fraction); await page.waitForTimeout(50); }
    await page.waitForTimeout(300); assert.equal(flowRequests,before);
    const point=await spot(); await page.mouse.click(point.x,point.y); await page.mouse.move(30,80);
    assert.equal(await card.getAttribute('data-pinned'),'true');
    await page.keyboard.press('Escape'); await card.waitFor({state:'hidden'});
  });
  await check('keyboard focus and Enter pin',async()=>{
    await hit(1).focus(); await page.keyboard.press('Enter'); await waitText('20 km/h');
    assert.equal(await card.getAttribute('role'),'dialog');
    await page.getByRole('button',{name:'Đóng thông tin đoạn đường'}).click();
    await card.waitFor({state:'hidden'});
  });
  for(const [value,expected] of [['stale','Dữ liệu gần nhất'],['closed','Đang bị chặn'],['zero','Chưa ước tính được'],['empty','Chưa có dữ liệu khớp'],['opposite','Chưa có dữ liệu khớp']]) {
    await check(value+' data state',async()=>{mode=value;await open();await hover();await waitText(expected);
      if(value==='closed'||value==='zero') assert.ok(!(await card.innerText()).includes('6 phút ·'));
    });
  }
  await check('error, retry and no stale success retained',async()=>{
    mode='error';await open();await hover();await waitText('Không tải được dữ liệu giao thông');
    const point=await spot();await page.mouse.click(point.x,point.y);
    mode='live';await page.getByRole('button',{name:'Thử tải lại',exact:true}).click();await waitText('20 km/h');
    assert.ok(!(await card.innerText()).includes('Không tải được dữ liệu giao thông'));
  });
  await check('late result for previous cell does not replace current section',async()=>{
    mode='live';delayFirst=true;await open();const before=flowRequests;
    await hover(1);await page.waitForFunction(()=>!!document.querySelector('.route-inspection-card'));
    const start=Date.now();
    while(flowRequests===before) { assert.ok(Date.now()-start<10000,'first hover starts a request'); await page.waitForTimeout(30); }
    await hover(2);await waitText('30 km/h');await page.waitForTimeout(900);
    assert.ok((await card.innerText()).includes('Trạm 2 → Trạm 3'));
    assert.ok(!(await card.innerText()).includes('20 km/h'));delayFirst=false;
  });
  await check('traffic off, route layer cleanup and camera interactions',async()=>{
    await page.keyboard.press('Escape');await page.locator('.gm-layers-summary').click();
    await page.getByRole('checkbox',{name:'Giao thông trực tiếp',exact:true}).uncheck();
    await page.locator('.gm-layers-summary').click();await page.waitForTimeout(200);
    await hover();await waitText('Bật lớp Giao thông');
    await page.keyboard.press('Escape');await page.locator('.gm-layers-summary').click();
    await page.getByRole('checkbox',{name:'Tuyến & điểm nháp',exact:true}).uncheck();
    assert.equal(await hit(1).count(),0);await card.waitFor({state:'hidden'});
    await page.getByRole('checkbox',{name:'Tuyến & điểm nháp',exact:true}).check();
    await page.getByRole('checkbox',{name:'Giao thông trực tiếp',exact:true}).check();
    await page.locator('.gm-layers-summary').click();
    await hit(1).waitFor({state:'attached'}); await page.waitForTimeout(450); await hover(); await waitText('20 km/h');
    await page.mouse.wheel(0,-150);await card.waitFor({state:'hidden'});
  });
  await check('touch pin and contained card at 320 px',async()=>{
    await page.setViewportSize({width:320,height:720});await open();
    // Tap away from station/incident markers, whose interaction intentionally takes precedence.
    const point=await spot(1,.65);
    assert.ok(await page.evaluate(point=>document.elementFromPoint(point.x,point.y)?.classList.contains('route-inspection-hit'),point),
      'collapsed layer control must not invisibly intercept map touches');
    await page.touchscreen.tap(point.x,point.y);
    await waitText('20 km/h');
    assert.equal(await card.getAttribute('data-pinned'),'true');
    const rect=await card.boundingBox();assert.ok(rect.x>=0&&rect.y>=0&&rect.x+rect.width<=320&&rect.y+rect.height<=720);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:output+'mobile-320-fixture.png'});
    await page.getByRole('button',{name:'Đóng thông tin đoạn đường'}).click();await card.waitFor({state:'hidden'});
  });
  assert.deepEqual(errors,[]);
  await writeFile(output+'results.json',JSON.stringify({timestamp:new Date().toISOString(),mode:'browser-fixture',checks,pageErrors:errors,flowRequests},null,2));
} catch(error) { await page.screenshot({path:output+'failure.png'}); throw error; }
finally { await browser.close(); }
