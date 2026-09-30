import type { TripSummary } from '@/features/fleet/types/fleet';

/** Backend uses UTC instants; forms and labels use the browser's local timezone. */
export const displayTripTime = (value: string | null) =>
  value
    ? new Date(value).toLocaleString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

export function toLocalDateTimeInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export const scheduledStopArrivalAt = (departureAt: string, arrivalOffsetSeconds: number) =>
  new Date(Date.parse(departureAt) + arrivalOffsetSeconds * 1000).toISOString();

export const tripReferenceTime = (trip: TripSummary) =>
  trip.dispatchMode === 'FIXED_SCHEDULE' ? trip.scheduledDepartureAt : trip.createdAt;

export const tripDispatchLabel = (trip: TripSummary) =>
  trip.dispatchMode === 'FIXED_SCHEDULE' ? 'Theo lịch cố định' : 'Điều phối tức thời';
