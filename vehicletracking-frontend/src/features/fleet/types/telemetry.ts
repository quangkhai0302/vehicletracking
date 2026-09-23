import type { TelemetryPosition } from '@/features/tracking/types/operations';
export interface TelemetryPage {
  items: TelemetryPosition[]; page: number; size: number; totalElements: number; totalPages: number;
}
