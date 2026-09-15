// GET-only smoke check of the user's running app; no trip/vehicle mutations.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const output=fileURLToPath(new URL('../artifacts/live-readonly/',import.meta.url));await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true}),page=await browser.newPage({viewport:{width:1440,height:900}});
page.setDefaultTimeout(15000);const errors=[],blockedWrites=[];
page.on('pageerror',error=>errors.push(error.message));
await page.route('**/api/**',route=>{if(route.request().method()!=='GET'){blockedWrites.push(new URL(route.request().url()).pathname);return route.abort();}return route.continue();});
try{
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});await page.locator('.simulation-connection[data-state=live]').waitFor();
  await page.getByRole('navigation',{name:'Chế độ vận hành'}).getByRole('button',{name:'Mô phỏng',exact:true}).click();
  await page.getByRole('region',{name:'Đội xe mô phỏng'}).waitFor();
  await page.waitForFunction(()=>!document.querySelector('.simulation-fleet-list')?.textContent.includes('Đang tải lộ trình đội xe'));
  const fleetRows=await page.locator('[data-simulation-trip]').count();
  await page.waitForFunction(n=>document.querySelectorAll('[data-simulation-route-trip]').length===n,fleetRows);
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  if(fleetRows)await page.getByRole('button',{name:'Xem tất cả',exact:true}).click();
  assert.deepEqual(errors,[]);assert.deepEqual(blockedWrites,[]);
  const result={timestamp:new Date().toISOString(),mode:'live-readonly',fleetRows:await page.locator('[data-simulation-trip]').count(),
    waitingGroups:await page.locator('.simulation-waiting-marker').count(),liveMarkers:await page.locator('.live-vehicle-marker').count(),
    summary:await page.locator('.simulation-fleet-heading').innerText(),pageErrors:errors,blockedWrites};
  result.routeTrips=await page.locator('[data-simulation-route-trip]').evaluateAll(elements=>elements.map(e=>({tripId:e.dataset.simulationRouteTrip,color:e.getAttribute('stroke'),geometry:e.getAttribute('d')})));
  await page.screenshot({path:output+'fleet.png'});await writeFile(output+'results.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}finally{await browser.close();}
