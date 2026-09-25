import { toValue, watch, type MaybeRefOrGetter } from 'vue';
import type { StopVisit } from '@/features/fleet/types/checkin';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { notifySuccess } from '@/shared/notifications/toast';

type SuccessNotifier = (...args: Parameters<typeof notifySuccess>) => unknown;

const visitKey = (visit: StopVisit) =>
  `${visit.tripId}:${visit.attemptNumber ?? 'current'}:${visit.id}`;

export function useCheckInNotifications(
  snapshot: MaybeRefOrGetter<OperationsSnapshot | null>,
  showSuccess: SuccessNotifier = notifySuccess,
) {
  const seen = new Set<string>();
  let previousServerTime: number | null = null;

  watch(
    () => toValue(snapshot),
    (current) => {
      if (!current) return;
      const currentServerTime = Date.parse(current.serverTime);
      const visits = current.checkIns.flatMap((checkIn) => checkIn.visits);

      if (previousServerTime === null) {
        visits.forEach((visit) => seen.add(visitKey(visit)));
        previousServerTime = currentServerTime;
        return;
      }

      for (const visit of visits) {
        const key = visitKey(visit);
        const detectedAt = Date.parse(visit.detectedAt);
        if (
          !seen.has(key) &&
          Number.isFinite(detectedAt) &&
          detectedAt > previousServerTime &&
          detectedAt <= currentServerTime
        ) {
          const trip = current.trips.find((item) => item.id === visit.tripId);
          const vehicle = trip?.vehiclePlateNumber ?? `Chuyến #${visit.tripId}`;
          showSuccess(
            `Xe ${vehicle} đã tự động check-in điểm dừng ${visit.stopSequence} · Chuyến #${visit.tripId}.`,
            { autoClose: 5000, toastId: `check-in-${key}` },
          );
        }
        seen.add(key);
      }
      previousServerTime = currentServerTime;
    },
    { immediate: true },
  );
}
