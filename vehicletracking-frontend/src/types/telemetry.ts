import type { TelemetryPosition } from './operations';
export interface TelemetryPage {
  items: TelemetryPosition[]; page: number; size: number; totalElements: number; totalPages: number;
}
