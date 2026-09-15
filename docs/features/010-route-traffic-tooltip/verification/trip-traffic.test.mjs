import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tripTrafficView } from '../../../../vehicletracking-frontend/src/utils/tripTraffic.ts';
const time='2026-09-14T10:00:00Z',later='2026-09-14T10:00:02Z';
const eta={tripId:1,calculatedAt:time,source:'HERE_LIVE',status:'AVAILABLE',nextStopSequence:2,
  trafficFetchedAt:time,trafficObservedAt:time,baselineRemainingSeconds:500,totalRemainingSeconds:800,warning:null,
  stops:[{sequenceNumber:2,stationName:'Trạm B',state:'NEXT',etaSeconds:600,etaAt:later}],affectedSegments:[]};
const run={status:'RUNNING',updatedAt:time,frame:{nextStopSequence:2,nextStopEtaSeconds:100,finished:false},
  traffic:{source:'HERE_LIVE',status:'AVAILABLE',nextStopEtaSeconds:400,blocked:false,fetchedAt:time}};
test('ETA and source come from the same selected response',()=>{
  assert.equal(tripTrafficView(eta,run).countdown,600);
  assert.equal(tripTrafficView(eta,{...run,updatedAt:later}).countdown,400);
});
test('blocked ETA takes priority over old positive simulator metadata',()=>{
  const view=tripTrafficView({...eta,status:'BLOCKED'},run);
  assert.equal(view.blocked,true);assert.equal(view.countdown,null);
});
test('new simulator closure supersedes an older finite ETA',()=>{
  const view=tripTrafficView(eta,{...run,updatedAt:later,traffic:{...run.traffic,status:'BLOCKED',blocked:true}});
  assert.equal(view.blocked,true);assert.equal(view.countdown,null);
});
test('new ETA recovery supersedes an old simulator closure',()=>{
  const view=tripTrafficView({...eta,calculatedAt:later},{...run,traffic:{...run.traffic,status:'BLOCKED',blocked:true}});
  assert.equal(view.blocked,false);assert.equal(view.countdown,600);
});
test('unavailable simulator metadata cannot replace a known blocked ETA',()=>{
  const view=tripTrafficView({...eta,status:'BLOCKED'},{...run,updatedAt:later,
    traffic:{...run.traffic,source:'UNAVAILABLE',status:'UNAVAILABLE',nextStopEtaSeconds:null}});
  assert.equal(view.blocked,true);assert.equal(view.countdown,null);
});
test('frame fallback is explicitly route snapshot, never HERE live',()=>{
  const view=tripTrafficView(null,{...run,traffic:{...run.traffic,nextStopEtaSeconds:null}});
  assert.equal(view.countdown,100);assert.equal(view.source,'ROUTE_SNAPSHOT');
});
test('completed stops do not fall back to an old frame countdown',()=>{
  const view=tripTrafficView({...eta,nextStopSequence:null},run);
  assert.equal(view.finished,true);assert.equal(view.nextStopSequence,null);assert.equal(view.countdown,null);
});
test('impacts are deduplicated and exclude already passed stops',()=>{
  const flow=(dest,jam)=>({kind:'FLOW',destinationStopSequence:dest,jamFactor:jam,traversability:'open'});
  const incident=type=>({kind:'INCIDENT',destinationStopSequence:2,traversability:type});
  const view=tripTrafficView({...eta,affectedSegments:[flow(1,6),flow(2,9),flow(2,9),incident('accident'),incident('construction')]},run);
  assert.deepEqual(view.impacts,['Ùn tắc nghiêm trọng','Tai nạn','Công trường / thi công']);
  assert.equal(view.delay,300);
});
test('missing ETA has no invented countdown or incident',()=>{
  const view=tripTrafficView(null,null);
  assert.equal(view.countdown,null);assert.equal(view.source,'UNAVAILABLE');assert.deepEqual(view.impacts,[]);
});
