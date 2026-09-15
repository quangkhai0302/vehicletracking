import { useEffect, useState } from 'react';
import { fetchTripRoute } from '../services/fleet';
import type { RouteDetail } from '../types/route';

/** Route ownership follows the selected vehicle's trip, not the open detail panel. */
export function useSelectedVehicleRoute(tripId: number | null, onError: (message: string) => void, revisionKey = '') {
  const [detail, setDetail] = useState<{tripId: number; route: RouteDetail; revisionKey: string} | null>(null);

  useEffect(() => {
    if (tripId === null) return;
    const controller = new AbortController();
    fetchTripRoute(tripId, controller.signal).then(result => {
      if (!controller.signal.aborted) setDetail({tripId, route: result, revisionKey});
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) {
        setDetail(null);
        onError(error instanceof Error ? error.message : 'Không tải được tuyến của xe đang chọn.');
      }
    });
    return () => controller.abort();
  }, [tripId, onError, revisionKey]);

  // Hide the previous trip immediately while another selection is loading.
  return tripId !== null && detail?.tripId === tripId && detail.revisionKey === revisionKey ? detail.route : null;
}
