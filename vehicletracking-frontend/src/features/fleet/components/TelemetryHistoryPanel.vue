<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue';
import { ChevronLeft, ChevronRight, History, RefreshCw } from '@lucide/vue';
import { fetchTelemetryHistory, type TelemetryHistoryQuery } from '@/features/fleet/api/telemetry';
import type { TelemetryPage } from '@/features/fleet/types/telemetry';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
const props = defineProps<{
  tripId: number;
  onFocusPosition: (position: [number, number]) => void;
}>();
const page = shallowRef<TelemetryPage | null>(null),
  source = ref<TelemetryHistoryQuery['source']>();
const from = ref(''),
  to = ref(''),
  pageNumber = ref(0),
  loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
watch(
  [() => props.tripId, source, from, to, pageNumber, attempt],
  (_, _old, cleanup) => {
    const controller = new AbortController();
    const query: TelemetryHistoryQuery = {
      tripId: props.tripId,
      source: source.value,
      page: pageNumber.value,
      size: 20,
    };
    if (from.value) query.from = new Date(from.value).toISOString();
    if (to.value) query.to = new Date(to.value).toISOString();
    fetchTelemetryHistory(query, controller.signal)
      .then((next) => {
        if (!controller.signal.aborted) page.value = next;
      })
      .catch((err) => {
        if (!controller.signal.aborted)
          error.value = err instanceof Error ? err.message : 'Không thể tải lịch sử vị trí.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
function retry() {
  loading.value = true;
  error.value = null;
  attempt.value++;
}
function applyFilter(key: 'source' | 'from' | 'to', event: Event) {
  loading.value = true;
  error.value = null;
  pageNumber.value = 0;
  const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
  if (key === 'source')
    source.value = value ? (value as TelemetryHistoryQuery['source']) : undefined;
  else if (key === 'from') from.value = value;
  else to.value = value;
}
</script>
<template>
  <section
    class="trip-history-panel"
    aria-label="Lịch sử vị trí"
  >
    <div class="trip-section-heading">
      <span><History :size="15" /> Lịch sử vị trí</span
      ><button
        class="fleet-icon-button"
        :disabled="loading"
        aria-label="Tải lại lịch sử"
        @click="retry"
      >
        <RefreshCw :size="14" />
      </button>
    </div>
    <div class="trip-history-filters">
      <select
        aria-label="Nguồn telemetry"
        :value="source ?? ''"
        @change="applyFilter('source', $event)"
      >
        <option value="">Tất cả nguồn</option>
        <option value="GPS">GPS</option>
        <option value="SIMULATOR">Mô phỏng</option></select
      ><label
        >Từ
        <input
          type="datetime-local"
          :value="from"
          @input="applyFilter('from', $event)" /></label
      ><label
        >Đến
        <input
          type="datetime-local"
          :value="to"
          @input="applyFilter('to', $event)"
      /></label>
    </div>
    <div
      v-if="error"
      class="fleet-error"
      role="alert"
    >
      {{ error
      }}<button
        class="fleet-text-button"
        @click="retry"
      >
        Thử lại
      </button>
    </div>
    <p
      v-if="loading"
      class="fleet-loading"
      role="status"
    >
      Đang tải lịch sử…
    </p>
    <p
      v-if="!loading && !error && page?.items.length === 0"
      class="fleet-help"
    >
      Không có mẫu telemetry phù hợp.
    </p>
    <template v-if="!loading && !error && page && page.items.length > 0"
      ><div class="telemetry-history-list">
        <button
          v-for="item in page.items"
          :key="item.id"
          class="telemetry-history-row"
          @click="onFocusPosition([item.latitude, item.longitude])"
        >
          <span
            ><strong>{{ item.source === 'SIMULATOR' ? 'Mô phỏng' : 'GPS' }}</strong> ·
            {{ displayTripTime(item.recordedAt) }}</span
          ><span
            >{{ item.speedKmh.toFixed(1) }} km/h · {{ item.latitude.toFixed(5) }},
            {{ item.longitude.toFixed(5) }}</span
          >
        </button>
      </div>
      <div class="trip-history-pagination">
        <span
          >Trang {{ (page.page ?? pageNumber) + 1 }} / {{ Math.max(page.totalPages, 1) }} ·
          {{ page.totalElements }} mẫu</span
        ><span
          ><button
            class="fleet-icon-button"
            :disabled="pageNumber <= 0 || loading"
            aria-label="Trang trước"
            @click="
              loading = true;
              pageNumber--;
            "
          >
            <ChevronLeft :size="14" /></button
          ><button
            class="fleet-icon-button"
            :disabled="pageNumber + 1 >= page.totalPages || loading"
            aria-label="Trang sau"
            @click="
              loading = true;
              pageNumber++;
            "
          >
            <ChevronRight :size="14" /></button
        ></span></div
    ></template>
  </section>
</template>
