<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onScopeDispose,
  ref,
  shallowRef,
  toRefs,
  watch,
} from 'vue';
import L from 'leaflet';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import type { MapTheme } from '@/features/map/types/map';
import type { RouteDetail, RouteDraftStop } from '@/features/routes/types/route';
import type { WorkspaceMode } from '@/shared/types/workspace';
import { useLiveOperations } from '@/features/tracking/composables/useLiveOperations';
import { useSimulator } from '@/features/simulation/composables/useSimulator';
import { useSimulationFleet } from '@/features/simulation/composables/useSimulationFleet';
import {
  usePlannedVehicleAnchors,
  type VehicleMarkerAnchor,
} from '@/features/tracking/composables/usePlannedVehicleAnchors';
import { useVehicleMarkers } from '@/features/tracking/composables/useVehicleMarkers';
import { useSelectedVehicleRoute } from '@/features/routes/composables/useSelectedVehicleRoute';
import { useMapCamera } from '@/features/map/composables/useMapCamera';
import { useMapLayers } from '@/features/map/composables/useMapLayers';
import { useStationWorkspace } from '@/features/stations/composables/useStationWorkspace';
import { useCompactLayout } from '@/shared/composables/useCompactLayout';
import { useTraffic } from '@/features/traffic/composables/useTraffic';
import { makeMotionPath, type MotionPath } from '@/features/fleet/utils/vehicleMotion';
import MapControls from './MapControls.vue';
import StationDrawer from '@/features/stations/components/StationDrawer.vue';
import StationPanel from '@/features/stations/components/StationPanel.vue';
import RouteWorkspace from '@/features/routes/components/RouteWorkspace.vue';
import '@/features/routes/styles/route.css';
import {
  Bell,
  BusFront,
  ChevronDown,
  ChevronUp,
  Crosshair,
  List,
  MapPin,
  PanelLeftClose,
  Play,
  Radio,
  X,
} from '@lucide/vue';
import SimulatorPanel from '@/features/simulation/components/SimulatorPanel.vue';
import TripTrafficSummary from '@/features/tracking/components/TripTrafficSummary.vue';
import TrackingVehicleCard from '@/features/tracking/components/TrackingVehicleCard.vue';
import AlertStream from '@/features/tracking/components/AlertStream.vue';
import ConfirmStationDelete from '@/features/stations/components/ConfirmStationDelete.vue';
import TrafficLayer from '@/features/traffic/components/TrafficLayer.vue';
const RouteInspectionLayer = defineAsyncComponent(() => import('@/features/routes/components/RouteInspectionLayer.vue'));
const SimulationFleetLayer = defineAsyncComponent(
  () => import('@/features/simulation/components/SimulationFleetLayer.vue'),
);
const SimulationRoutesLayer = defineAsyncComponent(
  () => import('@/features/simulation/components/SimulationRoutesLayer.vue'),
);
const props = withDefaults(
  defineProps<{
    initialWorkspace?: WorkspaceMode;
    initialTripId?: number | null;
    embedded?: boolean;
    onWorkspaceChange?: (workspace: WorkspaceMode) => void;
  }>(),
  { initialWorkspace: 'tracking', initialTripId: null, embedded: false },
);
const rootRef = shallowRef<HTMLElement | null>(null),
  mapContainerRef = shallowRef<HTMLDivElement | null>(null),
  mapInstanceRef = shallowRef<L.Map | null>(null);
const workspace = ref<WorkspaceMode>(props.initialWorkspace),
  compact = useCompactLayout();
const activePanel = ref<'context' | 'simulator' | 'alerts' | null>('context'),
  drawerOpen = ref(props.initialWorkspace !== 'tracking'),
  sheetExpanded = ref(false);
const simulatorExpanded = ref(true),
  alertsOpen = ref(false),
  alertCloseButtonRef = shallowRef<HTMLButtonElement | null>(null);
let alertPreviousPanel: 'context' | 'simulator' | null = null,
  alertFocusFrame = 0;
const alertUnreadOverride = shallowRef<{ serverTime: string; count: number } | null>(null);
const showStations = ref(true),
  showRoutes = ref(true),
  showTraffic = ref(true),
  draftStops = shallowRef<RouteDraftStop[]>([]),
  selectedDraftStopId = ref<string | null>(null);
watch(
  () => props.initialWorkspace,
  (mode) => {
    workspace.value = mode;
    drawerOpen.value = mode !== 'tracking';
    activePanel.value = mode === 'simulation' ? 'simulator' : 'context';
    if (mode === 'simulation') simulatorExpanded.value = true;
  },
  { immediate: true },
);
const mapReady = computed(() => mapInstanceRef.value !== null),
  theme = ref<MapTheme>('google-roadmap'),
  toast = ref<string | null>(null);
const setToast = (message: string) => {
  toast.value = message;
};
const live = useLiveOperations(),
  simulator = useSimulator(() => live.snapshot, setToast);
const simulationFleet = useSimulationFleet(
  () => live.snapshot,
  () => workspace.value === 'simulation',
);
const persistedVehicleAnchors = usePlannedVehicleAnchors(() => live.snapshot);
const traffic = useTraffic(mapInstanceRef, showTraffic, mapReady);
const trafficStatus = computed(() => traffic.flow?.status ?? traffic.incidents?.status);
const trafficMessage = computed(() =>
  traffic.loading
    ? 'Đang tải giao thông…'
    : traffic.error
      ? 'Chưa tải được giao thông. Thử lại trong lớp bản đồ.'
      : trafficStatus.value === 'STALE'
        ? 'Đang dùng dữ liệu gần nhất.'
        : trafficStatus.value === 'UNAVAILABLE'
          ? 'Giao thông chưa khả dụng.'
          : showTraffic.value
            ? 'Đang hiển thị dữ liệu theo nguồn của tuyến/khu vực'
            : 'Tạm tắt',
);
const connectionLabel = computed(() =>
  live.connection === 'live'
    ? 'Đang cập nhật trực tiếp'
    : live.connection === 'connecting'
      ? 'Đang kết nối…'
      : 'Mất kết nối · Đang thử lại',
);
const { focusLocation, fitBounds, getVisibleCenter, releaseFocus } = useMapCamera(
  rootRef,
  mapInstanceRef,
);
const routeRefreshToken = ref(0);
const stationWorkspace = useStationWorkspace({
  focusLocation,
  onToast: setToast,
  onStationUpdated: () => {
    routeRefreshToken.value++;
  },
  onPickStart: () => {
    activePanel.value = null;
  },
  onPickEnd: () => {
    drawerOpen.value = true;
    activePanel.value = 'context';
  },
});
const {
  stations,
  loadingStations,
  savingStation,
  deletingStation,
  stationError,
  selectedStationId,
  formMode,
  stationForm,
  pickingLocation,
  deleteCandidate,
  selectedStation,
} = toRefs(stationWorkspace);
const {
  setStationForm,
  setPickingLocation,
  setDeleteCandidate,
  handleBeginCreate,
  handleSelectStation,
  handleBeginEdit,
  handleCloseStationDrawer,
  handleSaveStation,
  handleFieldChange,
  handleDeactivate,
} = stationWorkspace;
const contextVisible = computed(
  () =>
    drawerOpen.value &&
    (workspace.value !== 'tracking' || selectedVehicleId.value !== null) &&
    !(pickingLocation.value && workspace.value === 'stations') &&
    (!compact.value || activePanel.value === 'context'),
);
const unreadAlertCount = computed(() => {
  if (!live.snapshot) return null;
  return alertUnreadOverride.value?.serverTime === live.snapshot.serverTime
    ? alertUnreadOverride.value.count
    : live.snapshot.notifications.filter((item) => !item.readAt).length;
});
const reportUnreadAlertCount = (count: number) => {
  if (live.snapshot) alertUnreadOverride.value = { serverTime: live.snapshot.serverTime, count };
};
const closeAlerts = () => {
  alertsOpen.value = false;
  activePanel.value = compact.value ? alertPreviousPanel : 'context';
  cancelAnimationFrame(alertFocusFrame);
  alertFocusFrame = requestAnimationFrame(() =>
    rootRef.value?.querySelector<HTMLButtonElement>('.panel-alert-launcher')?.focus(),
  );
};
watch(alertsOpen, (open, _old, cleanup) => {
  if (!open) return;
  const frame = requestAnimationFrame(() => alertCloseButtonRef.value?.focus());
  const keydown = (event: KeyboardEvent) => {
    if (
      event.key === 'Escape' &&
      !(event.target instanceof HTMLElement && event.target.closest('dialog'))
    )
      closeAlerts();
  };
  window.addEventListener('keydown', keydown);
  cleanup(() => {
    cancelAnimationFrame(frame);
    window.removeEventListener('keydown', keydown);
  });
});
onScopeDispose(() => cancelAnimationFrame(alertFocusFrame));
const selectMode = (mode: WorkspaceMode) => {
  if (mode !== 'stations') setPickingLocation(false);
  alertsOpen.value = false;
  workspace.value = mode;
  props.onWorkspaceChange?.(mode);
  sheetExpanded.value = false;
  drawerOpen.value = mode !== 'tracking';
  activePanel.value = mode === 'simulation' ? 'simulator' : 'context';
  if (mode === 'simulation') simulatorExpanded.value = true;
};
const openPanel = (panel: 'context' | 'simulator' | 'alerts') => {
  const previous = activePanel.value;
  setPickingLocation(false);
  activePanel.value = panel;
  sheetExpanded.value = false;
  if (panel !== 'alerts') alertsOpen.value = false;
  if (panel === 'context') drawerOpen.value = true;
  if (panel === 'simulator') simulatorExpanded.value = true;
  if (panel === 'alerts') {
    if (!alertsOpen.value)
      alertPreviousPanel = previous === 'context' || previous === 'simulator' ? previous : null;
    alertsOpen.value = true;
  }
};
const openAlerts = () => {
  openPanel('alerts');
};
const editorRoute = shallowRef<RouteDetail | null>(null),
  createdVehicleAnchors = shallowRef<VehicleMarkerAnchor[]>([]);
const selectedVehicleId = ref<number | null>(null),
  tripSelection = shallowRef<{ tripId: number | null } | null>(null),
  followingVehicle = ref(false);
let initialTripApplied: number | null = null;
const plannedVehicleAnchors = computed(() => {
  const byVehicle = new Map<number, VehicleMarkerAnchor>();
  persistedVehicleAnchors.value.forEach((anchor) => byVehicle.set(anchor.vehicleId, anchor));
  createdVehicleAnchors.value.forEach((anchor) => {
    const trip = live.snapshot?.trips.find((item) => item.id === anchor.tripId);
    if (!trip || (trip.status !== 'COMPLETED' && trip.status !== 'CANCELLED'))
      byVehicle.set(anchor.vehicleId, anchor);
  });
  return [...byVehicle.values()];
});
const mapSnapshot = computed(() => {
  if (!live.snapshot || workspace.value !== 'simulation') return live.snapshot;
  const waitingIds = new Set(simulationFleet.previews.map((item) => item.trip.vehicleId)),
    tripIds = new Set(simulationFleet.trips.map((trip) => trip.id));
  return {
    ...live.snapshot,
    positions: live.snapshot.positions.filter(
      (point) =>
        !waitingIds.has(point.vehicleId) &&
        (point.source !== 'SIMULATOR' || tripIds.has(point.tripId)),
    ),
  };
});
const selectedVehicle = computed(
  () =>
    mapSnapshot.value?.positions.find(
      (point) =>
        point.vehicleId === selectedVehicleId.value &&
        (!tripSelection.value?.tripId || point.tripId === tripSelection.value.tripId),
    ) ?? null,
);
const markerAnchors = computed(() =>
  workspace.value === 'simulation'
    ? plannedVehicleAnchors.value.filter(
        (anchor) => !simulationFleet.previews.some((item) => item.trip.id === anchor.tripId),
      )
    : plannedVehicleAnchors.value,
);
const selectedPlannedVehicle = computed(
  () =>
    plannedVehicleAnchors.value.find(
      (anchor) =>
        anchor.vehicleId === selectedVehicleId.value &&
        (!tripSelection.value?.tripId || anchor.tripId === tripSelection.value.tripId),
    ) ?? null,
);
const selectedWaitingVehicle = computed(() =>
  simulationFleet.previews.find(
    (item) =>
      item.trip.vehicleId === selectedVehicleId.value &&
      (!tripSelection.value?.tripId || item.trip.id === tripSelection.value.tripId),
  ),
);
const selectedTripId = computed(
  () =>
    (selectedVehicleId.value !== null ? tripSelection.value?.tripId : null) ??
    selectedVehicle.value?.tripId ??
    (selectedVehicleId.value === null
      ? null
      : (selectedPlannedVehicle.value?.tripId ??
        selectedWaitingVehicle.value?.trip.id ??
        (simulator.trip?.vehicleId === selectedVehicleId.value ? simulator.trip.id : null))),
);
const selectedTrip = computed(
  () => live.snapshot?.trips.find((trip) => trip.id === selectedTripId.value) ?? null,
);
const selectedRun = computed(() =>
  live.snapshot?.simulations.find((run) => run.tripId === selectedTripId.value),
);
const vehicleRoute = useSelectedVehicleRoute(
  selectedTripId,
  setToast,
  () => `${selectedRun.value?.attemptNumber ?? 1}:${selectedRun.value?.routeRevisionId ?? 0}`,
);
const plannedRoute = computed(() =>
  workspace.value === 'routes'
    ? editorRoute.value
    : workspace.value === 'stations'
      ? null
      : vehicleRoute.value,
);
const selectedSimulationRoutes = computed(() =>
  simulationFleet.routes.filter((item) => item.trip.id === selectedTripId.value),
);
const motionPaths = computed(() => {
  const paths = new Map<number, MotionPath>();
  for (const item of simulationFleet.routes) paths.set(item.trip.id, makeMotionPath(item.segments));
  if (selectedTripId.value !== null && vehicleRoute.value) {
    try {
      paths.set(
        selectedTripId.value,
        makeMotionPath(
          vehicleRoute.value.sections.map((section) =>
            decodeFlexiblePolyline(section.encodedPolyline),
          ),
        ),
      );
    } catch {
      /* Invalid geometry is not an interpolation path. */
    }
  }
  return paths;
});
const hasSimulationRoute = computed(
  () => workspace.value === 'simulation' && selectedSimulationRoutes.value.length > 0,
);
const selectVehicleTrip = (vehicleId: number, tripId: number) => {
  if (simulator.busy) return;
  selectedVehicleId.value = vehicleId;
  tripSelection.value = { tripId };
  simulator.select(tripId);
  followingVehicle.value = false;
  if (workspace.value === 'routes' || workspace.value === 'stations') {
    workspace.value = 'tracking';
    props.onWorkspaceChange?.('tracking');
    setPickingLocation(false);
  }
  drawerOpen.value = true;
  simulatorExpanded.value = true;
  sheetExpanded.value = true;
  activePanel.value = 'context';
};
watch(
  [() => props.initialTripId, () => live.snapshot, () => simulator.busy],
  ([id, snapshot, busy]) => {
    if (id === null) {
      initialTripApplied = null;
      return;
    }
    if (initialTripApplied === id || !snapshot || busy) return;
    const trip = snapshot.trips.find((item) => item.id === id);
    if (!trip) return;
    initialTripApplied = id;
    selectVehicleTrip(trip.vehicleId, trip.id);
    const point = snapshot.positions.find((item) => item.tripId === trip.id);
    if (point) focusLocation([point.latitude, point.longitude], 16);
  },
  { immediate: true },
);
const clearVehicleSelection = () => {
  if (simulator.busy) return;
  selectedVehicleId.value = null;
  followingVehicle.value = false;
  tripSelection.value = { tripId: null };
  simulator.select(null);
  if (workspace.value === 'tracking') drawerOpen.value = false;
};
useVehicleMarkers(() => ({
  mapRef: mapInstanceRef,
  snapshot: mapSnapshot.value,
  now: live.now,
  motionPaths: motionPaths.value,
  plannedPositions: markerAnchors.value,
  visible: true,
  selectedId: selectedVehicleId.value,
  groupSelection: workspace.value === 'simulation',
  following: followingVehicle.value,
  onSelect: selectVehicleTrip,
  onFocus: focusLocation,
}));
const focusVehicle = (id: number) => {
  const planned = plannedVehicleAnchors.value.find((item) => item.vehicleId === id),
    actual = mapSnapshot.value?.positions.find((item) => item.vehicleId === id);
  const point = planned && actual?.tripId !== planned.tripId ? planned : (actual ?? planned);
  if (point && !simulator.busy) {
    selectVehicleTrip(id, point.tripId);
    focusLocation([point.latitude, point.longitude], 16);
  }
};
const openSimulation = (id: number) => {
  if (simulator.busy) return;
  const trip = live.snapshot?.trips.find((item) => item.id === id);
  if (trip) selectVehicleTrip(trip.vehicleId, id);
  else {
    selectedVehicleId.value = null;
    tripSelection.value = { tripId: id };
    simulator.select(id);
  }
  selectMode('simulation');
};
const selectSimulationVehicle = (tripId: number) => {
  if (simulator.busy) return;
  const preview = simulationFleet.previews.find((item) => item.trip.id === tripId),
    trip = preview?.trip ?? live.snapshot?.trips.find((item) => item.id === tripId);
  if (!trip) return;
  selectVehicleTrip(trip.vehicleId, tripId);
  workspace.value = 'simulation';
  props.onWorkspaceChange?.('simulation');
  activePanel.value = 'simulator';
  const point = live.snapshot?.positions.find((item) => item.tripId === tripId);
  if (preview) focusLocation([preview.start.latitude, preview.start.longitude], 16);
  else if (point) focusLocation([point.latitude, point.longitude], 16);
};
const fleetPoints = computed<[number, number][]>(() => [
  ...(showRoutes.value
    ? selectedSimulationRoutes.value.flatMap((item) => item.segments.flat())
    : []),
  ...simulationFleet.previews.map(
    (item) => [item.start.latitude, item.start.longitude] as [number, number],
  ),
  ...(mapSnapshot.value?.positions
    .filter((point) => simulationFleet.trips.some((trip) => trip.id === point.tripId))
    .map((point) => [point.latitude, point.longitude] as [number, number]) ?? []),
]);
let fleetFitted = false;
watch(
  [workspace, mapReady, () => simulationFleet.loading, () => JSON.stringify(fleetPoints.value)],
  ([mode, ready, loading, key]) => {
    if (mode !== 'simulation') {
      fleetFitted = false;
      return;
    }
    if (!ready || loading || fleetFitted || key === '[]') return;
    fleetFitted = true;
    fitBounds(L.latLngBounds(JSON.parse(key) as [number, number][]));
  },
  { immediate: true },
);
const fitSimulationFleet = () => {
  if (fleetPoints.value.length) {
    followingVehicle.value = false;
    fitBounds(L.latLngBounds(fleetPoints.value));
  }
};
watch(toast, (message, _old, cleanup) => {
  if (!message) return;
  const timer = window.setTimeout(() => {
    toast.value = null;
  }, 3500);
  cleanup(() => window.clearTimeout(timer));
});
const { plannedRouteBounds, basemapStatus, retryBasemap } = useMapLayers(mapContainerRef, mapInstanceRef, () => ({
  workspace: workspace.value,
  showStations: showStations.value,
  showRoutes: showRoutes.value,
  showTraffic: showTraffic.value,
  theme: theme.value,
  plannedRoute: plannedRoute.value,
  hasSimulationRoute: hasSimulationRoute.value,
  draftStops: draftStops.value,
  selectedDraftStopId: selectedDraftStopId.value,
  stationWorkspace,
  setSelectedDraftStopId: (id) => {
    selectedDraftStopId.value = id;
  },
  setDrawerOpen: (open) => {
    drawerOpen.value = open;
  },
  setActivePanel: (panel) => {
    activePanel.value = panel;
  },
  setFollowingVehicle: (value) => {
    followingVehicle.value = value;
  },
  releaseFocus,
  setToast,
  focusLocation,
  fitBounds,
}));
const focusDraftStop = (id: string) => {
  selectedDraftStopId.value = id;
  const stop = draftStops.value.find((item) => item.id === id),
    station = stations.value.find((item) => item.id === stop?.stationId);
  if (station) focusLocation([station.latitude, station.longitude], 16);
};
const handleFit = () => {
  if (workspace.value === 'simulation' && fleetPoints.value.length) {
    fitSimulationFleet();
    return;
  }
  if (plannedRouteBounds.value && showRoutes.value) {
    fitBounds(plannedRouteBounds.value);
    return;
  }
  const draftStations = draftStops.value
    .map((stop) => stations.value.find((station) => station.id === stop.stationId))
    .filter((station) => station !== undefined);
  const points =
    workspace.value === 'routes' && draftStations.length
      ? draftStations
      : showStations.value
        ? stations.value
        : [];
  if (points.length)
    fitBounds(L.latLngBounds(points.map((station) => [station.latitude, station.longitude])));
};
const handlePickMapCenter = () => {
  if (!mapInstanceRef.value) return;
  const center = getVisibleCenter();
  if (!center) return;
  setStationForm((current) => ({
    ...current,
    latitude: center.lat.toFixed(6),
    longitude: center.lng.toFixed(6),
  }));
  setPickingLocation(false);
  drawerOpen.value = true;
  activePanel.value = 'context';
  setToast('Đã gán tọa độ tâm bản đồ vào biểu mẫu.');
  focusLocation(center);
};
const toggleFollow = () => {
  followingVehicle.value = !followingVehicle.value;
  if (followingVehicle.value && selectedVehicle.value)
    focusLocation([selectedVehicle.value.latitude, selectedVehicle.value.longitude], 16);
};
const collapseContext = () => {
  drawerOpen.value = false;
  activePanel.value = null;
  rootRef.value?.querySelector<HTMLButtonElement>('.panel-launchers button')?.focus();
};
const showSimulatorRoute = () => {
  workspace.value = 'simulation';
  props.onWorkspaceChange?.('simulation');
  if (simulator.trip) {
    const preview = simulationFleet.previews.find((item) => item.trip.id === simulator.tripId);
    if (preview) focusLocation([preview.start.latitude, preview.start.longitude], 16);
    else {
      focusVehicle(simulator.trip.vehicleId);
      followingVehicle.value = true;
    }
  }
};
const setPlannedRoute = (route: RouteDetail | null) => {
  editorRoute.value = route;
};
const setDraftStops = (stops: RouteDraftStop[]) => {
  draftStops.value = stops;
};
</script>
<template>
  <section
    ref="rootRef"
    :class="`map-first${embedded ? ' map-first-embedded' : ''}`"
    aria-label="Không gian bản đồ vận hành"
    :data-workspace="workspace"
    :data-sheet-expanded="sheetExpanded"
    :data-drawer-open="contextVisible"
    :data-alerts-open="alertsOpen"
  >
    <div
      id="main-map"
      ref="mapContainerRef"
      class="map-canvas"
      aria-label="Bản đồ tương tác"
      :tabindex="-1"
    />
    <div v-if="basemapStatus === 'error'" class="basemap-error glass-panel" role="alert">
      <strong>Không tải được bản đồ nền</strong>
      <p>Biểu tượng xe vẫn hiển thị, nhưng chưa thể xem đường và địa điểm. Kiểm tra kết nối rồi thử lại.</p>
      <button type="button" @click="retryBasemap">Tải lại bản đồ</button>
    </div>
    <TrafficLayer
      :map="mapInstanceRef"
      :map-ready="mapReady"
      :visible="showTraffic"
      :incidents="traffic.incidents"
    />
    <template v-if="workspace === 'simulation'"
      ><SimulationFleetLayer
        :map="mapInstanceRef"
        :map-ready="mapReady"
        visible
        :vehicles="simulationFleet.previews"
        :on-select="selectSimulationVehicle" /><SimulationRoutesLayer
        :map="mapInstanceRef"
        :map-ready="mapReady"
        :visible="showRoutes"
        :routes="selectedSimulationRoutes"
        :selected-trip-id="selectedTripId"
        :on-select="selectSimulationVehicle"
    /></template>
    <RouteInspectionLayer
      v-if="plannedRoute || hasSimulationRoute"
      :map="mapInstanceRef"
      :map-ready="mapReady"
      :route="
        workspace === 'simulation' ? (selectedSimulationRoutes[0]?.route ?? null) : plannedRoute
      "
      :visible="
        showRoutes && !pickingLocation && (workspace !== 'simulation' || hasSimulationRoute)
      "
      :traffic-enabled="showTraffic"
    />
    <div
      class="live-follow glass-panel"
      data-map-edge="top"
      :hidden="(!selectedVehicle && !selectedWaitingVehicle && !selectedPlannedVehicle) || (workspace === 'tracking' && contextVisible && !!selectedPlannedVehicle && !selectedVehicle)"
    >
      <div
        v-if="!selectedVehicle && selectedWaitingVehicle"
        class="live-follow-actions"
      >
        <span>{{ selectedWaitingVehicle.trip.vehiclePlateNumber }} · Chờ mô phỏng</span
        ><button
          type="button"
          :disabled="simulator.busy"
          @click="openSimulation(selectedWaitingVehicle.trip.id)"
        >
          Chạy mô phỏng</button
        ><button
          aria-label="Bỏ chọn xe"
          :disabled="simulator.busy"
          @click="clearVehicleSelection"
        >
          ×
        </button>
      </div>
      <div
        v-if="!selectedVehicle && !selectedWaitingVehicle && selectedPlannedVehicle"
        class="live-follow-actions"
      >
        <span>{{ selectedPlannedVehicle.vehiclePlateNumber }} · Chưa khởi hành</span
        ><button
          aria-label="Bỏ chọn xe"
          :disabled="simulator.busy"
          @click="clearVehicleSelection"
        >
          ×
        </button>
      </div>
      <template v-if="selectedVehicle"
        ><div class="live-follow-actions">
          <span
            >{{
              live.snapshot?.trips.find((trip) => trip.id === selectedVehicle?.tripId)
                ?.vehiclePlateNumber ?? selectedVehicle.vehicleId
            }}
            · {{ selectedVehicle.source === 'SIMULATOR' ? 'Giả lập' : 'GPS' }}</span
          ><button
            :aria-pressed="followingVehicle"
            @click="toggleFollow"
          >
            {{ followingVehicle ? 'Bỏ theo xe' : 'Theo xe' }}</button
          ><button
            aria-label="Bỏ chọn xe"
            :disabled="simulator.busy"
            @click="clearVehicleSelection"
          >
            ×
          </button>
        </div>
        <TripTrafficSummary
          :key="`${selectedVehicle.tripId}:${selectedVehicle.attemptNumber ?? 1}`"
          context="vehicle"
          :trip-id="selectedVehicle.tripId"
          :position="selectedVehicle"
          :run="
            selectedVehicle.source === 'SIMULATOR'
              ? (live.snapshot?.simulations.find((run) => run.tripId === selectedVehicle?.tripId) ??
                null)
              : null
          "
          :now="live.now"
          :connection="live.connection"
        />
      </template>
    </div>
    <aside
      class="context-drawer glass-panel"
      data-map-edge="left"
      :hidden="!contextVisible"
      aria-label="Bảng dữ liệu vận hành"
    >
      <div class="floating-panel-heading">
        <span
          ><MapPin v-if="workspace === 'stations'" :size="15" /><Play v-else-if="workspace === 'simulation'" :size="15" /><BusFront v-else-if="workspace === 'tracking'" :size="15" /><List v-else :size="15" />{{
            workspace === 'simulation'
              ? 'MÔ PHỎNG CHUYẾN ĐI'
              : workspace === 'stations'
                ? 'QUẢN LÝ TRẠM DỪNG'
                : workspace === 'tracking'
                  ? 'XE ĐƯỢC CHỌN'
                  : 'VẬN HÀNH'
          }}</span
        >
        <div>
          <button
            class="sheet-expand"
            :aria-label="sheetExpanded ? 'Thu chiều cao bảng' : 'Mở rộng bảng'"
            @click="sheetExpanded = !sheetExpanded"
          >
            <ChevronDown
              v-if="sheetExpanded"
              :size="16"
            /><ChevronUp
              v-else
              :size="16"
            /></button
          ><button
            aria-label="Thu bảng dữ liệu"
            @click="collapseContext"
          >
            <PanelLeftClose :size="16" />
          </button>
        </div>
      </div>
      <div
        class="planning-tabs"
        :hidden="workspace !== 'routes'"
        aria-label="Dữ liệu lộ trình"
      >
        <button
          :aria-pressed="workspace === 'routes'"
          @click="selectMode('routes')"
        >
          Tuyến đường</button
        ><button
          :aria-pressed="workspace === 'stations'"
          @click="selectMode('stations')"
        >
          Trạm dừng <span>{{ stations.length }}</span>
        </button>
      </div>
      <div class="context-content" :hidden="workspace !== 'tracking'">
        <TrackingVehicleCard
          :trip="selectedTrip"
          :position="selectedVehicle"
          :on-clear="clearVehicleSelection"
        />
      </div>
      <div
        class="context-content simulator-workspace"
        :hidden="workspace !== 'simulation'"
      >
        <SimulatorPanel
          :key="`${simulator.tripId ?? 'none'}:${simulator.run?.attemptNumber ?? 1}`"
          :simulator="simulator"
          :snapshot="live.snapshot"
          :now="live.now"
          :connection="live.connection"
          :connection-error="live.error"
          :on-reconnect="live.reconnect"
          :fleet="simulationFleet"
          :on-select-vehicle="selectSimulationVehicle"
          :on-fit-fleet="fitSimulationFleet"
          :on-manage-fleet="() => selectMode('tracking')"
          :on-show-route="showSimulatorRoute"
        />
      </div>
      <div
        class="context-content station-workspace"
        :hidden="workspace !== 'stations'"
      >
        <div
          class="panel-list-slot"
          :hidden="formMode !== 'closed' || selectedStation !== null"
        >
          <StationPanel
            :stations="stations"
            :selected-station-id="selectedStationId"
            :loading="loadingStations"
            :error="formMode === 'closed' ? stationError : null"
            :selection-disabled="formMode !== 'closed'"
            :mode="formMode"
            :on-begin-create="handleBeginCreate"
            :on-select="handleSelectStation"
            :on-begin-edit="handleBeginEdit"
            :on-delete="setDeleteCandidate"
          />
        </div>
        <StationDrawer
          :station="selectedStation"
          :mode="formMode"
          :form="stationForm"
          :saving="savingStation"
          :error="stationError"
          :picking-location="pickingLocation"
          :on-close="handleCloseStationDrawer"
          :on-begin-edit="() => handleBeginEdit()"
          :on-pick-location="
            () => {
              setPickingLocation(true);
              activePanel = null;
            }
          "
          :on-field-change="handleFieldChange"
          :on-save="handleSaveStation"
          :on-request-deactivate="
            () => {
              if (selectedStation) setDeleteCandidate(selectedStation);
            }
          "
        />
      </div>
      <div
        class="context-content"
        :hidden="workspace !== 'routes'"
      >
        <RouteWorkspace
          :map="mapInstanceRef"
          :stations="stations"
          :loading-stations="loadingStations"
          :on-planned-route-display="setPlannedRoute"
          :on-show-toast="setToast"
          :on-draft-stops-change="setDraftStops"
          :selected-draft-stop-id="selectedDraftStopId"
          :on-focus-draft-stop="focusDraftStop"
          :on-focus-stop="focusLocation"
          :refresh-token="routeRefreshToken"
        />
      </div>
      <div
        v-if="workspace === 'stations' || workspace === 'routes'"
        class="context-footer"
      >
        <span class="status-dot" />{{
          workspace === 'stations' ? 'Danh sách trạm đã lưu' : 'Quản lý tuyến đường'
        }}
      </div>
    </aside>
    <aside
      id="operations-alert-drawer"
      class="alert-drawer glass-panel"
      role="region"
      aria-label="Thông báo vận hành"
      :hidden="!alertsOpen"
    >
      <div class="floating-panel-heading">
        <span><Bell :size="15" />THÔNG BÁO VẬN HÀNH</span>
        <div>
          <span class="count-badge">{{ unreadAlertCount ?? '—' }}</span
          ><button
            ref="alertCloseButtonRef"
            type="button"
            aria-label="Đóng thông báo vận hành"
            @click="closeAlerts"
          >
            <X :size="16" />
          </button>
        </div>
      </div>
      <div class="floating-panel-body">
        <AlertStream
          v-if="live.snapshot"
          :notifications="live.snapshot.notifications"
          :on-unread-count-change="reportUnreadAlertCount"
        />
        <div
          v-else
          class="alert-drawer-status"
        >
          <Bell :size="22" /><strong>{{
            live.error ? 'Chưa tải được thông báo' : 'Đang tải thông báo vận hành…'
          }}</strong>
          <p>{{ live.error ?? 'Đang chờ dữ liệu vận hành đầu tiên từ máy chủ.' }}</p>
          <button
            v-if="live.error"
            type="button"
            class="alert-reconnect-button"
            @click="live.reconnect"
          >
            Thử kết nối lại
          </button>
        </div>
      </div>
    </aside>
    <div
      class="panel-launchers"
      data-map-edge="bottom"
      aria-label="Mở bảng công cụ"
    >
      <button
        v-if="workspace === 'stations'"
        :aria-pressed="drawerOpen"
        @click="
          drawerOpen = !drawerOpen;
          activePanel = 'context';
        "
      >
        <MapPin :size="16" /><span>Danh sách trạm</span>
      </button>

      <button
        v-if="workspace !== 'stations'"
        :aria-pressed="workspace === 'tracking'"
        @click="
          selectMode('tracking');
          drawerOpen = false;
        "
      >
        <BusFront :size="16" /><span>Theo dõi trực tiếp</span>
      </button>

      <button
        v-if="workspace !== 'stations'"
        :aria-pressed="workspace === 'simulation' && drawerOpen"
        @click="
          if (workspace === 'simulation' && drawerOpen) {
            selectMode('tracking');
            drawerOpen = false;
          } else {
            selectMode('simulation');
            drawerOpen = true;
            activePanel = 'context';
          }
        "
      >
        <Play :size="16" /><span>Mô phỏng xe</span>
      </button>

      <button
        class="panel-alert-launcher"
        :aria-pressed="alertsOpen"
        @click="openAlerts"
      >
        <Bell :size="16" /><span>Cảnh báo</span
        ><b v-if="unreadAlertCount !== null && unreadAlertCount > 0">{{
          unreadAlertCount > 99 ? '99+' : unreadAlertCount
        }}</b>
      </button>
      <span
        v-if="workspace === 'tracking' || workspace === 'simulation'"
        class="launcher-connection"
      >
        <span class="live-beacon-dot" aria-hidden="true" />
        <Radio :size="13" />
        <span>{{ connectionLabel }}</span>
      </span>
    </div>
    <div
      class="map-picking-banner"
      role="status"
      :hidden="!pickingLocation || workspace !== 'stations'"
    >
      <Crosshair :size="17" /><span>Kéo bản đồ để đưa vị trí trạm vào tâm ngắm</span
      ><button
        class="picking-center-btn"
        @click="handlePickMapCenter"
      >
        Chọn vị trí này</button
      ><button
        class="picking-cancel-btn"
        aria-label="Hủy chế độ chọn vị trí"
        @click="
          setPickingLocation(false);
          openPanel('context');
        "
      >
        <X :size="16" />
      </button>
    </div>
    <div
      class="station-center-target"
      :hidden="!pickingLocation || workspace !== 'stations'"
      aria-hidden="true"
    >
      <span />
    </div>
    <MapControls
      :theme="theme"
      :on-theme-change="
        (value) => {
          theme = value;
        }
      "
      :on-reset-center="() => focusLocation([10.7769, 106.7009], 13)"
      :on-zoom-in="() => mapInstanceRef?.zoomIn()"
      :on-zoom-out="() => mapInstanceRef?.zoomOut()"
      :on-fit="handleFit"
      :can-fit="
        (workspace === 'simulation' && fleetPoints.length > 0) ||
        (showStations && stations.length > 0) ||
        (showRoutes && (plannedRoute !== null || draftStops.length > 0))
      "
      :show-stations="showStations"
      :show-routes="showRoutes"
      :on-toggle-stations="
        () => {
          showStations = !showStations;
        }
      "
      :on-toggle-routes="
        () => {
          showRoutes = !showRoutes;
        }
      "
      :show-traffic="showTraffic"
      :on-toggle-traffic="
        () => {
          showTraffic = !showTraffic;
        }
      "
      :traffic-message="trafficMessage"
      :traffic-can-retry="Boolean(traffic.error) || trafficStatus === 'UNAVAILABLE'"
      :on-retry-traffic="traffic.refresh"
    />
    <ConfirmStationDelete
      v-if="deleteCandidate"
      :station="deleteCandidate"
      :saving="deletingStation"
      :on-cancel="() => setDeleteCandidate(null)"
      :on-confirm="handleDeactivate"
    />
    <div
      v-if="toast"
      class="application-toast"
      role="status"
    >
      {{ toast }}
    </div>
  </section>
</template>
