<script setup lang="ts">
import { computed, onMounted, onScopeDispose, onUnmounted, ref, shallowRef, watch } from 'vue';
import L from 'leaflet';
import { Maximize2 } from '@lucide/vue';
import type { RouteDetail } from '@/features/routes/types/route';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import { createRouteStopIcon, stopRoleLabel } from '@/features/map/utils/routeStopPresentation';
import { validLine } from '@/features/routes/utils/routeInspection';

const props = defineProps<{ route: RouteDetail; editing: boolean }>();
const container = shallowRef<HTMLDivElement | null>(null);
const map = shallowRef<L.Map | null>(null);
const tileError = ref(false);
let tiles: L.TileLayer | null = null;
let resizeFrame = 0;
let observer: ResizeObserver | null = null;
const lines = computed(() => {
  try {
    const result = props.route.sections.map((section) =>
      decodeFlexiblePolyline(section.encodedPolyline),
    );
    return result.every(validLine) ? result : [];
  } catch {
    return [];
  }
});
const geometryReady = computed(() => lines.value.length > 0);
const stops = computed(() =>
  props.route.stops.filter(
    (stop) =>
      Number.isFinite(stop.latitude) &&
      Number.isFinite(stop.longitude) &&
      Math.abs(stop.latitude) <= 90 &&
      Math.abs(stop.longitude) <= 180,
  ),
);
const fit = () => {
  if (!map.value) return;
  const bounds = L.latLngBounds([
    ...lines.value.flat(),
    ...stops.value.map((stop) => [stop.latitude, stop.longitude] as [number, number]),
  ]);
  map.value.invalidateSize({ pan: false });
  if (bounds.isValid())
    map.value.fitBounds(bounds, {
      paddingTopLeft: [48, 104],
      paddingBottomRight: [48, 48],
      maxZoom: 16,
      animate: false,
    });
};
const loadTiles = () => {
  if (!map.value) return;
  tiles?.off();
  tiles?.remove();
  tileError.value = false;
  tiles = L.tileLayer('https://{s}.google.com/vt/lyrs=m&hl=vi&gl=VN&x={x}&y={y}&z={z}', {
    subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
    maxZoom: 20,
    attribution: '&copy; Google',
  });
  tiles.on('tileerror', () => {
    tileError.value = true;
  });
  tiles.addTo(map.value);
};
onMounted(() => {
  if (!container.value) return;
  map.value = L.map(container.value, { center: [10.7769, 106.7009], zoom: 13 });
  loadTiles();
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => {
      map.value?.invalidateSize({ pan: false });
    });
  });
  observer.observe(container.value);
});
onScopeDispose(() => {
  observer?.disconnect();
  cancelAnimationFrame(resizeFrame);
});
watch(
  [map, () => props.route, () => props.editing],
  ([instance, _route, editing], _old, cleanup) => {
    if (!instance) return;
    const layer = L.layerGroup().addTo(instance);
    const renderer = L.svg();
    if (!editing) {
      for (const line of lines.value) {
        L.polyline(line, { renderer, color: '#075985', weight: 8, interactive: false }).addTo(
          layer,
        );
        L.polyline(line, { renderer, color: '#0ea5e9', weight: 5, interactive: false }).addTo(
          layer,
        );
      }
    }
    for (const stop of stops.value) {
      const content = document.createElement('div');
      content.className = 'route-map-stop-popup';
      const title = document.createElement('strong'),
        description = document.createElement('p');
      title.textContent = `${stop.sequenceNumber}. ${stop.stationName}`;
      description.textContent = `${stopRoleLabel(stop.role)} · Dừng ${stop.dwellDurationSeconds} giây`;
      content.append(title, description);
      L.marker([stop.latitude, stop.longitude], {
        title: `Trạm ${stop.sequenceNumber}: ${stop.stationName}`,
        keyboard: true,
        icon: createRouteStopIcon(stop.sequenceNumber, stop.role),
      })
        .bindPopup(content)
        .addTo(layer);
    }
    cleanup(() => {
      layer.remove();
      layer.eachLayer((item) => item.off());
      renderer.remove();
    });
  },
  { immediate: true },
);
watch([map, () => props.route, () => props.editing], fit, { flush: 'post' });
// Slot editors dispose their layers before the owning Leaflet instance is removed.
onUnmounted(() => {
  tiles?.off();
  map.value?.remove();
  map.value = null;
});
defineExpose({ geometryReady });
</script>

<template>
  <div
    class="route-map-layout"
    :class="{ 'is-editing': editing }"
  >
    <div class="route-map-view">
      <div
        ref="container"
        class="route-detail-map"
        role="region"
        :aria-label="`Bản đồ tuyến ${route.name}`"
      />
      <button
        type="button"
        class="route-map-fit"
        aria-label="Xem toàn tuyến trên bản đồ"
        @click="fit"
      >
        <Maximize2 :size="15" />Toàn tuyến
      </button>
      <div
        v-if="!geometryReady || tileError"
        class="route-map-notices"
        role="status"
      >
        <p v-if="!geometryReady">
          Chưa hiển thị được đường đi của tuyến. Hãy tải lại chi tiết tuyến.
        </p>
        <p v-if="tileError">
          Chưa tải được bản đồ nền.
          <button
            type="button"
            @click="loadTiles"
          >
            Thử lại bản đồ
          </button>
        </p>
      </div>
    </div>
    <slot :map="map" />
  </div>
</template>

<style scoped>
.route-map-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: 380px;
  height: min(58vh, 620px);
}
.route-map-layout.is-editing {
  grid-template-columns: minmax(0, 1fr) 320px;
}
.route-map-view {
  position: relative;
  min-width: 0;
  min-height: 0;
}
.route-detail-map {
  width: 100%;
  height: 100%;
  background: #edf2f7;
}
.route-map-fit {
  position: absolute;
  z-index: 800;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 9px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #075985;
  font-size: 12px;
  box-shadow: 0 2px 6px #0f172a20;
}
.route-map-notices {
  position: absolute;
  z-index: 800;
  left: 12px;
  right: 12px;
  bottom: 24px;
  padding: 10px;
  background: #fff;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  font-size: 12px;
  color: #92400e;
}
.route-map-notices button {
  color: #0369a1;
  text-decoration: underline;
}
:deep(.leaflet-control-zoom) {
  margin-top: 12px !important;
}
/* The application's global Leaflet theme uses !important; this modal has a light map. */
:deep(.leaflet-control-zoom a),
:deep(.leaflet-popup-content-wrapper),
:deep(.leaflet-popup-tip) {
  background: #fff !important;
  color: #0f172a !important;
  border-color: #e2e8f0 !important;
}
:deep(.leaflet-control-zoom a:hover) {
  background: #f0f9ff !important;
}
:deep(.route-map-stop-popup strong) {
  color: #0f172a;
}
:deep(.route-map-stop-popup p) {
  margin: 6px 0 0;
  color: #64748b;
}
@media (max-width: 700px) {
  .route-map-layout {
    min-height: 340px;
    height: 48vh;
  }
  .route-map-layout.is-editing {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
  }
  .is-editing .route-map-view {
    height: 340px;
  }
}
</style>
