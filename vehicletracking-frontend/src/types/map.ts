export type MapTheme = 'google-roadmap' | 'google-satellite' | 'google-dark';

export type TrafficMode = 'off' | 'google' | 'here';

export type StationType = 'START' | 'STOP' | 'END';

export interface Station {
  id: string;
  name: string;
  code: string;
  latitude: number;
  longitude: number;
  type: StationType;
  radiusMeters: number;
  address?: string;
  orderIndex?: number;
}

export interface Route {
  id: string;
  name: string;
  code: string;
  stations: Station[];
  polyline: [number, number][]; // [lat, lng][]
  distanceMeters: number;
  durationSeconds: number;
  trafficDelaySeconds?: number;
}

export interface TrafficFlowSegment {
  id: string;
  description: string;
  points: [number, number][]; // [lat, lng][]
  speedKmh: number;
  freeFlowKmh: number;
  jamFactor: number; // 0.0 to 10.0
  traversability?: string | null;
  confidence?: number | null;
}

export interface TrafficIncident {
  id: string;
  description: string;
  type: string;
  criticality: 'minor' | 'major' | 'critical' | 'low';
  startTime?: string;
  endTime?: string;
  points: [number, number][];
  center: [number, number];
  status?: 'ACTIVE' | 'EXPIRED' | string;
}

export interface TrafficSummary {
  totalSegments: number;
  clearCount: number; // JamFactor < 4
  slowCount: number; // JamFactor 4 - 7.9
  congestedCount: number; // JamFactor >= 8
  lastUpdated: Date | null;
}
