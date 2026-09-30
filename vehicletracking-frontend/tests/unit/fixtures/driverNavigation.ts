import type { DriverNavigationSnapshot, DriverRouteOptions } from '@/features/fleet/types/driverNavigation';
import type { TripStatus } from '@/features/fleet/types/fleet';
export const stamp = '2026-09-29T02:00:00Z';
export function encode(points: [number, number][]) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  let result = 'BF', latitude = 0, longitude = 0;
  const append = (value: number) => {
    while (value >= 32) { result += alphabet[(value & 31) | 32]; value = Math.floor(value / 32); }
    result += alphabet[value];
  };
  for (const [lat, lng] of points) {
    const a = Math.round(lat * 100000), b = Math.round(lng * 100000);
    append(a - latitude >= 0 ? (a - latitude) * 2 : -(a - latitude) * 2 - 1);
    append(b - longitude >= 0 ? (b - longitude) * 2 : -(b - longitude) * 2 - 1);
    latitude = a; longitude = b;
  }
  return result;
}
export function driverSnapshot(status: TripStatus = 'SCHEDULED', revision: number | null = null, id = 7): DriverNavigationSnapshot {
  const section = { sectionSequence: 1, destinationStopSequence: 2,
    encodedPolyline: encode([[10.77, 106.7], [10.7704, 106.7006], [10.771, 106.701]]),
    distanceMeters: 160, travelDurationSeconds: 60, baseTravelDurationSeconds: 60,
    instructions: [{ action: 'turn', direction: 'right', instruction: 'Rẽ phải vào đường thử nghiệm', offset: 1 }] };
  const stops = [1, 2].map(sequenceNumber => ({ sequenceNumber, stationId: sequenceNumber, stationName: `Trạm ${sequenceNumber}`,
    latitude: sequenceNumber === 1 ? 10.77 : 10.771, longitude: sequenceNumber === 1 ? 106.7 : 106.701,
    dwellDurationSeconds: 0, checkinRadiusMeters: 50, arrivalOffsetSeconds: sequenceNumber === 1 ? 0 : 60,
    departureOffsetSeconds: sequenceNumber === 1 ? 0 : 60, plannedArrivalAt: stamp, plannedDepartureAt: stamp }));
  const active = status !== 'SCHEDULED';
  return {
    serverTime: stamp, routeRevisionId: revision,
    trip: { id, vehicleId: 1, vehiclePlateNumber: '51B-12345', vehicleType: 'CAR', routeId: 1, routeName: 'Tuyến thử nghiệm',
      scheduledDepartureAt: stamp, plannedEndAt: stamp, startedAt: active ? stamp : null, endedAt: null, createdAt: stamp,
      dispatchMode: 'ON_DEMAND', scheduleId: null, scheduleName: null, driver: null, status, attemptNumber: 1 },
    stops,
    stations: stops.map(s => ({ id: s.stationId, name: s.stationName, address: `Địa chỉ ${s.stationName}`,
      latitude: s.latitude, longitude: s.longitude, checkinRadiusMeters: s.checkinRadiusMeters,
      active: true, createdAt: stamp, updatedAt: stamp })),
    checkIns: { tripId: id, revision: 0, nextStopSequence: active ? 2 : 1, awaitingExit: false, visits: [] },
    route: { id: 1, name: 'Tuyến thử nghiệm', transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 160,
      estimatedTravelDurationSeconds: 60, baseTravelDurationSeconds: 60, totalDwellDurationSeconds: 0,
      estimatedTripDurationSeconds: 60, estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp,
      stops: stops.map(s => ({ ...s, role: s.sequenceNumber === 1 ? 'START' as const : 'END' as const,
        distanceFromPreviousMeters: 160, travelDurationFromPreviousSeconds: 60 })), sections: [section] },
    position: active ? { id: 1, eventId: 'fixture', vehicleId: 1, tripId: id, recordedAt: stamp, receivedAt: stamp, simulatedAt: stamp,
      latitude: 10.77, longitude: 106.7, speedKmh: 20, heading: 45, accuracyMeters: 0, source: 'SIMULATOR', attemptNumber: 1 } : null,
    simulation: active ? { id: 1, tripId: id, status: status === 'IN_PROGRESS' ? 'RUNNING' : 'COMPLETED', multiplier: 1,
      elapsedSeconds: 0, durationSeconds: 60, simulatedAt: stamp, updatedAt: stamp, errorMessage: null,
      replacementTripId: null, routeRevisionId: revision, attemptNumber: 1,
      frame: { latitude: 10.77, longitude: 106.7, heading: 45, speedKmh: 20, progressPercent: 0,
        nextStopSequence: 2, nextStopEtaSeconds: 60, dwellRemainingSeconds: 0, dwelling: false, finished: false } } : null,
    guidance: active ? { maneuver: section.instructions[0]!, distanceMeters: 80 } : null,
  };
}
export function driverOptions(): DriverRouteOptions {
  const sections = driverSnapshot().route.sections;
  return { token: 'preview-token', expiresAt: '2026-09-29T02:02:00Z', routeRevisionId: null,
    options: [0, 1].map(optionIndex => ({ optionIndex, label: optionIndex === 0 ? 'Đường đề xuất' : 'Đường thay thế 1',
      distanceMeters: 180 + optionIndex * 100, durationSeconds: 60 + optionIndex * 30, sections })) };
}
