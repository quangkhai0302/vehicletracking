import type { TrafficFlowSegment, TrafficIncident } from '../types/map';
import type { TrafficEnvelope } from '../types/traffic';

export type Coordinate = [number, number];
const RAD = Math.PI / 180;
const METERS = 111195;
const longitudeDelta = (value: number) => ((value + 540) % 360) - 180;
const validPoint = (point: number[]) => point.length >= 2 && Number.isFinite(point[0]) && Number.isFinite(point[1])
  && Math.abs(point[0]) <= 90 && Math.abs(point[1]) <= 180;
export const validLine = (points: Coordinate[]) => points.length >= 2 && points.every(validPoint);

// Local projection uses the route tangent at the pointer, including curved sections.
export function project(point: Coordinate, points: Coordinate[]) {
  if (!validPoint(point) || !validLine(points)) return null;
  const scale = Math.max(0.00001, Math.cos(point[0] * RAD));
  let best: { point: Coordinate; distance: number; dx: number; dy: number } | null = null;
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i];
    const ax = longitudeDelta(a[1] - point[1]) * METERS * scale, ay = (a[0] - point[0]) * METERS;
    const dx = longitudeDelta(b[1] - a[1]) * METERS * scale, dy = (b[0] - a[0]) * METERS;
    const length = Math.hypot(dx, dy);
    if (length < 0.01) continue;
    const t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (length * length)));
    const distance = Math.hypot(ax + t * dx, ay + t * dy);
    if (!best || distance < best.distance) best = { distance, dx: dx / length, dy: dy / length,
      point: [a[0] + t * (b[0] - a[0]), longitudeDelta(a[1] + t * longitudeDelta(b[1] - a[1]))] };
  }
  return best;
}

export function matchFlow(point: Coordinate, route: Coordinate[], flows: TrafficFlowSegment[]) {
  const anchor = project(point, route);
  if (!anchor) return null;
  const candidates = flows.flatMap(flow => {
    if (!Number.isFinite(flow.speedKmh) || flow.speedKmh < 0 || flow.speedKmh > 500
      || (flow.confidence != null && (!Number.isFinite(flow.confidence) || flow.confidence <= 0))) return [];
    const match = project(anchor.point, flow.points);
    // Deliberately conservative: opposite carriageways and intersecting roads are not interchangeable.
    if (!match || match.distance > 18 || match.dx * anchor.dx + match.dy * anchor.dy < Math.cos(30 * RAD)) return [];
    return [{ flow, distance: match.distance }];
  }).sort((a, b) => a.distance - b.distance);
  if (!candidates.length) return null;
  if (candidates[1] && candidates[1].distance - candidates[0].distance < 3
    && candidates[1].flow.id !== candidates[0].flow.id) return null;
  return candidates[0].flow;
}

export function segmentMetrics(flow: TrafficFlowSegment) {
  const geometryLength = validLine(flow.points) ? flow.points.slice(1).reduce((sum, point, i) => {
    const a = flow.points[i];
    return sum + Math.hypot((point[0] - a[0]) * METERS,
      longitudeDelta(point[1] - a[1]) * METERS * Math.cos((point[0] + a[0]) / 2 * RAD));
  }, 0) : 0;
  const length = Number.isFinite(flow.lengthMeters) && (flow.lengthMeters ?? 0) > 0 ? flow.lengthMeters! : geometryLength;
  const blocked = ['closed', 'reversiblenotroutable'].includes((flow.traversability ?? '').toLowerCase());
  const travel = !blocked && flow.speedKmh > 0 && length > 0 ? length / (flow.speedKmh / 3.6) : null;
  const base = flow.freeFlowKmh > 0 && Number.isFinite(flow.freeFlowKmh) && length > 0 ? length / (flow.freeFlowKmh / 3.6) : null;
  return { length, blocked, travel, delay: travel !== null && base !== null ? Math.max(0, travel - base) : null };
}

export function nearbyIncidents(point: Coordinate, incidents: TrafficIncident[], now: number) {
  return incidents.filter(incident => {
    if (incident.status && incident.status !== 'ACTIVE') return false;
    if (incident.startTime && Date.parse(incident.startTime) > now) return false;
    if (incident.endTime && Date.parse(incident.endTime) <= now) return false;
    const points = incident.points?.length ? incident.points : incident.center ? [incident.center] : [];
    if (!points.length || !points.every(validPoint)) return false;
    if (points.length > 1) return (project(point, points)?.distance ?? Infinity) <= 50;
    return Math.hypot((points[0][0] - point[0]) * METERS,
      longitudeDelta(points[0][1] - point[1]) * METERS * Math.cos(point[0] * RAD)) <= 50;
  });
}

export function usableTraffic<T>(envelope: TrafficEnvelope<T> | null) {
  return envelope !== null && ['HERE_LIVE', 'HERE_LAST_KNOWN'].includes(envelope.source)
    && ['AVAILABLE', 'STALE', 'BLOCKED'].includes(envelope.status);
}

export function trafficAge<T>(envelope: TrafficEnvelope<T>, receivedAt: number, now: number) {
  if (!Number.isFinite(envelope.ageSeconds) || envelope.ageSeconds < 0) return null;
  return Math.floor(envelope.ageSeconds + Math.max(0, now - receivedAt) / 1000);
}

export function trafficCell(point: Coordinate | null) {
  if (!point) return null;
  const lat = Math.round(point[0] * 100) / 100, lng = Math.round(point[1] * 100) / 100;
  return [Math.max(-180, lng - 0.01), Math.max(-90, lat - 0.01), Math.min(180, lng + 0.01), Math.min(90, lat + 0.01)]
    .map(value => value.toFixed(4)).join(',');
}
