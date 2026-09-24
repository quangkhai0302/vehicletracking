<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import FleetWorkspace from '@/features/fleet/components/FleetWorkspace.vue';
import type { FleetTab } from '@/features/fleet/composables/useFleetWorkspace';
const props = defineProps<{ tab: FleetTab }>();
const router = useRouter(),
  route = useRoute(),
  toast = ref<string | null>(null);
const initialVehicleFilter = computed(() => {
  const value = Number(
    Array.isArray(route.query.vehicleId) ? route.query.vehicleId[0] : route.query.vehicleId,
  );
  return props.tab === 'trips' && Number.isInteger(value) && value > 0 ? value : null;
});
const initialRouteId = computed(() => {
  const raw = Array.isArray(route.query.routeId) ? route.query.routeId[0] : route.query.routeId;
  const value = Number(raw);
  return raw && Number.isSafeInteger(value) && value > 0 ? value : null;
});
const openTripFromRoute = computed(() => route.query.create === '1' && initialRouteId.value !== null);
watch(toast, (value, _old, cleanup) => {
  if (!value) return;
  const timer = window.setTimeout(() => {
    toast.value = null;
  }, 3200);
  cleanup(() => window.clearTimeout(timer));
});
const operations = () => {
  void router.push('/operations');
};
const simulateTrip = (tripId: number) => {
  void router.push({ path: '/operations', query: { mode: 'simulation', tripId: String(tripId) } });
};
</script>
<template>
  <div class="business-page">
    <section class="business-surface business-management-surface">
      <FleetWorkspace
        :initial-tab="tab"
        :initial-vehicle-filter="initialVehicleFilter"
        :initial-route-id="initialRouteId"
        :open-trip-from-route="openTripFromRoute"
        :on-exit-route-prefill="() => router.replace('/trips')"
        :locked-tab="tab"
        :on-toast="(message) => (toast = message)"
        :on-focus-stop="operations"
        :on-manage-routes="
          () => {
            void router.push('/routes');
          }
        "
        :on-manage-stations="
          () => {
            void router.push('/stations');
          }
        "
        :on-simulate-trip="simulateTrip"
        :on-view-route="(routeId) => router.push({ path: '/routes', query: { routeId: String(routeId) } })"
        :on-focus-vehicle="operations"
        :on-view-vehicle-trips="
          (vehicleId) => {
            void router.push(`/trips?vehicleId=${vehicleId}`);
          }
        "
        :on-clear-vehicle-filter="
          () => {
            void router.replace('/trips');
          }
        "
      />
    </section>
    <div
      v-if="toast"
      class="business-toast"
      role="status"
    >
      {{ toast }}
    </div>
  </div>
</template>
