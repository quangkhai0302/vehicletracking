<script setup lang="ts">
import { computed } from 'vue';
import type { TrafficIncidentsResponse } from '@/features/traffic/types/traffic';
import { nearbyIncidents, usableTraffic, type Coordinate } from '@/features/routes/utils/routeInspection';
const props = withDefaults(
  defineProps<{
    point: Coordinate;
    envelope: TrafficIncidentsResponse | null;
    now: number;
    failed?: boolean;
  }>(),
  { failed: false },
);
const severity: Record<string, string> = {
  critical: 'Nghiêm trọng',
  major: 'Đáng chú ý',
  minor: 'Nhẹ',
  low: 'Thấp',
};
const incidents = computed(() =>
  usableTraffic(props.envelope)
    ? nearbyIncidents(props.point, props.envelope!.results, props.now)
    : [],
);
</script>
<template>
  <div class="route-inspection-incidents">
    <strong>Sự cố gần đoạn đang xem (50 m)</strong>
    <p
      v-for="incident in incidents.slice(0, 2)"
      :key="incident.id"
    >
      {{ incident.description || incident.type || 'Sự cố giao thông'
      }}<span
        v-if="incident.criticality"
        class="route-inspection-note"
      >
        · {{ severity[incident.criticality] ?? 'Chưa rõ mức độ' }}</span
      >
    </p>
    <p v-if="incidents.length > 2">Và {{ incidents.length - 2 }} sự cố khác.</p>
    <p v-if="!incidents.length">
      {{
        failed
          ? 'Không tải được thông tin sự cố.'
          : usableTraffic(envelope)
            ? 'Chưa ghi nhận sự cố gần vị trí này.'
            : 'Chưa có dữ liệu sự cố.'
      }}
    </p>
  </div>
</template>
