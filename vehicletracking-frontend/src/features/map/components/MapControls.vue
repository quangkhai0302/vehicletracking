<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
import { Layers, Minus, Navigation, Plus, Scan } from '@lucide/vue';
import type { MapTheme } from '@/features/map/types/map';
defineProps<{
  theme: MapTheme;
  onThemeChange: (theme: MapTheme) => void;
  onResetCenter: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFit: () => void;
  canFit: boolean;
  showStations: boolean;
  showRoutes: boolean;
  onToggleStations: () => void;
  onToggleRoutes: () => void;
  showTraffic: boolean;
  onToggleTraffic: () => void;
  trafficMessage: string;
  trafficCanRetry: boolean;
  onRetryTraffic: () => void;
}>();
const details = shallowRef<HTMLDetailsElement | null>(null);
const themes = [
  { id: 'google-roadmap', name: 'Đường bộ' },
  { id: 'google-satellite', name: 'Vệ tinh' },
  { id: 'google-dark', name: 'Ban đêm' },
] as const;
const pointerDown = (event: MouseEvent) => {
  if (details.value?.open && !details.value.contains(event.target as Node))
    details.value.open = false;
};
const escape = () => {
  if (details.value) {
    details.value.open = false;
    details.value.querySelector('summary')?.focus();
  }
};
onMounted(() => document.addEventListener('pointerdown', pointerDown));
onBeforeUnmount(() => document.removeEventListener('pointerdown', pointerDown));
</script>
<template>
  <div :class="`gm-layers-widget ${theme === 'google-dark' ? 'dark' : 'light'}`">
    <details
      ref="details"
      class="gm-layers-details"
      @keydown.esc="escape"
    >
      <summary
        class="gm-layers-summary"
        aria-label="Lớp bản đồ"
      >
        <Layers :size="18" /><span>Lớp bản đồ</span>
      </summary>
      <div class="gm-layers-popover">
        <div class="gm-layers-section">
          <span class="gm-section-title">Bản đồ nền</span>
          <div class="gm-basemap-grid">
            <button
              v-for="item in themes"
              :key="item.id"
              type="button"
              :class="`gm-basemap-card ${theme === item.id ? 'active' : ''}`"
              :aria-pressed="theme === item.id"
              @click="onThemeChange(item.id)"
            >
              <span :class="`gm-basemap-thumb ${item.id}`" /><span class="gm-basemap-label">{{
                item.name
              }}</span>
            </button>
          </div>
        </div>
        <div class="gm-layers-divider" />
        <div class="gm-layers-section">
          <span class="gm-section-title">Chi tiết bản đồ</span>
          <div class="gm-overlays-list">
            <label class="gm-overlay-row"
              ><input
                type="checkbox"
                :checked="showStations"
                @change="onToggleStations"
              /><span class="gm-overlay-name">Trạm dừng</span></label
            >
            <label class="gm-overlay-row"
              ><input
                type="checkbox"
                :checked="showRoutes"
                @change="onToggleRoutes"
              /><span class="gm-overlay-name">Tuyến đường</span></label
            >
          </div>
        </div>
      </div>
    </details>
  </div>
  <div
    :class="`gm-control-stack ${theme === 'google-dark' ? 'dark' : 'light'}`"
    aria-label="Điều khiển bản đồ"
  >
    <button
      type="button"
      class="gm-btn-square"
      title="Thu phóng vừa toàn bộ tuyến hoặc các trạm"
      aria-label="Vừa khung lộ trình"
      :disabled="!canFit"
      @click="onFit"
    >
      <Scan :size="18" />
    </button>
    <button
      type="button"
      class="gm-btn-square"
      title="Về trung tâm TP. Hồ Chí Minh"
      aria-label="Về TP. Hồ Chí Minh"
      @click="onResetCenter"
    >
      <Navigation :size="18" />
    </button>
    <div class="gm-zoom-group">
      <button
        type="button"
        class="gm-zoom-btn"
        aria-label="Phóng to bản đồ"
        title="Phóng to"
        @click="onZoomIn"
      >
        <Plus :size="18" />
      </button>
      <div class="gm-zoom-divider" />
      <button
        type="button"
        class="gm-zoom-btn"
        aria-label="Thu nhỏ bản đồ"
        title="Thu nhỏ"
        @click="onZoomOut"
      >
        <Minus :size="18" />
      </button>
    </div>
  </div>
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
      :title="showTraffic ? 'Tắt hiển thị lớp giao thông' : 'Bật hiển thị lớp giao thông'"
      @click="onToggleTraffic"
    >
      <span class="gm-switch-track"><span class="gm-switch-thumb" /></span>
    </button>
  </div>
</template>
