<script setup lang="ts">
import type { MapTheme } from '@/features/map/types/map';
defineProps<{
  theme: MapTheme;
  showTraffic: boolean;
  onToggleTraffic: () => void;
  trafficMessage: string;
  trafficCanRetry: boolean;
  onRetryTraffic: () => void;
}>();
</script>
<template>
  <div
    :class="`gm-traffic-floating-pill ${theme === 'google-dark' ? 'dark' : 'light'}`"
    role="region"
    aria-label="Thông tin giao thông"
  >
    <div
      class="gm-traffic-pill-dropdown"
      :title="trafficMessage"
    >
      <span class="gm-traffic-title">Giao thông theo thời gian thực</span
      ><span class="gm-traffic-caret">▾</span>
    </div>
    <button
      v-if="showTraffic && trafficCanRetry"
      type="button"
      class="traffic-retry"
      @click="onRetryTraffic"
    >
      Thử lại
    </button>
    <div class="gm-traffic-pill-divider" />
    <div class="gm-traffic-pill-legend">
      <span class="gm-legend-tag fast">Nhanh</span>
      <div class="gm-legend-bar-gradient" />
      <span class="gm-legend-tag slow">Chậm</span>
    </div>
    <div class="gm-traffic-pill-divider" />
    <button
      type="button"
      :class="`gm-traffic-toggle-switch ${showTraffic ? 'on' : 'off'}`"
      :aria-pressed="showTraffic"
      aria-label="Giao thông theo thời gian thực"
      :title="showTraffic ? 'Tắt hiển thị lớp giao thông' : 'Bật hiển thị lớp giao thông'"
      @click="onToggleTraffic"
    >
      <span class="gm-switch-track"><span class="gm-switch-thumb" /></span>
    </button>
  </div>
</template>
