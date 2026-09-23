<script setup lang="ts">
import { ref, shallowRef, onMounted, onBeforeUnmount } from 'vue';
import L from 'leaflet';
import { MapPin } from '@lucide/vue';
import TypedCard from './TypedCard.vue';
import 'leaflet/dist/leaflet.css';
const count = ref(1);
const container = ref<HTMLDivElement | null>(null);
const map = shallowRef<L.Map | null>(null);
onMounted(() => {
  if (container.value) map.value = L.map(container.value).setView([10.77, 106.7], 13);
});
onBeforeUnmount(() => { map.value?.remove(); map.value = null; });
</script>
<template>
  <TypedCard :count="count" @increment="count = $event"><MapPin :size="20" /></TypedCard>
  <div ref="container" style="height: 200px"></div>
</template>
