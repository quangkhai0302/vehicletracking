import type { TrafficSource, TrafficStatus } from './traffic';

export interface TripEtaStop {
  sequenceNumber: number;
  stationName: string;
  state: 'CHECKED_IN' | 'NEXT' | 'PLANNED';
  etaAt: string | null;
  etaSeconds: number | null;
  actualArrivalAt: string | null;
  source: TrafficSource;
}

export interface AffectedTrafficSegment {
  sectionSequence: number;
  destinationStopSequence: number;
  kind: string;
  id: string;
  jamFactor: number;
  traversability: string;
  points?: number[][];
  center?: number[];
}

export interface TripEta {
  tripId: number;
  routeId: number;
  calculatedAt: string;
  source: TrafficSource;
  status: TrafficStatus;
  trafficObservedAt: string | null;
  trafficFetchedAt: string | null;
  nextStopSequence: number | null;
  baselineRemainingSeconds: number;
  totalRemainingSeconds: number;
  stops: TripEtaStop[];
  affectedSegments: AffectedTrafficSegment[];
  warning: string | null;
  geometryVersion: number;
  attemptNumber: number;
  routeRevisionId: number | null;
}
