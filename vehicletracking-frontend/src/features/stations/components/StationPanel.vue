<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowUpDown, Edit3, MapPin, Plus, Search, Trash2 } from '@lucide/vue';
import type { Station, StationFormMode } from '@/features/stations/types/station';
const props = withDefaults(
  defineProps<{
    stations: Station[];
    selectedStationId: number | null;
    loading: boolean;
    error: string | null;
    selectionDisabled: boolean;
    mode?: StationFormMode;
    onBeginCreate: () => void;
    onSelect: (station: Station) => void;
    onBeginEdit?: (station: Station) => void;
    onDelete?: (station: Station) => void;
  }>(),
  { mode: 'closed' },
);
const query = ref(''),
  sortBy = ref<'NAME' | 'RADIUS'>('NAME');
const filteredStations = computed(() => {
  const normalized = query.value.trim().toLocaleLowerCase('vi');
  const list = props.stations.filter(
    (station) =>
      !normalized ||
      station.name.toLocaleLowerCase('vi').includes(normalized) ||
      station.address?.toLocaleLowerCase('vi').includes(normalized),
  );
  return list.sort(
    sortBy.value === 'NAME'
      ? (a, b) => a.name.localeCompare(b.name, 'vi')
      : (a, b) => b.checkinRadiusMeters - a.checkinRadiusMeters,
  );
});
const selectKey = (event: KeyboardEvent, station: Station) => {
  if (event.target !== event.currentTarget) return;
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    props.onSelect(station);
  }
};
</script>
<template>
  <aside
    class="station-panel"
    aria-label="Danh sách trạm"
  >
    <div class="station-panel-header">
      <div>
        <div class="panel-eyebrow">Dữ liệu vận hành</div>
        <h2>Danh sách trạm</h2>
      </div>
      <span
        class="count-badge tabular-numbers"
        title="Số trạm hiển thị / Tổng số trạm"
        >{{ filteredStations.length }} / {{ stations.length }}</span
      >
    </div>
    <div class="station-toolbar">
      <label class="station-search flex-1"
        ><Search
          :size="15"
          aria-hidden="true" /><input
          v-model="query"
          placeholder="Tìm theo tên trạm hoặc địa chỉ..."
          aria-label="Tìm kiếm trạm"
      /></label>
      <button
        type="button"
        :class="`station-sort-toggle-btn ${sortBy === 'RADIUS' ? 'active' : ''}`"
        :title="
          sortBy === 'NAME'
            ? 'Đang xếp theo Tên (A-Z). Bấm để xếp theo Bán kính'
            : 'Đang xếp theo Bán kính. Bấm để xếp theo Tên'
        "
        aria-label="Đổi thứ tự sắp xếp"
        @click="sortBy = sortBy === 'NAME' ? 'RADIUS' : 'NAME'"
      >
        <ArrowUpDown :size="14" /><span>{{ sortBy === 'NAME' ? 'Tên A-Z' : 'Bán kính' }}</span>
      </button>
    </div>
    <div
      v-if="error"
      class="station-alert"
      role="alert"
    >
      {{ error }}
    </div>
    <div
      class="station-list"
      aria-live="polite"
    >
      <div
        v-if="loading"
        class="station-empty"
      >
        Đang tải danh sách trạm…
      </div>
      <div
        v-if="!loading && !error && stations.length === 0"
        class="station-empty"
      >
        <MapPin
          :size="26"
          aria-hidden="true"
        /><strong>Chưa có trạm nào</strong
        ><span>Nhấn “Thêm trạm” ở bên dưới để đặt trạm đầu tiên lên bản đồ.</span>
      </div>
      <div
        v-if="!loading && stations.length > 0 && filteredStations.length === 0"
        class="station-empty compact"
      >
        Không tìm thấy trạm phù hợp với tìm kiếm.
      </div>
      <template v-if="!loading"
        ><div
          v-for="station in filteredStations"
          :key="station.id"
          :class="`station-card ${selectedStationId === station.id ? 'selected' : ''}`"
          role="button"
          :tabindex="0"
          :aria-pressed="selectedStationId === station.id"
          @click="onSelect(station)"
          @keydown="selectKey($event, station)"
        >
          <div class="station-card-top">
            <div class="station-card-title-row">
              <span
                class="status-dot active"
                title="Đang hoạt động"
                aria-label="Đang hoạt động"
              /><strong
                class="station-card-name"
                :title="station.name"
                >{{ station.name }}</strong
              >
            </div>
            <div class="station-card-actions">
              <button
                v-if="onBeginEdit"
                type="button"
                class="card-action-btn"
                :disabled="selectionDisabled"
                title="Chỉnh sửa trạm này"
                :aria-label="`Sửa trạm ${station.name}`"
                @click.stop="onBeginEdit(station)"
                @keydown.stop
              >
                <Edit3 :size="13" /></button
              ><button
                v-if="onDelete"
                type="button"
                class="card-action-btn danger"
                :disabled="selectionDisabled"
                title="Ngừng sử dụng trạm này"
                :aria-label="`Ngừng sử dụng trạm ${station.name}`"
                @click.stop="onDelete(station)"
                @keydown.stop
              >
                <Trash2 :size="13" />
              </button>
            </div>
          </div>
          <p
            v-if="station.address"
            class="station-card-address"
            :title="station.address"
          >
            {{ station.address }}
          </p>
          <div class="station-card-body">
            <div class="station-card-meta tabular-numbers">
              <span class="station-meta-coords"
                >{{ station.latitude.toFixed(5) }}, {{ station.longitude.toFixed(5) }}</span
              ><span class="station-meta-radius"
                >Bán kính: {{ station.checkinRadiusMeters }} m</span
              >
            </div>
          </div>
        </div></template
      >
    </div>
    <div class="station-panel-bottom">
      <button
        type="button"
        class="add-station-dashed-btn"
        :disabled="selectionDisabled"
        aria-label="Thêm trạm mới"
        @click="onBeginCreate"
      >
        <Plus :size="16" /> Thêm trạm mới
      </button>
      <div class="station-panel-footer-mode">
        <span class="station-count-summary">{{ stations.length }} trạm trong hệ thống</span
        ><span
          v-if="mode === 'create'"
          class="mode-indicator create"
          >Đang tạo trạm mới</span
        ><span
          v-if="mode === 'edit'"
          class="mode-indicator edit"
          >Đang chỉnh sửa trạm</span
        >
      </div>
    </div>
  </aside>
</template>
