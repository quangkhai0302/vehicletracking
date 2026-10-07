<script setup lang="ts">
import { BusFront, UserRound, Users, MapPin, TriangleAlert } from '@lucide/vue';
import type { ReportSection } from '@/features/reports/types/reportWorkspace';

defineProps<{ modelValue: ReportSection }>();
const emit = defineEmits<{ 'update:modelValue': [section: ReportSection] }>();
const sections = [
  { id: 'vehicles', label: 'Theo xe', icon: BusFront },
  { id: 'drivers', label: 'Theo tài xế', icon: UserRound },
  { id: 'occupancy', label: 'Hành khách', icon: Users },
  { id: 'late-stops', label: 'Trễ trạm', icon: MapPin },
  { id: 'incidents', label: 'Sự cố', icon: TriangleAlert },
] as const;

function navigate(event: KeyboardEvent, index: number) {
  let next: number;
  if (event.key === 'ArrowRight') next = (index + 1) % sections.length;
  else if (event.key === 'ArrowLeft') next = (index + sections.length - 1) % sections.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = sections.length - 1;
  else return;
  event.preventDefault();
  emit('update:modelValue', sections[next]!.id);
  const tab = event.currentTarget as HTMLButtonElement;
  const target = tab.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next];
  target?.focus({ preventScroll: true });
  target?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
}
</script>

<template>
  <div class="report-section-tabs" role="tablist" aria-label="Nội dung báo cáo">
    <button
      v-for="(section, index) in sections"
      :id="`report-tab-${section.id}`"
      :key="section.id"
      type="button"
      role="tab"
      :aria-selected="modelValue === section.id"
      :aria-controls="`report-panel-${section.id}`"
      :tabindex="modelValue === section.id ? 0 : -1"
      @click="emit('update:modelValue', section.id)"
      @keydown="navigate($event, index)"
    >
      <component :is="section.icon" :size="17" aria-hidden="true" />
      {{ section.label }}
    </button>
  </div>
</template>
