import { useEffect, useState } from 'react';
import { fetchTrip } from '../services/fleet';
import type { TripDetail } from '../types/fleet';

/** Route ownership follows the selected vehicle's trip, not the open detail panel. */
export function useSelectedVehicleRoute(tripId: number | null, onError: (message: string) => void) {
  const [detail, setDetail] = useState<TripDetail | null>(null);

  useEffect(() => {
    if (tripId === null) return;
    const controller = new AbortController();
    fetchTrip(tripId, controller.signal).then(result => {
      if (!controller.signal.aborted) setDetail(result);
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) {
        setDetail(null);
        onError(error instanceof Error ? error.message : 'Không tải được tuyến của xe đang chọn.');
      }
    });
    return () => controller.abort();
  }, [tripId, onError]);

  // Hide the previous trip immediately while another selection is loading.
  return tripId !== null && detail?.trip.id === tripId ? detail.route : null;
}
