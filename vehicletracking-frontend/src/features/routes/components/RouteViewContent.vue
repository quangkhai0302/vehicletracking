<script setup lang="ts">
import { ref } from 'vue';
import { AlertCircle, RefreshCw, X } from '@lucide/vue';
import type { RouteDetail } from '@/features/routes/types/route';
import { formatDuration } from '@/shared/utils/format';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
const props = defineProps<{
  onShape: () => void;
  saving: boolean;
  onEdit: () => void;
  onDeactivate: () => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  routeDetail: RouteDetail | null;
  loadingDetail: boolean;
  error: string | null;
  onClose: () => void;
}>();
const confirm = ref(false);
const deactivate = () => {
  void props.onDeactivate().then((success) => {
    if (success) confirm.value = false;
  });
};
</script>
<template>
  <div class="route-drawer-header">
    <div>
      <div class="panel-eyebrow">Lộ trình chi tiết</div>
      <h3>{{ routeDetail?.name || 'Chi tiết tuyến' }}</h3>
    </div>
    <button
      type="button"
      class="drawer-close-btn"
      :disabled="saving"
      title="Đóng bảng chi tiết"
      aria-label="Đóng"
      @click="onClose"
    >
      <X :size="18" />
    </button>
  </div>
  <div class="route-drawer-body">
    <div
      v-if="error"
      class="route-error-banner"
      role="alert"
    >
      <AlertCircle :size="16" /><span>{{ error }}</span>
    </div>
    <div
      v-if="loadingDetail"
      class="panel-loading"
      role="status"
    >
      <RefreshCw
        :size="18"
        class="animate-spin"
      /><span>Đang tải thông tin chi tiết lộ trình...</span>
    </div>
    <template v-if="!loadingDetail && routeDetail">
      <div class="route-detail-summary-grid">
        <div class="metric-card">
          <span class="metric-card-label">Tổng cự ly</span
          ><span class="metric-card-value tabular-numbers"
            >{{ (routeDetail.totalDistanceMeters / 1000).toFixed(2) }} km</span
          ><span class="metric-card-sub">Theo mạng lưới giao thông</span>
        </div>
        <div class="metric-card">
          <span class="metric-card-label">Thời gian dự kiến</span
          ><span class="metric-card-value tabular-numbers">{{
            formatDuration(routeDetail.estimatedTripDurationSeconds)
          }}</span
          ><span class="metric-card-sub">Bao gồm thời gian dừng</span>
        </div>
        <div class="metric-card">
          <span class="metric-card-label">Thời gian lăn bánh</span
          ><span class="metric-card-value tabular-numbers">{{
            formatDuration(routeDetail.estimatedTravelDurationSeconds)
          }}</span
          ><span class="metric-card-sub">Di chuyển bằng ô tô</span>
        </div>
        <div class="metric-card">
          <span class="metric-card-label">Thời gian đón/trả</span
          ><span class="metric-card-value tabular-numbers">{{
            formatDuration(routeDetail.totalDwellDurationSeconds)
          }}</span
          ><span class="metric-card-sub">{{ routeDetail.stops.length }} điểm dừng</span>
        </div>
      </div>
      <div class="route-snapshot-note">
        <strong
          >Ước tính khi tạo tuyến ·
          {{ new Date(routeDetail.calculatedAt).toLocaleString('vi-VN') }}</strong
        >
        Thời gian này chưa được cập nhật theo vị trí xe đang di chuyển.
      </div>
      <div style="margin-top: 10px">
        <span class="form-label">Hành trình chi tiết qua các trạm</span>
        <div class="route-timeline">
          <div
            v-for="(stop, index) in routeDetail.stops"
            :key="stop.sequenceNumber"
            class="timeline-item"
          >
            <div class="timeline-track">
              <div :class="`timeline-node ${stop.role.toLowerCase()}`">
                {{ stop.sequenceNumber }}
              </div>
              <div
                v-if="index < routeDetail.stops.length - 1"
                class="timeline-line"
              />
            </div>
            <div class="timeline-content">
              <div class="timeline-title-row">
                <button
                  class="timeline-station-name"
                  title="Xem trạm trên bản đồ"
                  @click="onFocusStop([stop.latitude, stop.longitude], 16)"
                >
                  {{ stop.stationName }}</button
                ><span :class="`stop-badge ${stop.role.toLowerCase()}`">{{
                  stop.role === 'START' ? 'Khởi hành' : stop.role === 'END' ? 'Về đích' : 'Đón trả'
                }}</span>
              </div>
              <div
                v-if="index > 0"
                class="timeline-leg-meta tabular-numbers"
              >
                <span>+{{ (stop.distanceFromPreviousMeters / 1000).toFixed(2) }} km</span
                ><span>+{{ formatDuration(stop.travelDurationFromPreviousSeconds) }}</span>
              </div>
              <div class="timeline-offsets tabular-numbers">
                <span>Đến: +{{ formatDuration(stop.arrivalOffsetSeconds) }}</span
                ><span style="margin-left: 10px"
                  >Rời: +{{ formatDuration(stop.departureOffsetSeconds) }}</span
                ><span
                  v-if="stop.dwellDurationSeconds > 0"
                  style="margin-left: 10px; color: var(--color-text-muted)"
                  >(dừng {{ stop.dwellDurationSeconds }}s)</span
                >
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
  <div class="route-drawer-footer">
    <button
      type="button"
      class="btn-secondary"
      :disabled="saving"
      @click="onClose"
    >
      Đóng</button
    ><template v-if="!loadingDetail && routeDetail"
      ><button
        type="button"
        class="btn-primary"
        :disabled="saving"
        @click="onShape"
      >
        Kéo chỉnh đường đi</button
      ><button
        type="button"
        class="btn-secondary"
        :disabled="saving"
        @click="onEdit"
      >
        Sửa tuyến</button
      ><button
        type="button"
        class="danger-action"
        :disabled="saving"
        @click="confirm = true"
      >
        Ngừng sử dụng
      </button></template
    >
  </div>
  <FleetConfirmDialog
    v-if="confirm"
    title="Ngừng sử dụng tuyến đường?"
    message="Tuyến sẽ không còn trong danh sách tạo chuyến. Lịch sử được giữ lại; chuyến chưa kết thúc có thể ngăn thao tác này."
    confirm-label="Xác nhận ngừng tuyến"
    :busy="saving"
    :error="error"
    :on-close="
      () => {
        confirm = false;
      }
    "
    :on-confirm="deactivate"
  />
</template>
