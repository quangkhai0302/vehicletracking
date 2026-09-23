<script setup lang="ts">
defineProps<{ eyebrow: string; title: string; description: string }>();
</script>
<template>
  <header class="page-heading">
    <div class="page-heading-copy">
      <span class="page-eyebrow">{{ eyebrow }}</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div
      v-if="$slots.actions"
      class="page-heading-actions"
    >
      <slot name="actions" />
    </div>
  </header>
</template>
