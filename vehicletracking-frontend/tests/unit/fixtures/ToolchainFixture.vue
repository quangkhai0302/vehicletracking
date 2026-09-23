<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
import { MapPin } from '@lucide/vue';
import L from 'leaflet';
defineProps<{ count: number }>();
const emit = defineEmits<{ select: [count: number] }>();
const container = shallowRef<HTMLElement | null>(null);
const map = shallowRef<L.Map | null>(null);
onMounted(() => { if (container.value) map.value = L.map(container.value).setView([10.77, 106.7], 12); });
onBeforeUnmount(() => { map.value?.remove(); map.value = null; });
</script>
<template>
  <div><button @click="emit('select', count)"><MapPin :size="18" />{{ count }}</button><slot /><div ref="container" /></div>
</template>
