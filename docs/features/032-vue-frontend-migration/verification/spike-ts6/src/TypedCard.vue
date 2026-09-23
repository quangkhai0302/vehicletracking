<script setup lang="ts">
defineProps<{ count: number }>();
defineEmits<{ increment: [value: number] }>();
</script>
<template><button @click="$emit('increment', count + 1)"><slot />{{ count.toFixed(0) }}</button></template>
