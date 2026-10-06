import { expect, test } from 'vitest';
import { comparePaths, comparisonBounds, decodeComparisonPaths } from '@/features/reroute/utils/comparisonGeometry';
import type { Coordinate } from '@/features/routes/utils/routeInspection';
import { encode } from './fixtures/driverNavigation';

const origin: Coordinate = [10.77, 106.7];
const point = (east: number, north = 0): Coordinate => [origin[0] + north / 111195,
  origin[1] + east / (111195 * Math.cos(origin[0] * Math.PI / 180))];
const line = (...values: [number, number?][]): Coordinate[] => values.map(([east, north]) => point(east, north));
const length = (paths: Coordinate[][]) => paths.reduce((total, path) => total + path.slice(1).reduce((sum, p, i) => {
  const previous = path[i];
  return sum + Math.hypot((p[0] - previous[0]) * 111195,
    (p[1] - previous[1]) * 111195 * Math.cos(origin[0] * Math.PI / 180));
}, 0), 0);

test('compares a diversion and rejoin without marking the entire original long road as shared', () => {
  const before = [line([0], [100], [300], [400])];
  const after = [line([0], [100], [100, 100], [300, 100], [300], [400])];
  const compared = comparePaths(before, after);
  expect(length(compared.common)).toBeCloseTo(200, 4);
  expect(length(compared.beforeChanged)).toBeCloseTo(200, 4);
  expect(length(compared.afterChanged)).toBeCloseTo(400, 4);
  expect(compared.common).toHaveLength(2);
  expect(before[0]).toHaveLength(4);
  expect(after[0]).toHaveLength(6);
});

test('matches identical roads with different vertex segmentation and retains section gaps', () => {
  const before = [line([0], [100]), line([200], [300])];
  const after = [line([0], [40], [100]), line([200], [250], [300])];
  const compared = comparePaths(before, after);
  expect(compared.beforeChanged).toEqual([]);
  expect(compared.afterChanged).toEqual([]);
  expect(compared.common).toHaveLength(2);
  expect(length(compared.common)).toBeCloseTo(200, 4);
  expect(compared.common[0][compared.common[0].length - 1][1]).toBeCloseTo(point(100)[1], 10);
  expect(compared.common[1][0][1]).toBeCloseTo(point(200)[1], 10);
});

test('opposite directions and crossing roads are changed, not shared', () => {
  const before = [line([0], [400])];
  const reversed = comparePaths(before, [line([400], [0])]);
  expect(reversed.common).toEqual([]);
  expect(length(reversed.beforeChanged)).toBeCloseTo(400, 4);
  expect(length(reversed.afterChanged)).toBeCloseTo(400, 4);
  const crossing = comparePaths(before, [line([200, -200], [200, 200])]);
  expect(crossing.common).toEqual([]);
});

test('uses the eight meter tolerance conservatively and leaves missing legs visible', () => {
  const before = [line([0], [400])];
  expect(comparePaths(before, [line([0, 7], [400, 7])]).afterChanged).toEqual([]);
  expect(comparePaths(before, [line([0, 9], [400, 9])]).common).toEqual([]);
  const gap = comparePaths([line([0], [100]), line([200], [300])], [line([0], [300])]);
  expect(length(gap.afterChanged)).toBeGreaterThanOrEqual(80);
  expect(length(gap.common)).toBeLessThanOrEqual(220);
});

test('decode keeps each section separate and rejects missing or invalid data with a neutral message', () => {
  const paths = [line([0], [100]), line([200], [300])];
  expect(decodeComparisonPaths(paths.map(encode))).toHaveLength(2);
  for (const encoded of [[], ['invalid!'], ['BF'], [encode([[10.77, 106.7]])], [encode([[91, 0], [92, 0]])]]) {
    expect(() => decodeComparisonPaths(encoded)).toThrow('Không đọc được dữ liệu đường đi');
  }
  expect(() => comparePaths([[[NaN, 0], [10, 10]]], [])).toThrow('Không đọc được dữ liệu đường đi');
});

test('bounds include the change anchor and all sections; empty geometry remains explicit', () => {
  expect(comparisonBounds([])).toBeNull();
  expect(comparisonBounds([[[10, 105], [11, 106]], [[12, 107], [13, 108]]],
    { latitude: 9, longitude: 104 })).toEqual([[9, 104], [13, 108]]);
  expect(comparisonBounds([], { latitude: 10, longitude: 106 })).toEqual([[10, 106], [10, 106]]);
  expect(() => comparisonBounds([], { latitude: Infinity, longitude: 106 })).toThrow('Không đọc được dữ liệu đường đi');
});

test('a long road uses local segment lookup while still detecting a short diversion', () => {
  const before = [line([0], [50000])];
  const after = [line([0], [24000], [24000, 100], [26000, 100], [26000], [50000])];
  const compared = comparePaths(before, after);
  expect(length(compared.beforeChanged)).toBeCloseTo(2000, 4);
  expect(length(compared.afterChanged)).toBeCloseTo(2200, 4);
  expect(length(compared.common)).toBeCloseTo(48000, 4);
});
