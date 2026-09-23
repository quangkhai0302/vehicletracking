<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import L from 'leaflet';
import type { RouteDetail, RouteSection } from '@/features/routes/types/route';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import { useRouteTraffic } from '@/features/routes/composables/useRouteTraffic';
import {
  matchFlow,
  project,
  trafficCell,
  usableTraffic,
  validLine,
  type Coordinate,
} from '@/features/routes/utils/routeInspection';
import MapInspectionCard from '@/features/traffic/components/MapInspectionCard.vue';
import NearbyTrafficIncidents from '@/features/traffic/components/NearbyTrafficIncidents.vue';
import TrafficFlowDetails from '@/features/traffic/components/TrafficFlowDetails.vue';
import TrafficSourceLine from '@/features/traffic/components/TrafficSourceLine.vue';
interface InspectedSection {
  section: RouteSection;
  points: Coordinate[];
}
interface Selection {
  route: RouteDetail;
  item: InspectedSection;
  point: Coordinate;
  x: number;
  y: number;
  pinned: boolean;
}
const props = defineProps<{
  map: L.Map | null;
  mapReady: boolean;
  route: RouteDetail | null;
  visible: boolean;
  trafficEnabled: boolean;
}>();
const selection = shallowRef<Selection | null>(null),
  now = ref(Date.now());
const select = (value: Selection | null) => {
  selection.value = value;
  if (value) now.value = Date.now();
};
const prepared = computed(() => {
  try {
    const sections =
      props.route?.sections.map((section) => ({
        section,
        points: decodeFlexiblePolyline(section.encodedPolyline),
      })) ?? [];
    return sections.every((item) => validLine(item.points)) ? sections : [];
  } catch {
    return [];
  }
});
const current = computed(() =>
  props.visible && selection.value?.route === props.route ? selection.value : null,
);
const traffic = useRouteTraffic(() =>
  props.trafficEnabled && current.value ? trafficCell(current.value.point) : null,
);
watch(
  () => !!current.value,
  (open, _old, cleanup) => {
    if (!open) return;
    const timer = window.setInterval(() => {
      now.value = Date.now();
    }, 1000);
    cleanup(() => window.clearInterval(timer));
  },
  { immediate: true },
);
watch(
  [() => props.map, () => props.mapReady, () => props.visible, () => props.route, prepared],
  ([map, mapReady, visible, route, sections], _old, cleanup) => {
    if (!map || !mapReady || !visible || !route || !sections.length) return;
    // A dedicated pane keeps hit targets above the route's Canvas renderer regardless of effect order.
    // Marker panes remain above this pane, so stations/vehicles/incidents retain their own interactions.
    const pane = map.getPane('routeInspectionPane') ?? map.createPane('routeInspectionPane');
    pane.style.zIndex = '460';
    pane.style.pointerEvents = 'none';
    const renderer = L.svg({ pane: 'routeInspectionPane' });
    const layer = L.layerGroup().addTo(map);
    let raf: number | null = null;
    const cleanups: (() => void)[] = [];
    const close = () => {
      if (raf !== null) cancelAnimationFrame(raf);
      raf = null;
      select(null);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close();
    };
    for (const item of sections) {
      const path = L.polyline(item.points, {
        pane: 'routeInspectionPane',
        renderer,
        weight: 18,
        opacity: 0,
        className: 'route-inspection-hit',
        interactive: true,
        bubblingMouseEvents: false,
      }).addTo(layer);
      const show = (point: Coordinate, pinned: boolean, client?: { x: number; y: number }) => {
        const position = project(point, item.points)?.point;
        if (!position) return;
        const pixel = map.latLngToContainerPoint(position),
          rect = map.getContainer().getBoundingClientRect();
        select({
          route,
          item,
          point: position,
          pinned,
          x: client?.x ?? rect.left + pixel.x,
          y: client?.y ?? rect.top + pixel.y,
        });
      };
      const hover = (event: L.LeafletMouseEvent) => {
        if (selection.value?.pinned) return;
        if (raf !== null) cancelAnimationFrame(raf);
        raf = requestAnimationFrame(() => {
          raf = null;
          show([event.latlng.lat, event.latlng.lng], false, {
            x: event.originalEvent.clientX,
            y: event.originalEvent.clientY,
          });
        });
      };
      path.on('mouseover', hover);
      path.on('mousemove', hover);
      path.on('mouseout', () => {
        if (!selection.value?.pinned) close();
      });
      path.on('click', (event: L.LeafletMouseEvent) => {
        if (raf !== null) cancelAnimationFrame(raf);
        raf = null;
        show([event.latlng.lat, event.latlng.lng], true, {
          x: event.originalEvent.clientX,
          y: event.originalEvent.clientY,
        });
      });
      const element = path.getElement();
      if (element) {
        element.setAttribute('tabindex', '0');
        element.setAttribute('role', 'button');
        element.setAttribute(
          'aria-label',
          `Thông tin tuyến ${route.name}, đoạn ${item.section.sectionSequence}`,
        );
        element.setAttribute('data-route-section', String(item.section.sectionSequence));
        const focus = () => {
          if (!selection.value?.pinned)
            show(item.points[Math.floor(item.points.length / 2)], false);
        };
        const blur = () => {
          if (!selection.value?.pinned) close();
        };
        const keydown = (event: Event) => {
          const keyEvent = event as KeyboardEvent;
          if (keyEvent.key === 'Enter' || keyEvent.key === ' ') {
            keyEvent.preventDefault();
            keyEvent.stopPropagation();
            show(item.points[Math.floor(item.points.length / 2)], true);
          }
        };
        element.addEventListener('focus', focus);
        element.addEventListener('blur', blur);
        element.addEventListener('keydown', keydown);
        cleanups.push(() => {
          element.removeEventListener('focus', focus);
          element.removeEventListener('blur', blur);
          element.removeEventListener('keydown', keydown);
        });
      }
      cleanups.push(() => path.off());
    }
    map.on('movestart zoomstart click', close);
    window.addEventListener('keydown', escape);
    cleanup(() => {
      if (raf !== null) cancelAnimationFrame(raf);
      cleanups.forEach((dispose) => dispose());
      map.off('movestart zoomstart click', close);
      window.removeEventListener('keydown', escape);
      layer.remove();
      renderer.remove();
      select(null);
    });
  },
  { immediate: true },
);

const flow = computed(() =>
  props.trafficEnabled && current.value && traffic.data && usableTraffic(traffic.data.flow)
    ? matchFlow(current.value.point, current.value.item.points, traffic.data.flow!.results)
    : null,
);
const leg = computed(() => {
  if (!current.value) return { from: '', to: '' };
  const destination = current.value.route.stops.findIndex(
    (stop) => stop.sequenceNumber === current.value!.item.section.destinationStopSequence,
  );
  return {
    from: current.value.route.stops[destination - 1]?.stationName ?? 'Đầu chặng',
    to: current.value.route.stops[destination]?.stationName ?? 'Cuối chặng',
  };
});
</script>
<template>
  <MapInspectionCard
    v-if="current"
    :x="current.x"
    :y="current.y"
    :pinned="current.pinned"
    kind="route"
    :on-close="() => select(null)"
  >
    <strong class="route-inspection-name">{{ current.route.name }}</strong>
    <p class="route-inspection-leg">
      <span>Đoạn đang xem:</span> {{ leg.from }} → {{ leg.to }} · Đoạn
      {{ current.item.section.sectionSequence }}
    </p>
    <p
      v-if="!trafficEnabled"
      class="route-inspection-empty"
    >
      Bật lớp Giao thông để xem dữ liệu tại vị trí này.
    </p>
    <p
      v-else-if="!flow"
      class="route-inspection-empty"
      role="status"
    >
      {{
        traffic.loading
          ? 'Đang tải giao thông tại vị trí này…'
          : traffic.data?.flowError
            ? 'Không tải được dữ liệu giao thông.'
            : !usableTraffic(traffic.data?.flow ?? null)
              ? 'Chưa có dữ liệu giao thông.'
              : 'Chưa có dữ liệu khớp vị trí và hướng tuyến.'
      }}
    </p>
    <TrafficFlowDetails
      v-else
      :flow="flow"
    />
    <TrafficSourceLine
      v-if="trafficEnabled && traffic.data?.flow && usableTraffic(traffic.data.flow)"
      label="Nguồn giao thông"
      :envelope="traffic.data.flow"
      :received-at="traffic.data.receivedAt"
      :now="now"
    />
    <NearbyTrafficIncidents
      v-if="trafficEnabled && traffic.data"
      :point="current.point"
      :envelope="traffic.data.incidents"
      :now="now"
      :failed="traffic.data.incidentError"
    />
    <button
      v-if="
        trafficEnabled && current.pinned && (traffic.data?.flowError || traffic.data?.incidentError)
      "
      class="route-inspection-retry"
      :disabled="traffic.loading"
      @click="traffic.retry"
    >
      Thử tải lại
    </button>
  </MapInspectionCard>
</template>
