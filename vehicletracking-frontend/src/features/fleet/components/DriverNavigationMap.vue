<script setup lang="ts">
import { computed, onMounted, onUnmounted, onScopeDispose, ref, shallowRef, watch } from 'vue';
import L from 'leaflet';
import { LocateFixed, Route as RouteIcon } from '@lucide/vue';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import {
  createRouteStopIcon,
  createRouteStopPopup,
  updateRouteStopPopup,
} from '@/features/map/utils/routeStopPresentation';
import { driverNavigationPresentation } from '@/features/fleet/utils/driverNavigationPresentation';
import { useVehicleMarkers } from '@/features/tracking/composables/useVehicleMarkers';
import { makeMotionPath } from '@/features/fleet/utils/vehicleMotion';
import type { RouteSection } from '@/features/routes/types/route';
import type { DriverNavigationSnapshot } from '@/features/fleet/types/driverNavigation';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import TrafficMapControl from '@/features/traffic/components/TrafficMapControl.vue';
import TrafficLayer from '@/features/traffic/components/TrafficLayer.vue';
import { useTraffic } from '@/features/traffic/composables/useTraffic';
import 'leaflet/dist/leaflet.css';

const props = defineProps<{
  snapshot: DriverNavigationSnapshot;
  now: number;
  connected: boolean;
  preview: RouteSection[] | null;
}>();
const host = ref<HTMLElement | null>(null),
  following = ref(false),
  error = ref<string | null>(null);
const mapRef = shallowRef<L.Map | null>(null);
const showTraffic = ref(true);
const traffic = useTraffic(mapRef, showTraffic, () => mapRef.value !== null);
const trafficStatus = computed(() => traffic.flow?.status ?? traffic.incidents?.status);
const trafficMessage = computed(() =>
  !showTraffic.value
    ? 'Tạm tắt'
    : traffic.loading
      ? 'Đang tải giao thông…'
      : (traffic.error ??
        (trafficStatus.value === 'UNAVAILABLE'
          ? 'Giao thông chưa khả dụng.'
          : trafficStatus.value === 'STALE'
            ? 'Đang dùng dữ liệu gần nhất.'
            : 'Đang hiển thị giao thông hiện tại.')),
);
let tiles: L.TileLayer | null = null;
const tileUrl = () =>
  `https://{s}.google.com/vt/lyrs=${showTraffic.value ? 'm,traffic' : 'm'}&hl=vi&gl=VN&x={x}&y={y}&z={z}`;
watch(showTraffic, () => tiles?.setUrl(tileUrl()));
const info = computed(() => driverNavigationPresentation(props.snapshot));
let observer: ResizeObserver | null = null;
let routes: L.LayerGroup | null = null,
  stopLayer: L.LayerGroup | null = null,
  previewLayer: L.LayerGroup | null = null;
const stopMarkers = new Map<number, L.Marker>();
const valid = (lat: number, lng: number) =>
  Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
const points = (sections: RouteSection[]) => {
  const segments = sections.map((s) => decodeFlexiblePolyline(s.encodedPolyline));
  if (
    !segments.length ||
    segments.some((segment) => segment.length < 2 || segment.some(([lat, lng]) => !valid(lat, lng)))
  )
    throw new Error('Invalid route geometry');
  return segments;
};
const motionPaths = computed(() => {
  try {
    return new Map([
      [props.snapshot.trip.id, makeMotionPath(points(props.snapshot.route.sections))],
    ]);
  } catch {
    return new Map();
  }
});
const operations = computed<OperationsSnapshot>(() => ({
  serverTime: props.snapshot.serverTime,
  trips: [props.snapshot.trip],
  positions: props.snapshot.position ? [props.snapshot.position] : [],
  simulations: props.snapshot.simulation ? [props.snapshot.simulation] : [],
  checkIns: props.snapshot.checkIns ? [props.snapshot.checkIns] : [],
  notifications: [],
}));
const planned = computed(() => {
  const stop = info.value.stops[0],
    trip = props.snapshot.trip;
  if (
    trip.status !== 'SCHEDULED' ||
    props.snapshot.position ||
    !stop ||
    !valid(stop.latitude, stop.longitude)
  )
    return [];
  return [
    {
      vehicleId: trip.vehicleId,
      tripId: trip.id,
      vehiclePlateNumber: trip.vehiclePlateNumber,
      vehicleType: trip.vehicleType,
      latitude: stop.latitude,
      longitude: stop.longitude,
    },
  ];
});
useVehicleMarkers(() => ({
  mapRef,
  snapshot: operations.value,
  plannedPositions: planned.value,
  // Hold the shared animator when this tab has lost synchronization.
  now: props.connected ? props.now : props.now + 60001,
  visible: true,
  selectedId: props.snapshot.trip.vehicleId,
  following: following.value && !props.preview,
  onSelect: () => center(),
  onFocus: (point) => mapRef.value?.panTo(point, { animate: false }),
  motionPaths: motionPaths.value,
}));
function fitRoute() {
  const map = mapRef.value;
  if (!map) return;
  following.value = false;
  const stops = info.value.stops
    .filter((s) => valid(s.latitude, s.longitude))
    .map((s) => [s.latitude, s.longitude] as [number, number]);
  let all = stops;
  try {
    all = [...points(props.snapshot.route.sections).flat(), ...stops];
  } catch {
    /* Keep stations visible even with broken geometry. */
  }
  if (all.length)
    map.fitBounds(L.latLngBounds(all), {
      paddingTopLeft: [55, 100],
      paddingBottomRight: [65, 55],
      maxZoom: 16,
      animate: false,
    });
}
function drawRoute() {
  if (!routes) return;
  routes.clearLayers();
  error.value = null;
  try {
    for (const segment of points(props.snapshot.route.sections)) {
      L.polyline(segment, {
        pane: 'routePane',
        color: '#1967d2',
        weight: 8,
        opacity: 0.96,
        interactive: false,
      }).addTo(routes);
      L.polyline(segment, {
        pane: 'routePane',
        color: '#4285f4',
        weight: 5,
        interactive: false,
      }).addTo(routes);
    }
  } catch {
    error.value = 'Không thể hiển thị hình học lộ trình. Hãy tải lại hoặc liên hệ điều phối.';
  }
  fitRoute();
  drawPreview();
}
function drawStops() {
  if (!stopLayer) return;
  stopLayer.eachLayer((layer) => layer.off());
  stopLayer.clearLayers();
  stopMarkers.clear();
  info.value.stops.forEach((stop, index) => {
    if (!valid(stop.latitude, stop.longitude)) return;
    const state = info.value.state(stop.sequenceNumber),
      role = info.value.role(index);
    const metadata = props.snapshot.stations?.find((s) => s.id === stop.stationId) ?? stop;
    const marker = L.marker([stop.latitude, stop.longitude], {
      icon: createRouteStopIcon(stop.sequenceNumber, role, state),
      keyboard: true,
      zIndexOffset: 700 + stop.sequenceNumber,
      title: `Trạm ${stop.sequenceNumber}: ${stop.stationName}`,
    });
    const label = document.createElement('span');
    label.textContent = `#${stop.sequenceNumber} · ${stop.stationName}`;
    marker.bindTooltip(label, {
      permanent: true,
      direction: 'right',
      offset: [8, -20],
      opacity: 0.97,
      className: 'operational-stop-label',
    });
    marker.bindPopup(createRouteStopPopup({ ...stop, role }, state, metadata), {
      className: 'simulation-stop-info-popup',
      maxWidth: 320,
      offset: [0, -8],
      autoPanPaddingTopLeft: [12, 105],
      autoPanPaddingBottomRight: [12, 20],
    });
    marker.on('popupopen', () => {
      following.value = false;
    });
    marker.addTo(stopLayer!);
    stopMarkers.set(stop.sequenceNumber, marker);
  });
}
function updateStops() {
  info.value.stops.forEach((stop, index) => {
    const marker = stopMarkers.get(stop.sequenceNumber);
    if (!marker) return;
    const state = info.value.state(stop.sequenceNumber);
    if (!marker.getElement()?.querySelector(`.simulation-${state}`))
      marker.setIcon(createRouteStopIcon(stop.sequenceNumber, info.value.role(index), state));
    updateRouteStopPopup(marker.getPopup()?.getContent(), state);
  });
}
function showStop(sequence: number) {
  const marker = stopMarkers.get(sequence),
    map = mapRef.value;
  if (!marker || !map) return;
  following.value = false;
  map.setView(marker.getLatLng(), 16, { animate: false });
  marker.openPopup();
}
function drawPreview() {
  const map = mapRef.value;
  if (!map || !previewLayer) return;
  previewLayer.clearLayers();
  if (!props.preview) return;
  try {
    const segments = points(props.preview);
    segments.forEach((segment) =>
      L.polyline(segment, {
        pane: 'routePane',
        color: '#f59e0b',
        weight: 6,
        dashArray: '8 6',
        interactive: false,
      }).addTo(previewLayer!),
    );
    following.value = false;
    map.fitBounds(L.latLngBounds(segments.flat()), {
      paddingTopLeft: [55, 100],
      paddingBottomRight: [65, 55],
      maxZoom: 16,
      animate: false,
    });
  } catch {
    error.value = 'Không thể hiển thị đường đang xem thử.';
  }
}
function center() {
  const position = props.snapshot.position ?? planned.value[0];
  if (!position) return;
  following.value = true;
  mapRef.value?.panTo([position.latitude, position.longitude], { animate: false });
}
const resize = () => mapRef.value?.invalidateSize({ animate: false });
watch(() => props.snapshot.route.sections.map((s) => s.encodedPolyline).join('|'), drawRoute, {
  flush: 'post',
});
watch(() => JSON.stringify([props.snapshot.stops, props.snapshot.stations]), drawStops, {
  flush: 'post',
});
watch(
  () => [
    props.snapshot.checkIns?.revision,
    props.snapshot.checkIns?.visits,
    info.value.nextStop?.sequenceNumber,
    props.snapshot.trip.attemptNumber,
  ],
  updateStops,
  { flush: 'post' },
);
watch(() => props.preview, drawPreview, { flush: 'post' });
watch(
  () => props.snapshot.trip.status,
  (status, old) => {
    if (status === 'IN_PROGRESS' && old === 'SCHEDULED') center();
  },
);
onMounted(() => {
  if (!host.value) return;
  const map = L.map(host.value, { zoomControl: false }).setView([10.7769, 106.7009], 14);
  map.createPane('routePane').style.zIndex = '450';
  tiles = L.tileLayer(tileUrl(), {
    subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
    attribution: '&copy; Google Maps',
    maxZoom: 20,
  }).addTo(map);
  L.control.zoom({ position: 'bottomleft' }).addTo(map);
  routes = L.layerGroup().addTo(map);
  stopLayer = L.layerGroup().addTo(map);
  previewLayer = L.layerGroup().addTo(map);
  map.on('dragstart', () => {
    following.value = false;
  });
  mapRef.value = map;
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(resize);
    observer.observe(host.value);
  }
  window.addEventListener('resize', resize);
  drawRoute();
  drawStops();
});
onScopeDispose(() => {
  observer?.disconnect();
  window.removeEventListener('resize', resize);
  stopLayer?.eachLayer((layer) => layer.off());
  stopMarkers.clear();
});
// Dispose child layers and traffic watchers before removing their owning map.
onUnmounted(() => {
  const map = mapRef.value;
  mapRef.value = null;
  tiles = null;
  map?.remove();
});
defineExpose({ showStop, fitRoute });
</script>

<template>
  <div class="driver-map-host">
    <div
      ref="host"
      class="driver-leaflet-map"
      aria-label="Bản đồ lộ trình chuyến được phân công"
    />
    <TrafficLayer
      :map="mapRef"
      :map-ready="!!mapRef"
      :visible="showTraffic"
      :incidents="traffic.incidents"
    />
    <TrafficMapControl
      theme="google-roadmap"
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
    <div class="driver-map-actions">
      <button
        class="driver-map-overview"
        aria-label="Xem toàn tuyến"
        @click="fitRoute"
      >
        <RouteIcon :size="20" />
      </button>
      <button
        class="driver-map-center"
        :class="{ active: following }"
        :aria-pressed="following"
        :disabled="!snapshot.position && !planned.length"
        aria-label="Theo dõi vị trí xe"
        @click="center"
      >
        <LocateFixed :size="21" />
      </button>
    </div>
    <div
      class="driver-map-legend"
      aria-label="Chú giải trạm"
    >
      <span><i data-state="checked-in" />Đã check-in</span
      ><span><i data-state="next" />Kế tiếp</span><span><i data-state="pending" />Chưa tới</span>
    </div>
    <p
      v-if="error"
      class="driver-map-error"
      role="alert"
    >
      {{ error }}
    </p>
  </div>
</template>
