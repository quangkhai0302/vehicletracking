import type { TripSummary } from '../types/fleet';
import type { OperationsSnapshot } from '../types/operations';

// Google Maps Palette: Selected route is primary Google Blue #4285f4.
// Other vehicle routes use Google transit route colors.
export function simulationRouteColor(vehicleId: number, selected = false) {
  return selected ? '#4285f4' : ['#1a73e8', '#0f9d58', '#f9ab00', '#8e24aa'][Math.abs(vehicleId) % 4];
}

// One visible assignment per vehicle: an active trip blocks all waiting trips,
// including when it is receiving GPS and therefore cannot be simulated.
export function simulationFleetTrips(snapshot: OperationsSnapshot | null): TripSummary[] {
  if (!snapshot) return [];
  const active = new Map(snapshot.trips.filter(trip => trip.status === 'IN_PROGRESS').map(trip => [trip.vehicleId, trip]));
  const runs = new Map(snapshot.simulations.map(run => [run.tripId, run]));
  const gpsTrips = new Set(snapshot.positions.filter(point => point.source === 'GPS').map(point => point.tripId));
  const candidates = [...snapshot.trips].sort((a,b) => Date.parse(a.scheduledDepartureAt) - Date.parse(b.scheduledDepartureAt) || a.id - b.id);
  const byVehicle = new Map<number, TripSummary>();
  for (const trip of candidates) {
    if (gpsTrips.has(trip.id)) continue;
    if (active.has(trip.vehicleId)) {
      if (trip.status === 'IN_PROGRESS' && runs.has(trip.id)) byVehicle.set(trip.vehicleId, trip);
    } else if (trip.status === 'SCHEDULED' && !byVehicle.has(trip.vehicleId)) byVehicle.set(trip.vehicleId, trip);
  }
  return [...byVehicle.values()];
}

export function waitingSimulationTrips(snapshot: OperationsSnapshot | null, trips: TripSummary[]) {
  return trips.filter(trip => trip.status === 'SCHEDULED'
    && !snapshot?.positions.some(point => point.tripId === trip.id)
    && !snapshot?.simulations.some(run => run.tripId === trip.id && (run.elapsedSeconds > 0 || run.status === 'RUNNING')));
}
