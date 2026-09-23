<script setup lang="ts">
import { computed } from 'vue';
import type { TrafficFlowSegment } from '@/features/map/types/map';
import { segmentMetrics } from '@/features/routes/utils/routeInspection';
import { inspectionDuration } from '@/features/routes/utils/inspectionFormat';
const props = withDefaults(defineProps<{ flow: TrafficFlowSegment; showName?: boolean }>(), {
  showName: true,
});
const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const metrics = computed(() => segmentMetrics(props.flow));
const status = computed(() =>
  metrics.value.blocked
    ? 'Đóng đường'
    : props.flow.speedKmh === 0
      ? 'Dòng xe đang dừng'
      : Number.isFinite(props.flow.jamFactor)
        ? props.flow.jamFactor >= 8
          ? 'Ùn tắc'
          : props.flow.jamFactor >= 4
            ? 'Di chuyển chậm'
            : 'Thông thoáng'
        : 'Chưa rõ tình trạng',
);
const tone = computed(() =>
  metrics.value.blocked || props.flow.speedKmh === 0 || props.flow.jamFactor >= 8
    ? 'danger'
    : props.flow.jamFactor >= 4
      ? 'warning'
      : 'normal',
);
</script>
<template>
  <div class="route-inspection-status-row">
    <span>Tình trạng giao thông</span
    ><strong
      class="route-inspection-status"
      :data-tone="tone"
      >{{ status }}</strong
    >
  </div>
  <div
    v-if="showName && flow.description"
    class="route-inspection-road"
  >
    <span>Tên đường trên đoạn này</span><strong>{{ flow.description }}</strong>
  </div>
  <dl class="route-inspection-metrics">
    <div>
      <dt>Tốc độ hiện tại</dt>
      <dd>{{ number.format(flow.speedKmh) }} km/h</dd>
    </div>
    <div>
      <dt>Thời gian qua đoạn</dt>
      <dd>{{ metrics.blocked ? 'Đang bị chặn' : inspectionDuration(metrics.travel) }}</dd>
    </div>
    <div v-if="metrics.delay != null && metrics.delay > 0">
      <dt>Chậm hơn bình thường</dt>
      <dd>+{{ inspectionDuration(metrics.delay) }}</dd>
    </div>
  </dl>
</template>
