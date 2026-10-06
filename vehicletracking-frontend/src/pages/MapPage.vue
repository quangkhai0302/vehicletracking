<script setup lang="ts">
import { computed, defineAsyncComponent, defineComponent, h } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { WorkspaceMode } from '@/shared/types/workspace';
const props = defineProps<{ workspace: 'tracking' | 'routes' | 'stations'; mapKey: string }>();
const route = useRoute(),
  router = useRouter();
const MapComponent = defineAsyncComponent({
  loader: () => import('@/features/map/components/MapComponent.vue'),
  delay: 0,
  loadingComponent: defineComponent({
    setup: () => () =>
      h('div', { class: 'business-map-loading', role: 'status' }, 'Đang tải không gian bản đồ…'),
  }),
});
const requestedWorkspace = computed(() =>
  props.workspace === 'tracking' &&
  (Array.isArray(route.query.mode) ? route.query.mode[0] : route.query.mode) === 'simulation'
    ? 'simulation'
    : props.workspace,
);
const requestedTripId = computed(() => {
  const value =
    props.workspace === 'tracking'
      ? Number(Array.isArray(route.query.tripId) ? route.query.tripId[0] : route.query.tripId)
      : NaN;
  return Number.isSafeInteger(value) && value > 0 ? value : null;
});
const requestedRevisionId = computed(() => {
  const value =
    requestedWorkspace.value === 'tracking' && requestedTripId.value
      ? Number(
          Array.isArray(route.query.revisionId)
            ? route.query.revisionId[0]
            : route.query.revisionId,
        )
      : NaN;
  return Number.isSafeInteger(value) && value > 0 ? value : null;
});
const closeComparison = () => {
  const query = { ...route.query };
  delete query.revisionId;
  void router.replace({ path: '/operations', query });
};
const changeWorkspace = (mode: WorkspaceMode) => {
  if (mode === 'routes' && route.path !== '/routes') void router.push('/routes');
  else if (mode === 'stations' && route.path !== '/stations') void router.push('/stations');
  else if (mode === 'tracking' || mode === 'simulation') {
    const target =
      mode === 'simulation'
        ? `/operations?mode=simulation${requestedTripId.value ? `&tripId=${requestedTripId.value}` : ''}`
        : '/operations';
    if (
      route.path +
        (route.fullPath.includes('?') ? '?' + route.fullPath.split('?')[1].split('#')[0] : '') !==
      target
    )
      void router.push(target);
  }
};
</script>
<template>
  <div class="business-map-page">
    <MapComponent
      :key="mapKey"
      embedded
      :initial-workspace="requestedWorkspace"
      :initial-trip-id="requestedTripId"
      :initial-revision-id="requestedRevisionId"
      :on-comparison-close="closeComparison"
      :on-workspace-change="changeWorkspace"
    />
  </div>
</template>
