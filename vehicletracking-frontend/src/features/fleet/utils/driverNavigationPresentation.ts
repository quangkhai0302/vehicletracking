import type { DriverNavigationSnapshot } from '@/features/fleet/types/driverNavigation';
import type { RouteStopRole } from '@/features/routes/types/route';
import type { RouteStopProgress } from '@/features/map/utils/routeStopPresentation';

// Same sources/precedence as the admin simulator: frame for movement, visits for check-in.
export function driverNavigationPresentation(snapshot: DriverNavigationSnapshot) {
  const stops = [...snapshot.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber);
  const run = snapshot.simulation,
    frame = run?.frame;
  const attempt = run?.attemptNumber ?? snapshot.trip.attemptNumber;
  const visits = new Map(
    (snapshot.checkIns?.visits ?? [])
      .filter(
        (v) =>
          attempt === undefined || v.attemptNumber === undefined || v.attemptNumber === attempt,
      )
      .map((v) => [v.stopSequence, v]),
  );
  const sequence = frame?.nextStopSequence ?? snapshot.checkIns?.nextStopSequence;
  const nextStop =
    stops.find((s) => s.sequenceNumber === sequence) ??
    (frame?.finished || snapshot.trip.status === 'COMPLETED'
      ? null
      : (stops.find((s) => !visits.has(s.sequenceNumber)) ?? null));
  const role = (index: number): RouteStopRole =>
    index === 0 ? 'START' : index === stops.length - 1 ? 'END' : 'STOP';
  const state = (sequenceNumber: number): RouteStopProgress =>
    visits.has(sequenceNumber)
      ? 'checked-in'
      : nextStop?.sequenceNumber === sequenceNumber
        ? 'next'
        : 'pending';
  const speedKmh = frame?.speedKmh ?? snapshot.position?.speedKmh;
  return {
    stops,
    visits,
    nextStop,
    role,
    state,
    speedKmh: speedKmh === undefined ? null : Math.max(0, Math.round(speedKmh)),
    progressPercent: Math.min(
      100,
      Math.max(0, frame?.progressPercent ?? (snapshot.trip.status === 'COMPLETED' ? 100 : 0)),
    ),
    nextStopEtaSeconds: frame?.nextStopEtaSeconds ?? null,
    remainingSeconds: run ? Math.max(0, run.durationSeconds - run.elapsedSeconds) : null,
  };
}
export function formatSimulationDuration(seconds: number | null | undefined) {
  if (seconds == null || !Number.isFinite(seconds)) return '—';
  const value = Math.max(0, Math.ceil(seconds));
  if (value < 60) return `${value} giây`;
  const minutes = Math.floor(value / 60),
    remaining = value % 60;
  return remaining ? `${minutes}p ${remaining}s` : `${minutes} phút`;
}
