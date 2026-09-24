<script setup lang="ts">
import { Bell, BookOpen, BusFront, Navigation, Play, Radio } from '@lucide/vue';
import type { WorkspaceMode } from '@/shared/types/workspace';
withDefaults(
  defineProps<{
    mode: WorkspaceMode;
    onChange: (mode: WorkspaceMode) => void;
    connectionLabel?: string;
    embedded?: boolean;
    alertCount?: number | null;
    alertsOpen?: boolean;
    onOpenAlerts?: () => void;
  }>(),
  { connectionLabel: 'Chưa có vị trí xe', embedded: false, alertCount: null, alertsOpen: false },
);
</script>
<template>
  <header
    :class="`mode-bar glass-panel${embedded ? ' mode-bar-embedded' : ''}`"
    data-map-edge="top"
  >
    <a
      class="canvas-brand"
      href="#main-map"
      aria-label="Vehicletracking · đến bản đồ"
      ><span class="brand-logo-icon"><Navigation :size="17" /></span
      ><span>vehicle<span>tracking</span><span class="brand-badge">OPS</span></span></a
    >
    <nav aria-label="Chế độ vận hành">
      <button
        :aria-pressed="mode === 'tracking'"
        @click="onChange('tracking')"
      >
        <BusFront :size="16" /><span>Theo dõi</span>
      </button>
      <button
        :aria-pressed="mode === 'simulation'"
        @click="onChange('simulation')"
      >
        <Play :size="16" /><span>Mô phỏng</span>
      </button>
    </nav>
    <a
      class="guide-link"
      href="/huong-dan/index.html"
      target="_blank"
      rel="noreferrer"
      aria-label="Mở hướng dẫn sử dụng"
      title="Hướng dẫn sử dụng"
      ><BookOpen :size="15" /><span>Hướng dẫn</span></a
    >
    <button
      class="mode-alert-trigger"
      type="button"
      :aria-label="
        alertCount !== null && alertCount > 0
          ? `Mở cảnh báo, ${alertCount} chưa đọc`
          : 'Mở thông báo vận hành'
      "
      aria-controls="operations-alert-drawer"
      :aria-expanded="alertsOpen"
      :aria-pressed="alertsOpen"
      @click="onOpenAlerts?.()"
    >
      <Bell :size="15" /><span>Cảnh báo</span
      ><b v-if="alertCount !== null && alertCount > 0">{{
        alertCount > 99 ? '99+' : alertCount
      }}</b>
    </button>
    <span class="connection-state"
      ><span
        class="live-beacon-dot"
        aria-hidden="true"
      /><Radio :size="13" /> <span>{{ connectionLabel }}</span></span
    >
  </header>
</template>
