<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue';
import { Pencil, X } from '@lucide/vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import RouteMapCanvas from './RouteMapCanvas.vue';
import RouteShapeEditor from './RouteShapeEditor.vue';
import type { RouteDetail } from '@/features/routes/types/route';
import { formatDuration } from '@/shared/utils/format';
import '@/features/routes/styles/route.css';

const props = defineProps<{
  route: RouteDetail | null;
  loading: boolean;
  error: string | null;
  editable: boolean;
  onClose: () => void;
  onRetry: () => void;
  onSaved: (route: RouteDetail) => void;
  onCreateTrip: () => void;
}>();
const canvas = shallowRef<InstanceType<typeof RouteMapCanvas> | null>(null);
const editor = shallowRef<InstanceType<typeof RouteShapeEditor> | null>(null);
const editing = ref(false);
const busy = computed(() => props.loading || editor.value?.busy === true);
const closeEditor = () => {
  editing.value = false;
};
const close = () => {
  if (busy.value) return;
  if (editing.value) {
    editor.value?.requestClose(props.onClose);
  } else props.onClose();
};
const saved = (route: RouteDetail) => {
  editing.value = false;
  props.onSaved(route);
};
</script>

<template>
  <SidePanel
    class-name="schedule-editor route-map-dialog"
    :label="route?.name || 'Chi tiết tuyến đường'"
    :busy="busy"
    :content-sized="true"
    :on-close="close"
  >
    <header>
      <div>
        <span>{{ editing ? 'CHỈNH ĐƯỜNG ĐI' : 'CHI TIẾT LỘ TRÌNH' }}</span>
        <h2>{{ route?.name || 'Chi tiết tuyến đường' }}</h2>
      </div>
      <button
        type="button"
        aria-label="Đóng chi tiết"
        :disabled="busy"
        @click="close"
      >
        <X :size="18" />
      </button>
    </header>
    <div class="route-map-content">
      <p
        v-if="loading"
        class="route-map-state"
        role="status"
      >
        Đang tải dữ liệu lộ trình…
      </p>
      <div
        v-else-if="error || !route"
        class="route-map-state"
        role="alert"
      >
        <p>{{ error || 'Chưa có dữ liệu tuyến đường.' }}</p>
        <button
          type="button"
          class="schedule-button-secondary"
          @click="onRetry"
        >
          Tải lại chi tiết
        </button>
      </div>
      <template v-else>
        <div class="route-map-summary">
          <span
            ><small>Tổng cự ly</small
            ><strong>{{ (route.totalDistanceMeters / 1000).toFixed(1) }} km</strong></span
          >
          <span
            ><small>Dự kiến</small
            ><strong>{{ formatDuration(route.estimatedTripDurationSeconds) }}</strong></span
          >
          <span
            ><small>Số trạm</small><strong>{{ route.stops.length }} điểm</strong></span
          >
        </div>
        <RouteMapCanvas
          ref="canvas"
          v-slot="{ map }"
          :route="route"
          :editing="editing"
        >
          <RouteShapeEditor
            v-if="editing"
            :key="route.id"
            ref="editor"
            :route="route"
            :map="map"
            :on-close="closeEditor"
            :on-saved="saved"
          />
        </RouteMapCanvas>
        <p
          v-if="!editing"
          class="route-map-help"
        >
          Bấm vào biểu tượng trạm để xem thông tin. Chọn “Sửa tuyến đường” để chỉnh đường xe đi qua.
        </p>
      </template>
    </div>
    <footer
      v-if="!editing"
      class="schedule-editor-footer"
    >
      <button
        type="button"
        class="schedule-button-secondary"
        :disabled="busy"
        @click="close"
      >
        Đóng
      </button>
      <button
        v-if="route && editable"
        type="button"
        class="schedule-button-secondary"
        @click="onCreateTrip"
      >
        Tạo chuyến từ tuyến này
      </button>
      <button
        v-if="route && editable"
        type="button"
        class="schedule-button-primary"
        :disabled="!canvas?.geometryReady"
        @click="editing = true"
      >
        <Pencil :size="15" />Sửa tuyến đường
      </button>
      <button
        v-if="route && !canvas?.geometryReady"
        type="button"
        class="schedule-button-secondary"
        @click="onRetry"
      >
        Tải lại chi tiết
      </button>
    </footer>
  </SidePanel>
</template>

<style scoped>
.business-side-panel.route-map-dialog {
  width: min(1120px, calc(100vw - 32px));
  max-width: 1120px;
}
.route-map-dialog header h2 {
  white-space: normal;
  overflow-wrap: anywhere;
}
.route-map-content {
  min-height: 0;
  overflow-y: auto;
  background: #fff;
}
.route-map-summary {
  display: flex;
  gap: 32px;
  padding: 16px 22px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.route-map-summary span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.route-map-summary small {
  color: #64748b;
  font-size: 11px;
}
.route-map-summary strong {
  color: #075985;
  font-size: 15px;
}
.route-map-help {
  padding: 12px 22px;
  color: #64748b;
  font-size: 12px;
}
.route-map-state {
  min-height: 240px;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 24px;
  color: #475569;
}
.schedule-editor-footer {
  flex-wrap: wrap;
}
:deep(.route-drawer) {
  position: static;
  width: auto;
  max-width: none;
  height: 100%;
  margin: 0;
  padding: 0;
  transform: none;
  background: #fff;
  border: 0;
  border-left: 1px solid #e2e8f0;
  border-radius: 0;
  box-shadow: none;
  color: #334155;
}
:deep(.route-drawer-header),
:deep(.route-drawer-footer) {
  border-color: #e2e8f0;
  background: #f8fafc;
}
:deep(.route-drawer h3) {
  color: #0f172a;
}
:deep(.route-drawer-body) {
  color: #475569;
  font-size: 12px;
}
:deep(.route-drawer .route-shape-copy) {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  margin: 4px 0;
  font-size: 12px;
}
:deep(.route-drawer .route-shape-copy input[type='checkbox']) {
  width: 16px;
  height: 16px;
  min-height: 16px;
  flex: 0 0 16px;
  padding: 0;
  margin: 0;
}
:deep(.route-drawer .btn-secondary),
:deep(.route-drawer .drawer-close-btn) {
  background: #fff;
  border-color: #cbd5e1;
  color: #475569;
}
@media (max-width: 700px) {
  .business-side-panel.route-map-dialog {
    width: calc(100vw - 16px);
    max-width: calc(100vw - 16px);
    max-height: calc(100dvh - 16px);
  }
  .route-map-summary {
    gap: 20px;
    padding: 12px 16px;
  }
  .route-map-dialog .schedule-editor-footer {
    display: grid;
    grid-template-columns: minmax(0, 0.6fr) minmax(0, 1.4fr);
    gap: 8px;
    padding: 12px 16px;
  }
  .route-map-dialog .schedule-editor-footer button {
    min-height: 42px;
  }
  .route-map-dialog .schedule-editor-footer .schedule-button-primary {
    grid-column: 1 / -1;
  }
  :deep(.route-drawer) {
    height: auto;
    max-height: 440px;
    border-left: 0;
    border-top: 1px solid #e2e8f0;
  }
}
</style>
