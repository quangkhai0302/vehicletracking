<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import DriverNavigation from '@/features/fleet/components/DriverNavigation.vue';
const route = useRoute();
const tripId = computed(() => Number(route.params.tripId));
</script>
<template>
  <DriverNavigation
    :key="tripId"
    :trip-id="tripId"
  />
</template>
