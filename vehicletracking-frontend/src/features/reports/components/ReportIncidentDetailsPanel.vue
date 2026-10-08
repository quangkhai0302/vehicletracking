<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { X } from '@lucide/vue';
import type { OperationalReportIncidentDetail, OperationalReportIncidentRow } from '@/features/reports/types/reports';
import { incidentLabel, severityLabel, statusLabel } from '@/features/reports/utils/reportLabels';
import SidePanel from '@/shared/components/SidePanel.vue';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import '@/features/reports/styles/report-incident-details.css';

const props = defineProps<{
  group: OperationalReportIncidentRow;
  details: OperationalReportIncidentDetail[];
  from: string;
  to: string;
  vehicleLabel: string;
  driverLabel: string;
}>();
const emit = defineEmits<{ close: [] }>();
const page = ref(1);
const body = ref<HTMLDivElement | null>(null);
const pageSize = 10;
const rows = computed(() => props.details
  .filter(row => row.type === props.group.type && row.severity === props.group.severity)
  .sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt)));
const pageCount = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize)));
const pageRows = computed(() => rows.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(() => props.group, () => { page.value = 1; });
watch(page, () => { if (body.value) body.value.scrollTop = 0; });
const dateLabel = (value: string) => value.split('-').reverse().join('/');
const timestamp = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
}).format(new Date(value));
</script>

<template>
  <SidePanel content-sized class-name="report-incident-details" :label="`Chi tiết ${incidentLabel(group.type)} · ${severityLabel(group.severity)}`" :on-close="() => emit('close')">
    <header class="report-incident-details-heading">
      <div><p>CHI TIẾT SỰ CỐ VÀ CẢNH BÁO</p><h2>{{ incidentLabel(group.type) }}</h2><span>{{ group.count.toLocaleString('vi-VN') }} lần · {{ dateLabel(from) }} – {{ dateLabel(to) }}</span></div>
      <button type="button" aria-label="Đóng chi tiết sự cố" @click="emit('close')"><X :size="19" aria-hidden="true" /></button>
    </header>
    <div class="report-incident-details-scope">
      <span class="report-severity" :class="{ 'is-critical': group.severity === 'CRITICAL' }">{{ severityLabel(group.severity) }}</span>
      <span>{{ vehicleLabel }} · {{ driverLabel }}</span><small>Giờ Việt Nam</small>
    </div>
    <div ref="body" class="report-incident-details-body">
      <p v-if="!rows.length" class="report-incident-details-empty">Chưa có chi tiết cho nhóm sự cố này. Hãy đóng và làm mới báo cáo.</p>
      <ol v-else class="report-incident-list" :start="(page - 1) * pageSize + 1">
        <li v-for="row in pageRows" :key="row.id" class="report-incident-entry">
          <div class="report-incident-entry-heading">
            <div><h3>{{ row.routeName ?? 'Chưa có tên tuyến' }}</h3><p>Chuyến #{{ row.tripId }} · {{ row.vehiclePlateNumber ?? 'Chưa có xe' }}</p><p>Tài xế: {{ row.driverName ?? 'Chưa phân công' }}</p></div>
            <span class="report-incident-status" :class="`is-${row.status.toLowerCase()}`">{{ statusLabel(row.status) }}</span>
          </div>
          <p class="report-incident-entry-time">Thời điểm: <time :datetime="row.occurredAt">{{ timestamp(row.occurredAt) }}</time></p>
          <p class="report-incident-entry-content">{{ row.detail || 'Chưa có nội dung chi tiết.' }}</p>
        </li>
      </ol>
    </div>
    <PaginationControls v-model:page="page" :page-count="pageCount" :total="rows.length" :page-size="pageSize" label="sự cố / cảnh báo" />
  </SidePanel>
</template>
