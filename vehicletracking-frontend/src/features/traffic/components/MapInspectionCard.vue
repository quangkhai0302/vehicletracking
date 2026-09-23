<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue';
import '@/features/routes/styles/route-inspection.css';
const props = defineProps<{
  x: number;
  y: number;
  pinned: boolean;
  kind: 'route' | 'road';
  onClose: () => void;
}>();
const card = shallowRef<HTMLDivElement | null>(null),
  size = shallowRef({ width: 304, height: 360 });
const viewport = shallowRef({ width: window.innerWidth, height: window.innerHeight });
let observer: ResizeObserver | undefined;
const resize = () => {
  viewport.value = { width: window.innerWidth, height: window.innerHeight };
};
onMounted(() => {
  if (!card.value) return;
  observer = new ResizeObserver((entries) => {
    const rect = entries[0].target.getBoundingClientRect();
    if (size.value.width !== rect.width || size.value.height !== rect.height)
      size.value = { width: rect.width, height: rect.height };
  });
  observer.observe(card.value);
  window.addEventListener('resize', resize);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  window.removeEventListener('resize', resize);
});
const left = computed(() =>
  Math.max(
    8,
    Math.min(
      props.x + 18 + size.value.width > viewport.value.width - 8
        ? props.x - size.value.width - 18
        : props.x + 18,
      viewport.value.width - size.value.width - 8,
    ),
  ),
);
const top = computed(() =>
  Math.max(8, Math.min(props.y + 18, viewport.value.height - size.value.height - 8)),
);
</script>
<template>
  <Teleport to="body"
    ><div
      ref="card"
      class="route-inspection-card"
      :data-pinned="pinned"
      :data-kind="kind"
      :role="pinned ? 'dialog' : 'tooltip'"
      :aria-label="kind === 'route' ? 'Thông tin đoạn đường' : 'Thông tin đường trên bản đồ'"
      :style="{ left: `${left}px`, top: `${top}px` }"
    >
      <div class="route-inspection-heading">
        <span>{{ kind === 'route' ? 'THÔNG TIN TUYẾN' : 'GIAO THÔNG TRÊN ĐƯỜNG' }}</span
        ><button
          v-if="pinned"
          aria-label="Đóng thông tin đoạn đường"
          @click="onClose"
        >
          ×
        </button>
      </div>
      <slot /></div
  ></Teleport>
</template>
