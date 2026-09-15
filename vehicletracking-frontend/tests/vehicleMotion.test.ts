import { test } from 'node:test';
import assert from 'node:assert/strict';
import { makeMotionPath, pointOnMotionPath, sampleMotion, PLAYBACK_DELAY_MS } from '../src/utils/vehicleMotion.ts';

test('buffer crosses snapshot boundaries continuously instead of stopping after 900ms', () => {
  const samples = [0, 1000, 2000, 3000].map(time => ({time, latitude: time / 1000, longitude: 0, heading: 0}));
  for (let wall = 2000; wall <= 4000; wall += 16) {
    const expected = (wall - PLAYBACK_DELAY_MS) / 1000;
    assert.ok(Math.abs(sampleMotion(samples, wall - PLAYBACK_DELAY_MS)!.latitude - expected) < 1e-9);
  }
  assert.equal(sampleMotion(samples, 900)!.latitude, .9);
  assert.equal(sampleMotion(samples, 1000)!.latitude, 1);
  assert.equal(sampleMotion(samples, 1100)!.latitude, 1.1);
});

test('irregular samples interpolate by time and hold when disconnected', () => {
  const samples = [0, 1100, 1950].map(time => ({time, latitude: time / 1000, longitude: 1, heading: 0}));
  assert.equal(sampleMotion(samples, 1500)!.latitude, 1.5);
  assert.equal(sampleMotion(samples, 10000)!.latitude, 1.95);
  assert.equal(sampleMotion([], 1), null);
});

test('route interpolation follows a corner rather than drawing a diagonal at 5x/10x', () => {
  const path = makeMotionPath([[[0, 0], [0, .001], [.001, .001]]]);
  const samples = [
    {time: 0, latitude: 0, longitude: 0, heading: 90, progress: 0},
    {time: 1000, latitude: .001, longitude: .001, heading: 0, progress: 100},
  ];
  const corner = sampleMotion(samples, 500, path)!;
  assert.ok(Math.abs(corner.latitude) < 1e-9);
  assert.ok(Math.abs(corner.longitude - .001) < 1e-9);
  assert.deepEqual(pointOnMotionPath(path, 100), [.001, .001]);
});

test('heading takes the short turn through north and a repeated position stays still', () => {
  const a = {time: 0, latitude: 1, longitude: 2, heading: 350};
  const b = {...a, time: 1000, heading: 10};
  const middle = sampleMotion([a, b], 500)!;
  assert.equal(middle.heading, 0);
  assert.equal(middle.latitude, 1);
  assert.equal(middle.longitude, 2);
});
