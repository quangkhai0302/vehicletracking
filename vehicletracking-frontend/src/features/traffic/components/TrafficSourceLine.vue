<script setup lang="ts">
import { computed } from 'vue';
import type { TrafficEnvelope } from '@/features/traffic/types/traffic';
import { trafficAge } from '@/features/routes/utils/routeInspection';
const props = defineProps<{
  envelope: TrafficEnvelope<unknown>;
  receivedAt: number;
  now: number;
  label: string;
}>();
const age = computed(() => trafficAge(props.envelope, props.receivedAt, props.now));
const stale = computed(
  () =>
    props.envelope.source === 'HERE_LAST_KNOWN' ||
    props.envelope.status === 'STALE' ||
    (age.value !== null && age.value > 90),
);
const ageLabel = computed(() =>
  age.value === null
    ? 'Thời gian cập nhật không rõ'
    : age.value < 60
      ? `Cập nhật ${age.value} giây trước`
      : `Cập nhật ${Math.floor(age.value / 60)} phút trước`,
);
</script>
<template>
  <p
    class="route-inspection-source"
    :data-stale="stale"
  >
    {{ label }}: HERE · {{ stale ? 'Dữ liệu gần nhất' : ageLabel }}
  </p>
</template>
