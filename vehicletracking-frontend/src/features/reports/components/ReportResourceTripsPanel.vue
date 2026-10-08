<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { BusFront, UserRound, X } from '@lucide/vue';
import { TRIP_STATUS_LABELS } from '@/features/fleet/types/fleet';
import type { ReportTripSelection } from '@/features/reports/types/reportWorkspace';
import SidePanel from '@/shared/components/SidePanel.vue';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import '@/features/reports/styles/report-resource-trips.css';

const props = defineProps<{ selection: ReportTripSelection; from: string; to: string; scopeLabel: string }>();
const title = computed(() => props.selection.resource === 'vehicle'
  ? `Xe ${[props.selection.row.plateNumber, props.selection.row.vehicleName].filter(Boolean).join(' · ') || 'chưa xác định'}`
  : props.selection.row.driverName ?? 'Tài xế chưa xác định');
const dialogLabel = computed(() => props.selection.resource === 'vehicle'
  ? `Chuyến của xe ${props.selection.row.plateNumber ?? props.selection.row.vehicleName ?? 'chưa xác định'}`
  : `Chuyến của ${props.selection.row.driverName ?? 'tài xế chưa xác định'}`);
const emit = defineEmits<{ close: [] }>();
const page = ref(1);
const body = ref<HTMLDivElement | null>(null);
const pageSize = 10;
const trips = computed(() => props.selection.row.trips ?? []);
const pageCount = computed(() => Math.max(1, Math.ceil(trips.value.length / pageSize)));
const pageTrips = computed(() => trips.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(page, () => { if (body.value) body.value.scrollTop = 0; });
const dateLabel = (value: string) => value.split('-').reverse().join('/');
function timestamp(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value));
}
</script>

<template>
  <SidePanel class-name="report-resource-trips" :label="dialogLabel" :on-close="() => emit('close')">
    <header class="report-resource-trips-heading">
      <div>
        <p>CHI TIẾT CHUYẾN ĐÃ CHẠY</p>
        <h2>{{ title }}</h2>
        <span>{{ selection.row.tripCount.toLocaleString('vi-VN') }} chuyến · {{ dateLabel(from) }} – {{ dateLabel(to) }}</span>
      </div>
      <button type="button" aria-label="Đóng danh sách chuyến" @click="emit('close')"><X :size="19" aria-hidden="true" /></button>
    </header>
    <div class="report-resource-trips-scope"><span><UserRound v-if="selection.resource === 'vehicle'" :size="15" aria-hidden="true" /><BusFront v-else :size="15" aria-hidden="true" /> {{ scopeLabel }}</span><small>Kỳ theo ngày khởi hành dự kiến · Giờ Việt Nam</small></div>
    <div ref="body" class="report-resource-trips-body">
      <p v-if="!trips.length" class="report-resource-trips-empty">{{ selection.row.tripCount > 0 ? 'Chưa có danh sách chuyến cho báo cáo này. Hãy đóng và làm mới báo cáo.' : 'Không có chuyến đã chạy trong phạm vi đã chọn.' }}</p>
      <ol v-else class="report-resource-trip-list" :start="(page - 1) * pageSize + 1">
        <li v-for="trip in pageTrips" :key="trip.tripId" class="report-resource-trip">
          <div class="report-resource-trip-heading">
            <div><h3>{{ trip.routeName ?? 'Chưa có tên tuyến' }}</h3><p>Chuyến #{{ trip.tripId }} · <template v-if="'driverName' in trip">Tài xế: {{ trip.driverName ?? 'Chưa phân công' }}</template><template v-else>{{ trip.vehiclePlateNumber ?? 'Chưa có biển số' }}</template></p></div>
            <span class="report-resource-trip-status" :class="`is-${trip.status.toLowerCase()}`">{{ TRIP_STATUS_LABELS[trip.status] ?? 'Chưa xác định' }}</span>
          </div>
          <dl class="report-resource-trip-times">
            <div><dt>Khởi hành dự kiến</dt><dd><time :datetime="trip.scheduledDepartureAt">{{ timestamp(trip.scheduledDepartureAt) }}</time></dd></div>
            <div><dt>Bắt đầu</dt><dd><time :datetime="trip.startedAt">{{ timestamp(trip.startedAt) }}</time></dd></div>
            <div><dt>{{ trip.status === 'CANCELLED' ? 'Hủy chuyến' : 'Kết thúc' }}</dt><dd><time v-if="trip.endedAt" :datetime="trip.endedAt">{{ timestamp(trip.endedAt) }}</time><span v-else>{{ trip.status === 'IN_PROGRESS' ? 'Đang thực hiện' : 'Chưa ghi nhận' }}</span></dd></div>
          </dl>
        </li>
      </ol>
    </div>
    <PaginationControls v-model:page="page" :page-count="pageCount" :total="trips.length" :page-size="pageSize" label="chuyến" />
  </SidePanel>
</template>
