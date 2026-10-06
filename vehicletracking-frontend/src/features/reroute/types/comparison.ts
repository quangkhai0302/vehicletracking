export type ComparisonMode = 'compare' | 'before' | 'after';
export interface ComparisonAnchor { latitude: number; longitude: number }
export interface ComparisonPath {
  encodedPolylines: string[];
  distanceMeters: number;
  durationSeconds: number | null;
}
export interface RouteComparison {
  tripId: number;
  revisionId: number;
  revisionNumber: number;
  createdAt: string;
  reason: string | null;
  status: 'AVAILABLE' | 'RECONSTRUCTED' | 'UNAVAILABLE';
  message: string | null;
  attemptNumber: number | null;
  anchor: ComparisonAnchor | null;
  before: ComparisonPath | null;
  after: ComparisonPath | null;
}
