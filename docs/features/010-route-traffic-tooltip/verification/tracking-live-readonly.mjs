// Current application, GET-only verification. Does not start/stop vehicles or change data.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output=fileURLToPath(new URL('../artifacts/vehicle-tracking-live/',import.meta.url));
await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});
page.setDefaultTimeout(15000);
const errors=[],blockedWrites=[],etaResponses=[];let flowRequests=0;
page.on('pageerror',error=>errors.push(error.message));
await page.route('**/api/**',route=>{
  const path=new URL(route.request().url()).pathname;
  if(path.endsWith('/traffic/flow'))flowRequests++;
  if(route.request().method()!=='GET'){blockedWrites.push(path);return route.abort();}
  return route.continue();
});
page.on('response',async response=>{
  if(!new URL(response.url()).pathname.endsWith('/eta'))return;
  try{const data=await response.json();etaResponses.push({httpStatus:response.status(),source:data.source,status:data.status,nextStopSequence:data.nextStopSequence});}catch{/* Response aborted during cleanup. */}
});
try{
  await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
  await page.getByLabel('Chọn chuyến mô phỏng').locator('option:not([value=""])').first().waitFor({state:'attached'});
  const tripId=await page.getByLabel('Chọn chuyến mô phỏng').locator('option:not([value=""])').first().getAttribute('value');
  await page.getByLabel('Chọn chuyến mô phỏng').selectOption(tripId);
  const panel=page.getByRole('region',{name:'Vị trí, vận tốc và ETA mô phỏng',exact:true});
  await panel.waitFor();
  await page.waitForFunction(()=>!document.querySelector('.trip-traffic-source')?.textContent.includes('Đang tính'));
  await page.getByRole('button',{name:'Thu bảng dữ liệu',exact:true}).click();
  await page.locator('.simulator-content').evaluate(element=>element.parentElement.scrollTop=0);
  assert.equal(await page.locator('[data-traffic-road]').count(),0);
  assert.equal(flowRequests,0);
  assert.deepEqual(errors,[]);assert.deepEqual(blockedWrites,[]);
  await page.screenshot({path:output+'simulator.png'});
  const result={timestamp:new Date().toISOString(),mode:'vehicle-tracking-live-readonly',vehicleMarkerCount:await page.locator('.live-vehicle-marker').count(),
    roadHitCount:0,flowRequests,speedVisible:await page.getByTestId('simulation-speed').isVisible(),
    etaVisible:await page.getByTestId('simulation-eta').isVisible(),sourceLabel:await panel.locator('.trip-traffic-source').innerText(),
    etaResponses,pageErrors:errors,blockedWrites};
  await writeFile(output+'results.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}catch(error){await page.screenshot({path:output+'failure.png'});throw error;}
finally{await browser.close();}
