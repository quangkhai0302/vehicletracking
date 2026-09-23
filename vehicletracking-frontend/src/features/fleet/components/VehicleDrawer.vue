<script setup lang="ts">
import { computed } from 'vue';
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Compass,
  MapPin,
  Maximize2,
  Navigation,
  Phone,
  User,
  X,
} from '@lucide/vue';
import type { Vehicle } from '@/features/fleet/types/vehicle';
const props = defineProps<{
  vehicle: Vehicle | null;
  following: boolean;
  onToggleFollow: () => void;
  onFitRoute: () => void;
  onClose: () => void;
}>();
const isDelayed = computed(() => props.vehicle?.status === 'DELAYED');
</script>

<template>
  <aside
    v-if="vehicle"
    class="vehicle-drawer"
    aria-label="Chi tiết phương tiện"
  >
    <div class="drawer-header">
      <div>
        <div class="drawer-eyebrow-row">
          <span class="panel-eyebrow">Giám sát xe trực tuyến</span
          ><span :class="['vehicle-status-pill', isDelayed ? 'delayed' : 'running']"
            ><span class="status-dot-pulse" />{{
              isDelayed ? 'Trễ lịch trình' : 'Đang vận hành'
            }}</span
          >
        </div>
        <h2>{{ vehicle.plateNumber }}</h2>
        <div class="vehicle-sub-header">
          <span>{{ vehicle.model }}</span
          ><span class="bullet-sep">•</span><span>{{ vehicle.routeName }}</span>
        </div>
      </div>
      <button
        type="button"
        class="icon-action"
        aria-label="Đóng panel chi tiết xe"
        title="Đóng"
        @click="onClose"
      >
        <X :size="18" />
      </button>
    </div>
    <div
      v-if="isDelayed"
      class="station-alert drawer-alert warning"
      role="alert"
    >
      <AlertTriangle :size="15" /><span
        >Phương tiện đang di chuyển chậm hơn dự kiến 4 phút do mật độ giao thông.</span
      >
    </div>
    <div class="drawer-content-scroll">
      <section class="speedometer-card">
        <div class="speedometer-main">
          <div class="speed-display">
            <span class="speed-number tabular-numbers">{{ vehicle.speedKmh }}</span
            ><span class="speed-unit">km/h</span>
          </div>
          <div class="speed-meta">
            <span class="speed-label">VẬN TỐC TỨC THỜI</span>
            <div class="speed-comparison">
              <span>Mục tiêu: {{ vehicle.desiredSpeedKmh }} km/h</span><span>•</span
              ><span>Tối đa: {{ vehicle.speedLimitKmh }} km/h</span>
            </div>
          </div>
        </div>
        <div class="speed-progress-track">
          <div
            :class="['speed-progress-fill', { danger: vehicle.speedKmh > vehicle.speedLimitKmh }]"
            :style="{
              width: `${Math.min(100, (vehicle.speedKmh / vehicle.speedLimitKmh) * 100)}%`,
            }"
          />
        </div>
      </section>
      <section class="next-station-card">
        <div class="next-card-header">
          <div class="next-card-title">
            <MapPin
              :size="16"
              class="text-cyan"
            /><span>TRẠM TIẾP THEO</span>
          </div>
          <span class="next-eta-badge tabular-numbers">ETA ~{{ vehicle.etaMinutes }} phút</span>
        </div>
        <h3 class="next-station-name">{{ vehicle.nextStationName }}</h3>
        <div class="next-card-metrics tabular-numbers">
          <div>
            <span class="metric-label">Cự ly còn lại</span
            ><span class="metric-val">{{ vehicle.distanceToNextMeters }} m</span>
          </div>
          <div>
            <span class="metric-label">Tiến độ tuyến</span
            ><span class="metric-val">{{ vehicle.tripProgressPercent }}%</span>
          </div>
          <div>
            <span class="metric-label">Hướng di chuyển</span
            ><span class="metric-val">{{ vehicle.heading }}°</span>
          </div>
        </div>
        <div class="trip-progress-bar">
          <div
            class="trip-progress-fill"
            :style="{ width: `${vehicle.tripProgressPercent}%` }"
          />
        </div>
      </section>
      <section class="driver-card">
        <div class="driver-info">
          <div class="driver-avatar"><User :size="18" /></div>
          <div>
            <span class="driver-label">TÀI XẾ PHỤ TRÁCH</span
            ><strong class="driver-name">{{ vehicle.driverName }}</strong>
          </div>
        </div>
        <a
          :href="`tel:${vehicle.driverPhone.replace(/\s+/g, '')}`"
          class="driver-phone-btn"
          :title="`Gọi ${vehicle.driverPhone}`"
          ><Phone :size="14" /><span>{{ vehicle.driverPhone }}</span></a
        >
      </section>
      <section class="timeline-section">
        <div class="timeline-title-row"><Clock :size="15" /><span>LỊCH TRÌNH CÁC TRẠM</span></div>
        <div class="timeline-list">
          <div
            v-for="(stop, idx) in vehicle.timeline"
            :key="stop.stationId || idx"
            :class="[
              'timeline-item',
              { passed: stop.status === 'PASSED', current: stop.status === 'CURRENT' },
            ]"
          >
            <div class="timeline-marker-col">
              <div class="timeline-node">
                <CheckCircle2
                  v-if="stop.status === 'PASSED'"
                  :size="12"
                /><Compass
                  v-else-if="stop.status === 'CURRENT'"
                  :size="12"
                />
              </div>
              <div
                v-if="idx < vehicle.timeline.length - 1"
                class="timeline-line"
              />
            </div>
            <div class="timeline-info">
              <strong class="timeline-station-name">{{ stop.stationName }}</strong>
              <div class="timeline-times tabular-numbers">
                <span class="time-planned">Dự kiến: {{ stop.plannedTime }}</span
                ><span class="time-sep">•</span
                ><span :class="['time-actual', stop.status === 'PASSED' ? 'passed' : 'eta']">{{
                  stop.status === 'PASSED'
                    ? `Đã đến: ${stop.actualOrEtaTime}`
                    : `ETA: ${stop.actualOrEtaTime}`
                }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
    <div class="drawer-actions-footer">
      <button
        type="button"
        :class="['follow-vehicle-btn', { following }]"
        :aria-pressed="following"
        @click="onToggleFollow"
      >
        <Navigation
          :size="14"
          :class="following ? 'spin-subtle' : ''"
        /><span>{{ following ? 'Đang bám theo xe' : 'Bám theo xe này' }}</span>
      </button>
      <button
        type="button"
        class="fit-route-btn"
        title="Xem toàn bộ lộ trình tuyến"
        aria-label="Xem toàn bộ tuyến đường"
        @click="onFitRoute"
      >
        <Maximize2 :size="14" /><span>Toàn tuyến</span>
      </button>
    </div>
  </aside>
</template>
