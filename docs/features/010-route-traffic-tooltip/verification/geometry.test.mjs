import test from 'node:test';
import assert from 'node:assert/strict';
import { matchFlow, project, segmentMetrics, nearbyIncidents, usableTraffic, trafficAge, trafficCell } from '../../../../vehicletracking-frontend/src/utils/routeInspection.ts';

const route = [[10, 106], [10, 106.01]], point = [10, 106.005];
const flow = (changes = {}) => ({ id: 'forward', description: 'Fixture', points: route, lengthMeters: 2000,
  speedKmh: 20, freeFlowKmh: 40, jamFactor: 6, traversability: 'open', confidence: .9, ...changes });
test('matches the local route direction and preserves provider metrics', () => {
  assert.equal(matchFlow(point, route, [flow()]).id, 'forward');
  assert.deepEqual(segmentMetrics(flow()), { length: 2000, blocked: false, travel: 360, delay: 180 });
});
test('does not match opposite carriageway, perpendicular junction or distant road', () => {
  assert.equal(matchFlow(point, route, [flow({ points: [...route].reverse() })]), null);
  assert.equal(matchFlow(point, route, [flow({ points: [[9.99, 106.005], [10.01, 106.005]] })]), null);
  assert.equal(matchFlow(point, route, [flow({ points: [[10.001, 106], [10.001, 106.01]] })]), null);
});
test('uses tangent of curved section instead of whole-section bearing', () => {
  const curve = [[9.99, 106], [10, 106], [10, 106.01]];
  assert.equal(matchFlow(point, curve, [flow()]).id, 'forward');
});
test('ambiguous nearby parallel matches produce no data', () => {
  assert.equal(matchFlow(point, route, [flow(), flow({ id: 'parallel', points: [[10.00001, 106], [10.00001, 106.01]] })]), null);
});
test('invalid geometry and unreliable or invalid speed do not become current traffic', () => {
  for (const item of [flow({ points: [] }), flow({ points: [[10, NaN], [10, 106.01]] }), flow({ confidence: 0 }), flow({ speedKmh: NaN }), flow({ speedKmh: -2 })]) {
    assert.equal(matchFlow(point, route, [item]), null);
  }
  assert.equal(project(point, [[10,106],[10,106]]), null);
});
test('closure and stopped traffic do not yield finite traversal estimates', () => {
  assert.equal(segmentMetrics(flow({ speedKmh: 0 })).travel, null);
  assert.equal(segmentMetrics(flow({ traversability: 'closed' })).travel, null);
  assert.equal(segmentMetrics(flow({ traversability: 'reversibleNotRoutable' })).blocked, true);
  assert.equal(segmentMetrics(flow({ freeFlowKmh: 0 })).delay, null);
});
test('supports old flow responses without length; never reports negative delay', () => {
  const metrics = segmentMetrics(flow({ lengthMeters: undefined, speedKmh: 60 }));
  assert.ok(metrics.length > 1000 && metrics.length < 1200);
  assert.equal(metrics.delay, 0);
});
test('only nearby and currently effective incidents are shown', () => {
  const now = Date.parse('2026-09-14T06:00:00Z');
  const incident = { id: 'active', description: 'Fixture', type: 'accident', criticality: 'major', points: [point], center: point, status: 'ACTIVE' };
  const results = nearbyIncidents(point, [incident, { ...incident, id: 'expired', endTime: '2026-09-14T05:59:00Z' },
    { ...incident, id: 'future', startTime: '2026-09-14T07:00:00Z' }, { ...incident, id: 'far', points: [[10.01,106.005]] },
    { ...incident, id: 'inactive', status: 'EXPIRED' }], now);
  assert.deepEqual(results.map(item => item.id), ['active']);
});
test('freshness grows from server age without assuming synchronized client clock', () => {
  const envelope = { source: 'HERE_LAST_KNOWN', status: 'STALE', ageSeconds: 80 };
  assert.equal(trafficAge(envelope, 1000, 31000), 110);
  assert.equal(usableTraffic(envelope), true);
  assert.equal(usableTraffic({ ...envelope, status: 'UNAVAILABLE' }), false);
  assert.equal(usableTraffic({ ...envelope, source: 'ROUTE_SNAPSHOT' }), false);
});
test('mouse motion inside a cell reuses bounds; leaving it changes bounds', () => {
  assert.equal(trafficCell([10.001,106.001]), trafficCell([10.002,106.002]));
  assert.notEqual(trafficCell([10.001,106.001]), trafficCell([10.02,106.02]));
  assert.equal(trafficCell(null), null);
});
