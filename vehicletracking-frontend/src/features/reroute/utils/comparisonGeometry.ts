import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import { validLine, type Coordinate } from '@/features/routes/utils/routeInspection';

const METERS = 111195;
const RAD = Math.PI / 180;
const TOLERANCE = 8;
const SAMPLE_LENGTH = 20;
const CELL_SIZE = 40;
const DIRECTION_COSINE = Math.cos(30 * RAD);
const geometryError = () => new Error('Không đọc được dữ liệu đường đi của lần thay đổi này.');
const longitudeDelta = (value: number) => ((value + 540) % 360) - 180;

type Point = { x: number; y: number };
type Segment = { a: Coordinate; b: Coordinate; from: Point; to: Point; dx: number; dy: number };
export type ComparedPaths = {
  beforeChanged: Coordinate[][];
  afterChanged: Coordinate[][];
  common: Coordinate[][];
};

export function decodeComparisonPaths(encodedPolylines: string[]): Coordinate[][] {
  if (!encodedPolylines.length) throw geometryError();
  return encodedPolylines.map(encoded => {
    const line = decodeFlexiblePolyline(encoded);
    if (!validLine(line)) throw geometryError();
    return line;
  });
}

function projection(origin: Coordinate) {
  const longitudeScale = Math.max(0.00001, Math.cos(origin[0] * RAD));
  return (point: Coordinate): Point => ({
    x: longitudeDelta(point[1] - origin[1]) * METERS * longitudeScale,
    y: (point[0] - origin[0]) * METERS,
  });
}

function segments(paths: Coordinate[][], project: (point: Coordinate) => Point): Segment[][] {
  return paths.map(line => {
    if (!validLine(line)) throw geometryError();
    const result: Segment[] = [];
    for (let i = 1; i < line.length; i++) {
      const first = line[i - 1], last = line[i];
      const from = project(first), to = project(last);
      const dx = to.x - from.x, dy = to.y - from.y;
      const length = Math.hypot(dx, dy);
      if (length < 0.01) continue;
      const count = Math.ceil(length / SAMPLE_LENGTH);
      const interpolate = (t: number): Coordinate => [
        first[0] + (last[0] - first[0]) * t,
        longitudeDelta(first[1] + longitudeDelta(last[1] - first[1]) * t),
      ];
      for (let j = 0; j < count; j++) {
        const start = j / count, end = (j + 1) / count;
        result.push({ a: interpolate(start), b: interpolate(end),
          from: { x: from.x + dx * start, y: from.y + dy * start },
          to: { x: from.x + dx * end, y: from.y + dy * end },
          dx: dx / length, dy: dy / length });
      }
    }
    return result;
  });
}

function indexSegments(paths: Segment[][]) {
  const cells = new Map<string, Segment[]>();
  for (const line of paths) {
    for (const segment of line) {
      const minX = Math.floor((Math.min(segment.from.x, segment.to.x) - TOLERANCE) / CELL_SIZE);
      const maxX = Math.floor((Math.max(segment.from.x, segment.to.x) + TOLERANCE) / CELL_SIZE);
      const minY = Math.floor((Math.min(segment.from.y, segment.to.y) - TOLERANCE) / CELL_SIZE);
      const maxY = Math.floor((Math.max(segment.from.y, segment.to.y) + TOLERANCE) / CELL_SIZE);
      for (let x = minX; x <= maxX; x++) {
        for (let y = minY; y <= maxY; y++) {
          const key = `${x}:${y}`;
          const items = cells.get(key);
          if (items) items.push(segment);
          else cells.set(key, [segment]);
        }
      }
    }
  }
  return cells;
}

function isCommon(segment: Segment, cells: Map<string, Segment[]>) {
  // Check the ends as well as the middle; a crossing alone must not hide a changed road.
  return [0, 0.5, 1].every(t => {
    const x = segment.from.x + (segment.to.x - segment.from.x) * t;
    const y = segment.from.y + (segment.to.y - segment.from.y) * t;
    const nearby = cells.get(`${Math.floor(x / CELL_SIZE)}:${Math.floor(y / CELL_SIZE)}`) ?? [];
    return nearby.some(other => {
      if (segment.dx * other.dx + segment.dy * other.dy < DIRECTION_COSINE) return false;
      const dx = other.to.x - other.from.x, dy = other.to.y - other.from.y;
      const along = Math.max(0, Math.min(1,
        ((x - other.from.x) * dx + (y - other.from.y) * dy) / (dx * dx + dy * dy)));
      return Math.hypot(x - other.from.x - along * dx, y - other.from.y - along * dy) <= TOLERANCE;
    });
  });
}

function split(paths: Segment[][], other: Map<string, Segment[]>) {
  const changed: Coordinate[][] = [], common: Coordinate[][] = [];
  for (const line of paths) {
    let selected: boolean | null = null;
    let points: Coordinate[] = [];
    const flush = () => {
      if (points.length >= 2) (selected ? common : changed).push(points);
    };
    for (const segment of line) {
      const shared = isCommon(segment, other);
      if (selected === shared) points.push(segment.b);
      else {
        flush();
        selected = shared;
        points = [segment.a, segment.b];
      }
    }
    // Never join separate sections: a missing leg must remain a visible gap.
    flush();
  }
  return { changed, common };
}

export function comparePaths(before: Coordinate[][], after: Coordinate[][]): ComparedPaths {
  const origin = before[0]?.[0] ?? after[0]?.[0];
  if (!origin) return { beforeChanged: [], afterChanged: [], common: [] };
  const project = projection(origin);
  const previous = segments(before, project), next = segments(after, project);
  const beforeParts = split(previous, indexSegments(next));
  const afterParts = split(next, indexSegments(previous));
  // Draw the shared road once, using the after geometry rather than stacked copies.
  return { beforeChanged: beforeParts.changed, afterChanged: afterParts.changed, common: afterParts.common };
}

export function comparisonBounds(
  paths: Coordinate[][],
  anchor?: { latitude: number; longitude: number } | null,
): [[number, number], [number, number]] | null {
  let south = Infinity, west = Infinity, north = -Infinity, east = -Infinity;
  const include = (point: Coordinate) => {
    if (!Number.isFinite(point[0]) || !Number.isFinite(point[1])
      || Math.abs(point[0]) > 90 || Math.abs(point[1]) > 180) throw geometryError();
    south = Math.min(south, point[0]); north = Math.max(north, point[0]);
    west = Math.min(west, point[1]); east = Math.max(east, point[1]);
  };
  for (const line of paths) for (const point of line) include(point);
  if (anchor) include([anchor.latitude, anchor.longitude]);
  return Number.isFinite(south) ? [[south, west], [north, east]] : null;
}
