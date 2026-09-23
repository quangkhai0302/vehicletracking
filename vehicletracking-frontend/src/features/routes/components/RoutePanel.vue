<script setup lang="ts">
import { computed, ref } from 'vue';
import { AlertCircle, Clock, MapPin, Milestone, Plus, RefreshCw, Route, Search } from '@lucide/vue';
import type { RouteSummary } from '@/features/routes/types/route';
import { formatDuration } from '@/shared/utils/format';
const props = withDefaults(
  defineProps<{
    createDisabled?: boolean;
    routes: RouteSummary[];
    selectedRouteId: number | null;
    loading: boolean;
    error: string | null;
    onSelectRoute: (route: RouteSummary) => void;
    onBeginCreate: () => void;
    onRetry: () => void;
  }>(),
  { createDisabled: false },
);
const query = ref('');
const filteredRoutes = computed(() => {
  const normalized = query.value.trim().toLocaleLowerCase('vi');
  return normalized
    ? props.routes.filter((route) =>
        [route.name, route.startStationName, route.endStationName].some((value) =>
          value?.toLocaleLowerCase('vi').includes(normalized),
        ),
      )
    : props.routes;
});
const selectKey = (event: KeyboardEvent, route: RouteSummary) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    props.onSelectRoute(route);
  }
};
</script>
<template>
  <aside
    class="route-panel"
    aria-label="Danh sách tuyến đường"
  >
    <div class="route-panel-header">
      <div>
        <div class="panel-eyebrow">Dữ liệu vận hành</div>
        <h2>Danh sách tuyến</h2>
      </div>
      <span
        class="count-badge tabular-numbers"
        title="Số tuyến hiển thị / Tổng số tuyến"
        >{{ filteredRoutes.length }}/{{ routes.length }}</span
      >
    </div>
    <div class="route-panel-actions">
      <div class="route-search-box">
        <Search :size="15" /><input
          v-model="query"
          type="search"
          class="route-search-input"
          placeholder="Tìm theo tên tuyến, trạm..."
          aria-label="Tìm kiếm tuyến đường"
        />
      </div>
      <button
        type="button"
        class="btn-primary-create"
        :disabled="createDisabled"
        title="Tạo tuyến đường mới"
        @click="onBeginCreate"
      >
        <Plus :size="15" /><span>Tạo tuyến</span>
      </button>
    </div>
    <div class="route-list">
      <div
        v-if="loading"
        class="panel-loading"
        role="status"
      >
        <RefreshCw
          :size="18"
          class="animate-spin"
        /><span>Đang tải danh sách tuyến đường...</span>
      </div>
      <div
        v-if="!loading && error"
        class="panel-error"
        role="alert"
      >
        <AlertCircle :size="20" />
        <div class="panel-error-content">
          <strong>Không thể tải dữ liệu</strong>
          <p>{{ error }}</p>
          <button
            type="button"
            class="btn-retry"
            @click="onRetry"
          >
            <RefreshCw :size="14" /> Thử lại
          </button>
        </div>
      </div>
      <div
        v-if="!loading && !error && filteredRoutes.length === 0"
        class="panel-empty"
      >
        <Route
          :size="32"
          class="empty-icon"
        />
        <h3>Chưa có tuyến đường nào</h3>
        <p>
          {{
            query
              ? 'Không tìm thấy tuyến đường phù hợp với từ khóa.'
              : 'Bắt đầu tạo tuyến đường cố định đầu tiên bằng cách chọn danh sách điểm dừng.'
          }}
        </p>
        <button
          v-if="!query"
          type="button"
          class="btn-primary-create"
          :disabled="createDisabled"
          @click="onBeginCreate"
        >
          <Plus :size="15" /> Tạo tuyến ngay
        </button>
      </div>
      <template v-if="!loading && !error"
        ><article
          v-for="route in filteredRoutes"
          :key="route.id"
          :class="`route-card ${route.id === selectedRouteId ? 'selected' : ''}`"
          :tabindex="0"
          role="button"
          :aria-pressed="route.id === selectedRouteId"
          @click="onSelectRoute(route)"
          @keydown="selectKey($event, route)"
        >
          <div class="route-card-header">
            <div class="route-card-name">{{ route.name }}</div>
            <span class="count-badge tabular-numbers">{{ route.stopCount }} trạm</span>
          </div>
          <div class="route-card-endpoints">
            <MapPin
              :size="13"
              class="text-emerald"
            /><span>{{ route.startStationName || 'Điểm đầu' }}</span
            ><span class="text-muted">→</span
            ><MapPin
              :size="13"
              class="text-red"
            /><span>{{ route.endStationName || 'Điểm cuối' }}</span>
          </div>
          <div class="route-card-metrics tabular-numbers">
            <span class="route-metric-item"
              ><Milestone :size="12" /> {{ (route.totalDistanceMeters / 1000).toFixed(1) }} km</span
            ><span class="route-metric-item"
              ><Clock :size="12" /> {{ formatDuration(route.estimatedTripDurationSeconds) }}</span
            ><span class="route-metric-item text-muted">HERE CAR</span>
          </div>
        </article></template
      >
    </div>
  </aside>
</template>
