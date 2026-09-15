/** Presentation-only buffer. Server telemetry remains the source of truth. */
export interface MotionSample {
  time: number;
  latitude: number;
  longitude: number;
  heading: number;
  progress?: number;
}
export interface MotionPath {
  points: readonly (readonly [number, number])[];
  distances: number[];
  length: number;
}
export const PLAYBACK_DELAY_MS = 1500;
export function makeMotionPath(segments: readonly (readonly (readonly [number, number])[])[]): MotionPath {
  const points = segments.flat();
  const boundaries = new Set<number>();
  let count = 0;
  for (const segment of segments) { boundaries.add(count); count += segment.length; }
  const distances = [0];
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i];
    const rad = Math.PI / 180;
    const h = Math.sin((b[0] - a[0]) * rad / 2) ** 2
      + Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin((b[1] - a[1]) * rad / 2) ** 2;
    distances.push(distances[i - 1] + (boundaries.has(i) ? 0 : 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)))));
  }
  return { points, distances, length: distances[distances.length - 1] ?? 0 };
}
export function pointOnMotionPath(path: MotionPath, progress: number): [number, number] | null {
  if (!path.points.length || path.length <= 0) return null;
  const target = Math.max(0, Math.min(100, progress)) / 100 * path.length;
  let lo = 1, hi = path.points.length - 1;
  while (lo < hi) { const mid = (lo + hi) >>> 1; if (path.distances[mid] < target) lo = mid + 1; else hi = mid; }
  const a = path.points[lo - 1], b = path.points[lo];
  const length = path.distances[lo] - path.distances[lo - 1];
  const ratio = length > 0 ? (target - path.distances[lo - 1]) / length : 0;
  return [a[0] + (b[0] - a[0]) * ratio, a[1] + (b[1] - a[1]) * ratio];
}
export function sampleMotion(samples: readonly MotionSample[], time: number, path?: MotionPath): MotionSample | null {
  if (!samples.length) return null;
  if (time <= samples[0].time) return samples[0];
  for (let i = 1; i < samples.length; i++) {
    const a = samples[i - 1], b = samples[i];
    if (time > b.time) continue;
    const t = Math.max(0, Math.min(1, (time - a.time) / Math.max(1, b.time - a.time)));
    const position = path && a.progress !== undefined && b.progress !== undefined && b.progress >= a.progress
      ? pointOnMotionPath(path, a.progress + (b.progress - a.progress) * t) : null;
    const headingDelta = ((b.heading - a.heading + 540) % 360) - 180;
    return { time, latitude: position?.[0] ?? a.latitude + (b.latitude - a.latitude) * t,
      longitude: position?.[1] ?? a.longitude + (b.longitude - a.longitude) * t,
      heading: (a.heading + headingDelta * t + 360) % 360 };
  }
  // A disconnected stream must never make a vehicle keep driving indefinitely.
  return samples[samples.length - 1];
}
