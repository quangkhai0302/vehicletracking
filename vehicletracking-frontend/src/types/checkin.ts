export type CheckInSource = 'GPS' | 'SIMULATOR';
export type CheckInEvidenceKind = 'POINT' | 'SEGMENT' | 'ROUTE_TRACE';
export interface StopVisit {
  id: number; tripId: number; stopSequence: number; source: CheckInSource; evidenceKind: CheckInEvidenceKind;
  actualArrivalAt: string; simulatedArrivalAt: string | null; detectedAt: string;
  fromSampleId: number | null; toSampleId: number; evidenceFraction: number;
  latitude: number; longitude: number;
}
export interface TripCheckIns {
  tripId: number; revision: number; nextStopSequence: number | null;
  awaitingExit: boolean; visits: StopVisit[];
}
