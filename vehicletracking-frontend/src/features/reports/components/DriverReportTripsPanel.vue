<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { BusFront, X } from '@lucide/vue';
import { TRIP_STATUS_LABELS } from '@/features/fleet/types/fleet';
import type { OperationalReportDriverRow } from '@/features/reports/types/reports';
import SidePanel from '@/shared/components/SidePanel.vue';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import '@/features/reports/styles/driver-report-trips.css';

const props = defineProps<{ driver: OperationalReportDriverRow; from: string; to: string; vehicleLabel: string }>();
const emit = defineEmits<{ close: [] }>();
const page = ref(1);
const body = ref<HTMLDivElement | null>(null);
const pageSize = 10;
const trips = computed(() => props.driver.trips ?? []);
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
  <SidePanel class-name="driver-report-trips" :label="`Chuyến của ${driver.driverName ?? 'tài xế chưa xác định'}`" :on-close="() => emit('close')">
    <header class="driver-report-trips-heading">
      <div>
        <p>CHI TIẾT CHUYẾN ĐÃ CHẠY</p>
        <h2>{{ driver.driverName ?? 'Tài xế chưa xác định' }}</h2>
        <span>{{ driver.tripCount.toLocaleString('vi-VN') }} chuyến · {{ dateLabel(from) }} – {{ dateLabel(to) }}</span>
      </div>
      <button type="button" aria-label="Đóng danh sách chuyến" @click="emit('close')"><X :size="19" aria-hidden="true" /></button>
    </header>
    <div class="driver-report-trips-scope"><span><BusFront :size="15" aria-hidden="true" /> {{ vehicleLabel }}</span><small>Kỳ theo ngày khởi hành dự kiến · Giờ Việt Nam</small></div>
    <div ref="body" class="driver-report-trips-body">
      <p v-if="!trips.length" class="driver-report-trips-empty">{{ driver.tripCount > 0 ? 'Chưa có danh sách chuyến cho báo cáo này. Hãy đóng và làm mới báo cáo.' : 'Không có chuyến đã chạy trong phạm vi đã chọn.' }}</p>
      <ol v-else class="driver-report-trip-list" :start="(page - 1) * pageSize + 1">
        <li v-for="trip in pageTrips" :key="trip.tripId" class="driver-report-trip">
          <div class="driver-report-trip-heading">
            <div><h3>{{ trip.routeName ?? 'Chưa có tên tuyến' }}</h3><p>Chuyến #{{ trip.tripId }} · {{ trip.vehiclePlateNumber ?? 'Chưa có biển số' }}</p></div>
            <span class="driver-report-trip-status" :class="`is-${trip.status.toLowerCase()}`">{{ TRIP_STATUS_LABELS[trip.status] ?? 'Chưa xác định' }}</span>
          </div>
          <dl class="driver-report-trip-times">
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
