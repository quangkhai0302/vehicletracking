import { useEffect, useRef, useState } from 'react';
import type { RouteCreateInput, RouteDetail, RouteDraftStop, RouteSummary } from '../../types/route';
import type { Station } from '../../types/station';
import { createRoute, fetchRouteById, fetchRoutes } from '../../services/routes';
import { RoutePanel } from './RoutePanel';
import { RouteDrawer } from './RouteDrawer';

interface RouteWorkspaceProps {
  stations: Station[];
  loadingStations: boolean;
  onDraftStopsChange: (stops: RouteDraftStop[]) => void;
  selectedDraftStopId: string | null;
  onFocusDraftStop: (id: string) => void;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onPlannedRouteDisplay: (routeDetail: RouteDetail | null) => void;
  onShowToast: (message: string) => void;
}

export function RouteWorkspace({
  stations, loadingStations, onDraftStopsChange, selectedDraftStopId, onFocusDraftStop, onFocusStop,
  onPlannedRouteDisplay,
  onShowToast,
}: RouteWorkspaceProps) {
  const [routes, setRoutes] = useState<RouteSummary[]>([]);
  const [loadingRoutes, setLoadingRoutes] = useState(true);
  const [routeError, setRouteError] = useState<string | null>(null);
  const [selectedRouteId, setSelectedRouteId] = useState<number | null>(null);
  const [routeDetail, setRouteDetail] = useState<RouteDetail | null>(null);
  const [loadingRouteDetail, setLoadingRouteDetail] = useState(false);
  const [routeDrawerMode, setRouteDrawerMode] = useState<'closed' | 'create' | 'view'>('closed');
  const [savingRoute, setSavingRoute] = useState(false);

  // M-04: Request token & AbortController to prevent race condition on consecutive route selections
  const detailAbortRef = useRef<AbortController | null>(null);
  const detailRequestIdRef = useRef<number>(0);

  // M4-01: Token & mounted flag to prevent deferred POST createRoute from usurping user selection
  const isMountedRef = useRef<boolean>(true);
  const createRequestIdRef = useRef<number>(0);

  // H3-01: Keep stable ref of onPlannedRouteDisplay to avoid re-triggering effects on parent renders
  const onPlannedRouteDisplayRef = useRef(onPlannedRouteDisplay);
  useEffect(() => {
    onPlannedRouteDisplayRef.current = onPlannedRouteDisplay;
  }, [onPlannedRouteDisplay]);

  // Initial load of routes - only runs once on mount
  useEffect(() => {
    const abortController = new AbortController();
    fetchRoutes(abortController.signal)
      .then((data) => {
        if (!abortController.signal.aborted) { setRoutes(data); setRouteError(null); }
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === 'AbortError') return;
        setRouteError(err instanceof Error ? err.message : 'Không thể tải danh sách tuyến đường');
      })
      .finally(() => {
        if (!abortController.signal.aborted) setLoadingRoutes(false);
      });

    return () => {
      abortController.abort();
    };
  }, []);

  // Unmount cleanup: only runs when RouteWorkspace unmounts (e.g. workspace switched)
  useEffect(() => {
    isMountedRef.current = true;
    const abortRef = detailAbortRef;
    const plannedDisplayRef = onPlannedRouteDisplayRef;
    return () => {
      isMountedRef.current = false;
      abortRef.current?.abort();
      plannedDisplayRef.current(null);
    };
  }, []);

  const handleRetry = () => {
    setLoadingRoutes(true);
    setRouteError(null);
    fetchRoutes()
      .then((data) => {
        setRoutes(data);
        setRouteError(null);
      })
      .catch((err: unknown) => {
        setRouteError(err instanceof Error ? err.message : 'Không thể tải danh sách tuyến đường');
      })
      .finally(() => {
        setLoadingRoutes(false);
      });
  };

  const handleSelectRoute = (route: RouteSummary) => {
    // Abort previous in-flight detail request
    if (detailAbortRef.current) {
      detailAbortRef.current.abort();
    }
    const abortController = new AbortController();
    detailAbortRef.current = abortController;
    const requestId = ++detailRequestIdRef.current;

    // M4-01: Invalidate any pending create request from usurping selection/drawer
    createRequestIdRef.current++;
    setSavingRoute(false);

    // M2-03: Immediately clear old detail and old map route before fetching route B
    setSelectedRouteId(route.id);
    setRouteDetail(null);
    onPlannedRouteDisplayRef.current(null);
    setRouteDrawerMode('view');
    setLoadingRouteDetail(true);
    setRouteError(null);

    fetchRouteById(route.id, abortController.signal)
      .then((detail) => {
        if (!isMountedRef.current || detailRequestIdRef.current !== requestId) return;
        setRouteDetail(detail);
        onPlannedRouteDisplayRef.current(detail);
      })
      .catch((err: unknown) => {
        if (!isMountedRef.current || detailRequestIdRef.current !== requestId) return;
        if (err instanceof Error && err.name === 'AbortError') return;
        const msg = err instanceof Error ? err.message : 'Không thể tải chi tiết tuyến đường';
        setRouteError(msg);
        setRouteDetail(null);
        onPlannedRouteDisplayRef.current(null);
      })
      .finally(() => {
        if (isMountedRef.current && detailRequestIdRef.current === requestId) {
          setLoadingRouteDetail(false);
        }
      });
  };

  const handleBeginCreateRoute = () => {
    if (detailAbortRef.current) {
      detailAbortRef.current.abort();
    }
    detailRequestIdRef.current++;
    createRequestIdRef.current++;
    setSavingRoute(false);
    setSelectedRouteId(null);
    setRouteDetail(null);
    onPlannedRouteDisplayRef.current(null);
    setRouteDrawerMode('create');
    setRouteError(null);
  };

  const handleCloseRouteDrawer = () => {
    if (detailAbortRef.current) {
      detailAbortRef.current.abort();
    }
    detailRequestIdRef.current++;
    createRequestIdRef.current++;
    setSavingRoute(false);
    setRouteDrawerMode('closed');
    setSelectedRouteId(null);
    setRouteDetail(null);
    onPlannedRouteDisplayRef.current(null);
  };

  const handleSaveRoute = async (input: RouteCreateInput) => {
    const createId = ++createRequestIdRef.current;
    setSavingRoute(true);
    setRouteError(null);
    let created: RouteDetail;
    try {
      created = await createRoute(input);
    } catch (err: unknown) {
      if (!isMountedRef.current || createRequestIdRef.current !== createId) {
        return;
      }
      const msg = err instanceof Error ? err.message : 'Lỗi khi tạo tuyến đường';
      setRouteError(msg);
      setSavingRoute(false);
      return;
    }

    // Luôn cập nhật danh sách tuyến cục bộ nếu component còn mounted
    const newSummary: RouteSummary = {
      id: created.id,
      name: created.name,
      transportMode: created.transportMode,
      routingProvider: created.routingProvider,
      startStationName: created.stops.length > 0 ? created.stops[0].stationName : '',
      endStationName: created.stops.length > 0 ? created.stops[created.stops.length - 1].stationName : '',
      stopCount: created.stops.length,
      totalDistanceMeters: created.totalDistanceMeters,
      estimatedTravelDurationSeconds: created.estimatedTravelDurationSeconds,
      totalDwellDurationSeconds: created.totalDwellDurationSeconds,
      estimatedTripDurationSeconds: created.estimatedTripDurationSeconds,
      calculatedAt: created.calculatedAt,
      createdAt: created.createdAt,
    };
    if (isMountedRef.current) {
      setRoutes((prev) => [newSummary, ...prev.filter((r) => r.id !== created.id)]);
    }

    // M4-01 & M5-01: Chỉ chiếm quyền selection, drawer view, map và kết thúc saving nếu response vẫn thuộc phiên tạo hiện hành
    if (isMountedRef.current && createRequestIdRef.current === createId) {
      setSelectedRouteId(created.id);
      setRouteDetail(created);
      onPlannedRouteDisplayRef.current(created);
      setRouteDrawerMode('view');
      onShowToast(`Đã tạo tuyến đường "${created.name}" thành công!`);
      setSavingRoute(false);
    } else if (isMountedRef.current) {
      // M5-01: Response stale của request cũ chỉ thông báo toast, tuyệt đối không gọi setSavingRoute(false)
      // để tránh mở khóa nút Lưu/Hủy khi có một request tạo mới khác đang pending
      onShowToast(`Đã tạo tuyến đường "${created.name}" thành công!`);
    }

    // Background sync - failure here does NOT affect the created route
    try {
      const refreshed = await fetchRoutes();
      if (isMountedRef.current) {
        setRoutes(refreshed);
      }
    } catch {
      // Background sync failed; local list already updated
    }
  };

  return (
    <>
      <div className="panel-list-slot" hidden={routeDrawerMode !== 'closed'}>
      <RoutePanel
        routes={routes}
        selectedRouteId={selectedRouteId}
        loading={loadingRoutes}
        error={routeError && routeDrawerMode === 'closed' ? routeError : null}
        onSelectRoute={handleSelectRoute}
        onBeginCreate={handleBeginCreateRoute}
        createDisabled={loadingStations}
        onRetry={handleRetry}
      />
      </div>
      <RouteDrawer
        mode={routeDrawerMode}
        routeDetail={routeDetail}
        stations={stations}
        onDraftStopsChange={onDraftStopsChange} selectedDraftStopId={selectedDraftStopId}
        onFocusDraftStop={onFocusDraftStop} onFocusStop={onFocusStop}
        loadingDetail={loadingRouteDetail}
        saving={savingRoute}
        error={routeDrawerMode !== 'closed' ? routeError : null}
        onClose={handleCloseRouteDrawer}
        onSaveRoute={handleSaveRoute}
      />
    </>
  );
}
