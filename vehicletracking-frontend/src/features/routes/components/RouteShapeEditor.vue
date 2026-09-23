<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import L from 'leaflet';
import type { RouteDetail, RouteShapePoint } from '@/features/routes/types/route';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import { shapeRoute } from '@/features/routes/api/routes';
import { formatDuration } from '@/shared/utils/format';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
const props = defineProps<{
  route: RouteDetail;
  map: L.Map | null;
  onClose: () => void;
  onSaved: (route: RouteDetail) => void;
}>();
const points = shallowRef<RouteShapePoint[]>(props.route.shapingPoints ?? []),
  preview = shallowRef(props.route);
const previewKey = ref(JSON.stringify(points.value)),
  busy = ref(false),
  error = ref<string | null>(null);
const confirmClose = ref(false),
  copy = ref(false);
let alive = true,
  request: AbortController | null = null;
onScopeDispose(() => {
  alive = false;
  request?.abort();
});
const dirty = computed(
  () => JSON.stringify(points.value) !== JSON.stringify(props.route.shapingPoints ?? []),
);
const previewCurrent = computed(() => previewKey.value === JSON.stringify(points.value));
const updatePoints = (update: (current: RouteShapePoint[]) => RouteShapePoint[]) => {
  points.value = update(points.value);
};
watch(
  [() => props.map, preview, () => props.route.stops, points, busy],
  ([map, currentPreview, stops, currentPoints, isBusy], _old, cleanup) => {
    if (!map) return;
    const pane = map.getPane('routeShapeEditor') ?? map.createPane('routeShapeEditor');
    pane.style.zIndex = '620';
    const layer = L.layerGroup().addTo(map);
    const renderer = L.svg({ pane: 'routeShapeEditor' });
    let finishDrag: (() => void) | null = null;
    let suppressClick = false;
    const addPoint = (destinationStopSequence: number, position: L.LatLng) => {
      if (isBusy || currentPoints.length >= 20) return;
      updatePoints((current) => {
        const next = [...current];
        let at = next.findIndex((p) => p.destinationStopSequence > destinationStopSequence);
        if (at < 0) at = next.length;
        next.splice(at, 0, {
          destinationStopSequence,
          latitude: position.lat,
          longitude: position.lng,
        });
        return next;
      });
    };
    for (const section of currentPreview.sections) {
      let line: [number, number][];
      try {
        line = decodeFlexiblePolyline(section.encodedPolyline);
      } catch {
        continue;
      }
      L.polyline(line, { renderer, color: '#174ea6', weight: 9, interactive: false }).addTo(layer);
      const path = L.polyline(line, {
        renderer,
        color: '#4285f4',
        weight: 5,
        bubblingMouseEvents: false,
      }).addTo(layer);
      path.bindTooltip('Bấm để thêm điểm dẫn đường, rồi kéo điểm tới đường muốn đi', {
        sticky: true,
      });
      path.on('click', (event: L.LeafletMouseEvent) => {
        if (!suppressClick) addPoint(section.destinationStopSequence, event.latlng);
        suppressClick = false;
      });
      path.on('mousedown', (event: L.LeafletMouseEvent) => {
        if (isBusy || currentPoints.length >= 20 || event.originalEvent.button !== 0) return;
        const enabled = map.dragging.enabled();
        map.dragging.disable();
        const start = event.containerPoint;
        const ghost = L.circleMarker(event.latlng, {
          pane: 'routeShapeEditor',
          radius: 8,
          color: '#1967d2',
          fillColor: '#fff',
          fillOpacity: 1,
        }).addTo(layer);
        let moved = false;
        const move = (e: MouseEvent) => {
          moved ||= map.mouseEventToContainerPoint(e).distanceTo(start) > 4;
          ghost.setLatLng(map.mouseEventToLatLng(e));
        };
        const cleanup = () => {
          document.removeEventListener('mousemove', move);
          document.removeEventListener('mouseup', up);
          if (enabled) map.dragging.enable();
          ghost.remove();
          finishDrag = null;
        };
        const up = (e: MouseEvent) => {
          cleanup();
          if (moved) {
            suppressClick = true;
            addPoint(section.destinationStopSequence, map.mouseEventToLatLng(e));
          }
        };
        finishDrag?.();
        finishDrag = cleanup;
        document.addEventListener('mousemove', move);
        document.addEventListener('mouseup', up);
      });
    }
    currentPoints.forEach((point, index) => {
      const marker = L.marker([point.latitude, point.longitude], {
        draggable: !isBusy,
        keyboard: true,
        pane: 'routeShapeEditor',
        zIndexOffset: 1000,
        icon: L.divIcon({
          className: 'route-shape-handle',
          html: `<span>${index + 1}</span>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        }),
        title: `Kéo điểm dẫn đường ${index + 1}`,
      }).addTo(layer);
      marker.bindTooltip(`Điểm ${index + 1} · Kéo để đổi đường đi`);
      marker.on('dragend', () => {
        const position = marker.getLatLng();
        updatePoints((current) =>
          current.map((p, i) =>
            i === index ? { ...p, latitude: position.lat, longitude: position.lng } : p,
          ),
        );
      });
    });
    for (const stop of stops) {
      L.circleMarker([stop.latitude, stop.longitude], {
        pane: 'routeShapeEditor',
        radius: 7,
        color: '#fff',
        fillColor: '#00875a',
        fillOpacity: 1,
      })
        .bindTooltip(`Trạm ${stop.sequenceNumber}: ${stop.stationName}`)
        .addTo(layer);
    }
    cleanup(() => {
      finishDrag?.();
      layer.eachLayer((item) => item.off());
      layer.clearLayers();
      layer.remove();
      renderer.remove();
    });
  },
  { immediate: true },
);

const execute = async (action: 'preview' | 'save' | 'copy') => {
  if (busy.value) return;
  const controller = new AbortController();
  request = controller;
  const key = JSON.stringify(points.value);
  busy.value = true;
  error.value = null;
  try {
    const result = await shapeRoute(props.route.id, points.value, action, controller.signal);
    if (!alive) return;
    if (action === 'preview') {
      preview.value = result;
      previewKey.value = key;
    } else props.onSaved(result);
  } catch (err) {
    if (alive && !controller.signal.aborted)
      error.value = err instanceof Error ? err.message : 'Không thể tính lại tuyến.';
  } finally {
    if (alive) busy.value = false;
  }
};
const close = () => {
  if (dirty.value) confirmClose.value = true;
  else props.onClose();
};
const moveUp = (index: number) =>
  updatePoints((current) => {
    const next = [...current];
    [next[index - 1], next[index]] = [next[index], next[index - 1]];
    return next;
  });
</script>
<template>
  <aside
    class="route-drawer"
    aria-label="Chỉnh đường đi trên bản đồ"
  >
    <div class="route-drawer-header">
      <h3>Chỉnh đường đi</h3>
      <button
        type="button"
        class="drawer-close-btn"
        :disabled="busy"
        aria-label="Đóng"
        @click="close"
      >
        ×
      </button>
    </div>
    <div class="route-drawer-body">
      <p>
        Kéo đoạn tuyến màu xanh đến đường muốn đi, hoặc bấm tuyến để thêm điểm trắng rồi kéo điểm.
        Sau đó bấm <strong>Tính lại tuyến</strong>.
      </p>
      <p>Trạm dừng được giữ nguyên. Các điểm dẫn đường chỉ chọn đường xe đi qua.</p>
      <p>
        {{ (preview.totalDistanceMeters / 1000).toFixed(2) }} km ·
        {{ formatDuration(preview.estimatedTripDurationSeconds)
        }}{{ !previewCurrent ? ' · Cần tính lại sau khi kéo điểm' : '' }}
      </p>
      <div
        v-if="error"
        class="route-error-banner"
        role="alert"
      >
        {{ error }}
      </div>
      <ol class="route-shape-points">
        <li
          v-for="(point, index) in points"
          :key="index"
        >
          <span>Điểm {{ index + 1 }} · trước trạm {{ point.destinationStopSequence }}</span>
          <button
            type="button"
            class="btn-secondary"
            :disabled="
              busy ||
              index === 0 ||
              points[index - 1].destinationStopSequence !== point.destinationStopSequence
            "
            :aria-label="`Đưa điểm ${index + 1} lên trước`"
            @click="moveUp(index)"
          >
            ↑
          </button>
          <button
            type="button"
            class="btn-secondary"
            :disabled="busy"
            :aria-label="`Xóa điểm dẫn đường ${index + 1}`"
            @click="updatePoints((current) => current.filter((_, i) => i !== index))"
          >
            Xóa
          </button>
        </li>
      </ol>
      <p v-if="!points.length">Chưa có điểm dẫn đường. Bạn có thể kéo bản đồ để tìm vị trí.</p>
      <button
        type="button"
        class="btn-primary"
        :disabled="busy || previewCurrent"
        @click="execute('preview')"
      >
        {{ busy ? 'Đang tính tuyến…' : 'Tính lại tuyến' }}
      </button>
      <label class="route-shape-copy"
        ><input
          v-model="copy"
          type="checkbox"
          :disabled="busy"
        />Lưu thành tuyến mới</label
      >
      <p class="panel-help">
        Nếu tuyến đã có chuyến đi, hãy lưu thành tuyến mới rồi chọn tuyến đó khi tạo chuyến.
      </p>
    </div>
    <div class="route-drawer-footer">
      <button
        type="button"
        class="btn-secondary"
        :disabled="busy"
        @click="close"
      >
        Hủy</button
      ><button
        type="button"
        class="btn-primary"
        :disabled="busy || !previewCurrent || (!dirty && !copy)"
        @click="execute(copy ? 'copy' : 'save')"
      >
        Lưu tuyến
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirmClose"
      title="Bỏ thay đổi đường đi?"
      message="Các điểm bạn vừa kéo chưa được lưu."
      confirm-label="Bỏ thay đổi"
      :busy="false"
      :error="null"
      :on-close="
        () => {
          confirmClose = false;
        }
      "
      :on-confirm="onClose"
    />
  </aside>
</template>
