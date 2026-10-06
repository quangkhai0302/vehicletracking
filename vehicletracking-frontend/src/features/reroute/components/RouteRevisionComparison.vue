<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import L from 'leaflet';
import { ArrowLeft, LocateFixed, RefreshCw } from '@lucide/vue';
import { useRouteComparison } from '../composables/useRouteComparison';
import { comparePaths, comparisonBounds, decodeComparisonPaths } from '../utils/comparisonGeometry';
import type { ComparisonMode } from '../types/comparison';
import type { Coordinate } from '@/features/routes/utils/routeInspection';
import '../styles/comparison.css';
const props = defineProps<{
  map: L.Map | null;
  mapReady: boolean;
  tripId: number;
  revisionId: number;
  visible: boolean;
  onFit: (bounds: L.LatLngBounds) => void;
  onClose: () => void;
}>();
const { data, loading, error, retry } = useRouteComparison(
  () => props.tripId,
  () => props.revisionId,
);
const mode = ref<ComparisonMode>('compare');
const prepared = computed(() => {
  if (!data.value) return null;
  try {
    const before = data.value.before
      ? decodeComparisonPaths(data.value.before.encodedPolylines)
      : [];
    const after = data.value.after ? decodeComparisonPaths(data.value.after.encodedPolylines) : [];
    return { before, after, difference: comparePaths(before, after), error: null };
  } catch {
    return {
      before: [],
      after: [],
      difference: { beforeChanged: [], afterChanged: [], common: [] },
      error: 'Không hiển thị được hình dạng đường đã lưu của lần thay đổi này.',
    };
  }
});
watch(data, (value) => {
  mode.value = value?.before ? 'compare' : 'after';
});
const canCompare = computed(
  () => !!data.value?.before && !!data.value.after && !prepared.value?.error,
);
const changedBounds = computed(() => {
  const paths = prepared.value;
  if (!paths || paths.error) return null;
  const changed = [...paths.difference.beforeChanged, ...paths.difference.afterChanged];
  return comparisonBounds(
    changed.length ? changed : [...paths.before, ...paths.after],
    data.value?.anchor,
  );
});
const fit = () => {
  const paths = prepared.value;
  const bounds =
    mode.value === 'compare'
      ? changedBounds.value
      : comparisonBounds(
          mode.value === 'before' ? (paths?.before ?? []) : (paths?.after ?? []),
          data.value?.anchor,
        );
  if (bounds) props.onFit(L.latLngBounds(bounds));
};
defineExpose({ fit });
watch(
  [data, () => props.mapReady],
  async ([value, ready]) => {
    if (value && ready) {
      await nextTick();
      fit();
    }
  },
  { flush: 'post', immediate: true },
);
watch(
  [() => props.map, () => props.mapReady, () => props.visible, prepared, mode],
  ([map, ready, visible, paths, selectedMode], _previous, cleanup) => {
    if (!map || !ready || !visible || !paths || paths.error) return;
    const pane = map.getPane('routeComparisonPane') ?? map.createPane('routeComparisonPane');
    pane.style.zIndex = '460';
    pane.style.pointerEvents = 'none';
    const renderer = L.svg({ pane: 'routeComparisonPane' });
    const layer = L.layerGroup().addTo(map);
    const draw = (lines: Coordinate[][], kind: 'before' | 'after' | 'common') => {
      for (const points of lines)
        L.polyline(points, {
          renderer,
          pane: 'routeComparisonPane',
          interactive: false,
          color: kind === 'before' ? '#c56814' : kind === 'after' ? '#007fbd' : '#8895a5',
          weight: kind === 'common' ? 4 : 6,
          opacity: 1,
          dashArray: kind === 'before' ? '9 7' : undefined,
          className: `route-comparison-path route-comparison-${kind}`,
        }).addTo(layer);
    };
    if (selectedMode === 'compare') {
      draw(paths.difference.common, 'common');
      draw(paths.difference.beforeChanged, 'before');
      draw(paths.difference.afterChanged, 'after');
    } else draw(selectedMode === 'before' ? paths.before : paths.after, selectedMode);
    if (data.value?.anchor)
      L.circleMarker([data.value.anchor.latitude, data.value.anchor.longitude], {
        renderer,
        pane: 'routeComparisonPane',
        radius: 7,
        color: '#fff',
        weight: 3,
        fillColor: '#263a51',
        fillOpacity: 1,
        interactive: false,
        className: 'route-comparison-anchor',
      })
        .addTo(layer)
        .bindTooltip('Điểm đổi tuyến', { permanent: true, direction: 'top' });
    cleanup(() => {
      layer.remove();
      renderer.remove();
    });
  },
  { immediate: true, flush: 'post' },
);
const date = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value));
const distance = (meters: number) =>
  `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(meters / 1000)} km`;
const duration = (seconds: number | null) =>
  seconds == null ? 'Chưa có ước tính' : `${Math.round(seconds / 60)} phút`;
</script>
<template>
  <aside
    class="route-comparison-panel"
    data-map-edge="left"
    aria-label="So sánh đổi tuyến"
  >
    <div class="route-comparison-heading">
      <span>LỊCH SỬ ĐỔI TUYẾN</span>
      <h2>Đường đi đã thay đổi thế nào?</h2>
      <p>
        Chuyến #{{ tripId }}<template v-if="data"> · Lần đổi #{{ data.revisionNumber }}</template>
      </p>
    </div>
    <div
      class="route-comparison-body"
      aria-live="polite"
      :aria-busy="loading"
    >
      <p
        v-if="loading"
        role="status"
      >
        Đang tải đường trước và sau thay đổi…
      </p>
      <div
        v-else-if="error"
        class="route-comparison-warning"
        role="alert"
      >
        <p>{{ error }}</p>
        <button
          type="button"
          @click="retry"
        >
          <RefreshCw :size="14" />Thử lại
        </button>
      </div>
      <template v-else-if="data">
        <p
          v-if="!visible"
          class="route-comparison-warning"
          role="status"
        >
          Đường đi đang ẩn. Bật “Tuyến đường” trong Lớp bản đồ để xem lại.
        </p>
        <time>{{ date(data.createdAt) }}</time>
        <p class="route-comparison-reason">
          {{ data.reason || 'Đường đi của chuyến đã được cập nhật.' }}
        </p>
        <p
          v-if="data.message || prepared?.error"
          class="route-comparison-warning"
          role="status"
        >
          {{ prepared?.error || data.message }}
        </p>
        <div
          class="route-comparison-modes"
          role="group"
          aria-label="Đường hiển thị"
        >
          <button
            type="button"
            :disabled="!canCompare"
            :aria-pressed="mode === 'compare'"
            @click="mode = 'compare'"
          >
            So sánh
          </button>
          <button
            type="button"
            :disabled="!canCompare"
            :aria-pressed="mode === 'before'"
            @click="mode = 'before'"
          >
            Trước thay đổi
          </button>
          <button
            type="button"
            :disabled="!data.after || !!prepared?.error"
            :aria-pressed="mode === 'after'"
            @click="mode = 'after'"
          >
            Sau thay đổi
          </button>
        </div>
        <div
          class="route-comparison-legend"
          aria-label="Chú giải đường đi"
        >
          <span v-if="mode !== 'after'"><i class="before" />Trước</span>
          <span v-if="mode !== 'before'"><i class="after" />Sau</span>
          <span v-if="mode === 'compare'"><i class="common" />Đoạn chung</span>
        </div>
        <div class="route-comparison-metrics">
          <div v-if="data.before">
            <span>Trước thay đổi</span><strong>{{ distance(data.before.distanceMeters) }}</strong
            ><small>{{ duration(data.before.durationSeconds) }}</small>
          </div>
          <div v-if="data.after">
            <span>Sau thay đổi</span><strong>{{ distance(data.after.distanceMeters) }}</strong
            ><small>{{ duration(data.after.durationSeconds) }}</small>
          </div>
        </div>
        <p class="route-comparison-note">
          Quãng đường còn lại và thời gian ước tính tại lúc đổi tuyến. Đây là lịch sử, không phải vị
          trí xe hiện tại.
        </p>
        <button
          type="button"
          class="route-comparison-fit"
          :disabled="!changedBounds"
          @click="fit"
        >
          <LocateFixed :size="15" />Xem vùng thay đổi
        </button>
      </template>
    </div>
    <button
      type="button"
      class="route-comparison-close"
      @click="onClose"
    >
      <ArrowLeft :size="16" />Về giám sát trực tiếp
    </button>
  </aside>
</template>
