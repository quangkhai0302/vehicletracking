import { test } from 'node:test';
import assert from 'node:assert/strict';
import { simulationFleetTrips,waitingSimulationTrips } from '../../../../vehicletracking-frontend/src/utils/simulationFleet.ts';
const trip=(id,vehicleId,status='SCHEDULED',hour=10)=>({id,vehicleId,status,scheduledDepartureAt:`2026-09-14T${hour}:00:00Z`});
const snapshot=(trips,simulations=[],positions=[])=>({trips,simulations,positions});
test('distinct vehicles wait together; earliest waiting trip wins per vehicle',()=>{
  const data=snapshot([trip(1,1,'SCHEDULED',12),trip(2,2),trip(3,1)]);
  assert.deepEqual(simulationFleetTrips(data).map(t=>t.id),[2,3]);
});
test('active simulator wins over every waiting trip of that vehicle',()=>{
  const data=snapshot([trip(1,1),trip(2,1,'IN_PROGRESS'),trip(3,2)],[{tripId:2,status:'RUNNING'}]);
  assert.deepEqual(simulationFleetTrips(data).map(t=>t.id),[2,3]);
});
test('a GPS or unsimulated active trip blocks waiting previews for its vehicle',()=>{
  const data=snapshot([trip(1,1),trip(2,1,'IN_PROGRESS'),trip(3,2)],[],[{tripId:2,source:'GPS'}]);
  assert.deepEqual(simulationFleetTrips(data).map(t=>t.id),[3]);
});
test('historical completed trip does not hide a new waiting assignment',()=>{
  const data=snapshot([trip(1,1,'COMPLETED'),trip(2,1)],[],[{tripId:1,source:'SIMULATOR'}]);
  assert.deepEqual(waitingSimulationTrips(data,simulationFleetTrips(data)).map(t=>t.id),[2]);
});
test('paused run at start without emitted telemetry is a waiting preview',()=>{
  const data=snapshot([trip(1,1)],[{tripId:1,status:'PAUSED',elapsedSeconds:0}]);
  assert.equal(waitingSimulationTrips(data,simulationFleetTrips(data)).length,1);
});
test('existing position or elapsed motion must not be relocated to the start',()=>{
  const data=snapshot([trip(1,1),trip(2,2)],[{tripId:2,status:'PAUSED',elapsedSeconds:100}],[{tripId:1,source:'SIMULATOR'}]);
  assert.deepEqual(waitingSimulationTrips(data,simulationFleetTrips(data)),[]);
});
test('completed/cancelled trips and no snapshot produce no new waiting vehicles',()=>{
  assert.deepEqual(simulationFleetTrips(snapshot([trip(1,1,'COMPLETED'),trip(2,2,'CANCELLED')])),[]);
  assert.deepEqual(simulationFleetTrips(null),[]);
});
