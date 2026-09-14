import type { TrafficFlowSegment, TrafficIncident } from './map';

export type TrafficSource = 'HERE_LIVE' | 'HERE_LAST_KNOWN' | 'ROUTE_SNAPSHOT' | 'UNAVAILABLE';
export type TrafficStatus = 'AVAILABLE' | 'STALE' | 'BLOCKED' | 'UNAVAILABLE';

export interface TrafficEnvelope<T> {
  source: TrafficSource;
  status: TrafficStatus;
  observedAt: string | null;
  fetchedAt: string;
  ageSeconds: number;
  warning: string | null;
  results: T[];
}

export type TrafficFlowResponse = TrafficEnvelope<TrafficFlowSegment>;
export type TrafficIncidentsResponse = TrafficEnvelope<TrafficIncident>;
