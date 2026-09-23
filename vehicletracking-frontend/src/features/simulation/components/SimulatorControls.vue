<script setup lang="ts">
import { Pause, Play, RotateCcw, Zap } from '@lucide/vue';
import type { SimulatorMultiplier } from '@/features/fleet/types/vehicle';
defineProps<{
  isRunning: boolean;
  multiplier: SimulatorMultiplier;
  onTogglePlay: () => void;
  onReset: () => void;
  onChangeMultiplier: (multiplier: SimulatorMultiplier) => void;
}>();
const multipliers: SimulatorMultiplier[] = [1, 2, 5, 10];
</script>
<template>
  <div
    class="simulator-controls-bar"
    role="region"
    aria-label="Bộ điều khiển mô phỏng telemetry"
  >
    <div class="simulator-header">
      <div class="simulator-badge">
        <span :class="`simulator-indicator ${isRunning ? 'running' : 'paused'}`" /><span
          class="simulator-tag"
          >MÔ PHỎNG TELEMETRY</span
        >
      </div>
      <button
        type="button"
        class="simulator-reset-btn"
        title="Khởi động lại mô phỏng"
        aria-label="Khởi động lại mô phỏng"
        @click="onReset"
      >
        <RotateCcw :size="13" />
      </button>
    </div>
    <div class="simulator-actions">
      <button
        type="button"
        :class="`simulator-play-btn ${isRunning ? 'active' : ''}`"
        :aria-label="isRunning ? 'Tạm dừng mô phỏng' : 'Bắt đầu mô phỏng'"
        @click="onTogglePlay"
      >
        <template v-if="isRunning"><Pause :size="14" /> Tạm dừng</template
        ><template v-else><Play :size="14" /> Tiếp tục</template>
      </button>
      <div
        class="multiplier-group"
        role="group"
        aria-label="Tốc độ mô phỏng"
      >
        <Zap
          :size="13"
          class="multiplier-icon"
        /><button
          v-for="speed in multipliers"
          :key="speed"
          type="button"
          :class="`multiplier-btn ${multiplier === speed ? 'active' : ''}`"
          :aria-pressed="multiplier === speed"
          :title="`Tốc độ mô phỏng ${speed}x`"
          @click="onChangeMultiplier(speed)"
        >
          {{ speed }}x
        </button>
      </div>
    </div>
  </div>
</template>
