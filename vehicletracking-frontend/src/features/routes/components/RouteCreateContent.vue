<script lang="ts">
let nextDraftStopId = 0;
const createDraftStopId = () => `draft-stop-${++nextDraftStopId}`;
</script>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, useId, watch } from 'vue';
import { AlertCircle, Car, CheckCircle2, Plus, RefreshCw, X } from '@lucide/vue';
import type { RouteCreateInput, RouteDetail, RouteDraftStop } from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
import SortableStopList from './SortableStopList.vue';
const props = defineProps<{
  initialRoute?: RouteDetail;
  stations: Station[];
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
  onDraftStopsChange: (stops: RouteDraftStop[]) => void;
  selectedDraftStopId: string | null;
  onFocusDraftStop: (id: string) => void;
}>();
const nameInputId = useId(),
  nameInput = shallowRef<HTMLInputElement | null>(null);
const name = ref(props.initialRoute?.name ?? ''),
  confirmDiscard = ref(false);
const activeStations = computed(() => props.stations.filter((station) => station.active));
const formStops = shallowRef<RouteDraftStop[]>(
  props.initialRoute
    ? props.initialRoute.stops.map((stop) => ({
        id: createDraftStopId(),
        stationId: stop.stationId,
        dwellDurationSeconds: stop.dwellDurationSeconds,
      }))
    : activeStations.value
        .slice(0, 2)
        .map((station) => ({
          id: createDraftStopId(),
          stationId: station.id,
          dwellDurationSeconds: 0,
        })),
);
const initialStops = formStops.value;
watch([formStops, () => props.onDraftStopsChange], ([stops, callback]) => callback(stops), {
  immediate: true,
});
onBeforeUnmount(() => props.onDraftStopsChange([]));
onMounted(() => nameInput.value?.focus());
const normalizeStops = (stops: RouteDraftStop[]) =>
  stops.map((stop, index) => ({
    ...stop,
    dwellDurationSeconds:
      index === 0 || index === stops.length - 1
        ? 0
        : Math.max(
            0,
            Math.min(
              3600,
              Number.isFinite(stop.dwellDurationSeconds) ? stop.dwellDurationSeconds : 0,
            ),
          ),
  }));
const updateStops = (stops: RouteDraftStop[]) => {
  formStops.value = normalizeStops(stops);
};
const safeClose = () => {
  if (props.saving) return;
  if (
    name.value !== (props.initialRoute?.name ?? '') ||
    JSON.stringify(formStops.value) !== JSON.stringify(initialStops)
  )
    confirmDiscard.value = true;
  else props.onClose();
};
const addStop = () => {
  if (!activeStations.value.length) return;
  const last = formStops.value[formStops.value.length - 1];
  const next =
    activeStations.value.find((station) => station.id !== last?.stationId) ??
    activeStations.value[0];
  updateStops([
    ...formStops.value,
    { id: createDraftStopId(), stationId: next.id, dwellDurationSeconds: 0 },
  ]);
};
const hasNoConsecutiveDuplicates = computed(
  () =>
    formStops.value.length >= 2 &&
    formStops.value.every(
      (stop, index) => index === 0 || stop.stationId !== formStops.value[index - 1].stationId,
    ),
);
const allStationsActive = computed(() =>
  formStops.value.every((stop) =>
    activeStations.value.some((station) => station.id === stop.stationId),
  ),
);
const isFormValid = computed(
  () =>
    name.value.trim().length > 0 &&
    name.value.trim().length <= 150 &&
    formStops.value.length >= 2 &&
    formStops.value.length <= 50 &&
    hasNoConsecutiveDuplicates.value &&
    allStationsActive.value &&
    formStops.value.every((stop, index) =>
      index === 0 || index === formStops.value.length - 1
        ? stop.dwellDurationSeconds === 0
        : stop.dwellDurationSeconds >= 0 && stop.dwellDurationSeconds <= 3600,
    ),
);
const submit = () => {
  if (props.saving || !isFormValid.value) return;
  props.onSaveRoute({
    name: name.value.trim(),
    stops: normalizeStops(formStops.value).map((stop) => ({
      stationId: stop.stationId,
      dwellDurationSeconds: stop.dwellDurationSeconds,
    })),
  });
};
</script>
<template>
  <div class="route-drawer-header">
    <div>
      <div class="panel-eyebrow">Kế hoạch vận hành</div>
      <h3>{{ initialRoute ? 'Sửa tuyến đường' : 'Tạo tuyến đường mới' }}</h3>
    </div>
    <button
      type="button"
      class="drawer-close-btn"
      :disabled="saving"
      title="Đóng bảng tạo tuyến"
      aria-label="Đóng"
      @click="safeClose"
    >
      <X :size="18" />
    </button>
  </div>
  <div class="route-drawer-body">
    <p
      v-if="initialRoute?.shapingPoints?.length"
      class="panel-help"
    >
      Sửa thông tin hoặc trạm bằng biểu mẫu này sẽ tính lại đường đi và bỏ các điểm kéo chỉnh đã
      lưu. Để giữ trạm và chỉ đổi đường đi, hãy dùng “Kéo chỉnh đường đi”.
    </p>
    <div
      v-if="confirmDiscard"
      class="discard-confirm"
      role="alert"
    >
      <strong>Bỏ bản nháp tuyến đường?</strong>
      <p>Tên và thứ tự điểm dừng chưa lưu sẽ bị xóa.</p>
      <div>
        <button
          type="button"
          class="btn-secondary"
          @click="confirmDiscard = false"
        >
          Tiếp tục chỉnh sửa</button
        ><button
          type="button"
          class="danger-action"
          @click="onClose"
        >
          Bỏ bản nháp
        </button>
      </div>
    </div>
    <div
      v-if="error"
      class="route-error-banner"
      role="alert"
    >
      <AlertCircle :size="16" /><span>{{ error }}</span>
    </div>
    <form
      id="route-create-form"
      @submit.prevent="submit"
    >
      <fieldset
        :disabled="saving"
        class="route-form-fields"
      >
        <div class="form-field">
          <label
            :for="nameInputId"
            class="form-label"
            >Tên tuyến đường *</label
          ><input
            :id="nameInputId"
            ref="nameInput"
            v-model="name"
            type="text"
            class="form-input"
            placeholder="Ví dụ: Tuyến 01: Bến Thành - Suối Tiên"
            :maxlength="150"
            autofocus
            required
          />
        </div>
        <div
          class="form-field"
          style="margin-top: 14px"
        >
          <span class="form-label">Phương tiện vận chuyển</span>
          <div
            style="
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding: 9px 12px;
              background: linear-gradient(
                145deg,
                rgba(20, 32, 48, 0.7) 0%,
                rgba(10, 18, 28, 0.85) 100%
              );
              border: 1px solid rgba(56, 189, 248, 0.25);
              border-radius: 8px;
              font-size: 12px;
            "
          >
            <div style="display: flex; align-items: center; gap: 8px">
              <Car
                :size="16"
                class="text-cyan"
              /><span style="font-weight: 500; color: #f1f5f9">Ô tô / Xe buýt</span>
            </div>
            <span
              style="
                font-size: 10px;
                font-weight: 700;
                padding: 2px 7px;
                border-radius: 4px;
                background: rgba(34, 211, 238, 0.15);
                color: #38bdf8;
                border: 1px solid rgba(34, 211, 238, 0.35);
              "
              >HERE CAR</span
            >
          </div>
        </div>
        <div style="margin-top: 18px">
          <div class="stops-builder-header">
            <span class="form-label">Danh sách điểm dừng ({{ formStops.length }} trạm)</span
            ><span style="font-size: 11px; color: var(--color-text-muted)">Tối thiểu 2 trạm</span>
          </div>
          <SortableStopList
            :stops="formStops"
            :stations="activeStations"
            :disabled="saving"
            :on-change="updateStops"
            :selected-id="selectedDraftStopId"
            :on-focus-stop="onFocusDraftStop"
          />
          <button
            type="button"
            class="btn-add-stop"
            :disabled="activeStations.length === 0 || formStops.length >= 50"
            @click="addStop"
          >
            <Plus :size="14" /> Thêm điểm dừng đón/trả
          </button>
        </div>
        <p class="route-draft-note">
          {{
            initialRoute
              ? 'Tính lại lộ trình HERE và cập nhật tuyến hiện tại. Chỉ sửa được tuyến chưa từng được dùng bởi chuyến đi.'
              : 'Bản nháp · Chưa tính lộ trình hoặc ETA. Tính & lưu sẽ tạo một tuyến mới theo thứ tự trên.'
          }}
        </p>
        <p
          v-if="!hasNoConsecutiveDuplicates && formStops.length >= 2"
          role="alert"
          class="inline-error"
        >
          Hai điểm liền nhau phải là hai trạm khác nhau.
        </p>
        <p
          v-if="!allStationsActive"
          role="alert"
          class="inline-error"
        >
          Có trạm đã ngừng hoạt động. Chọn lại trạm trước khi lưu.
        </p>
        <p
          v-if="formStops.length < 2"
          class="availability-note"
        >
          Cần ít nhất hai điểm dừng. Bạn có thể thêm trạm ở tab Trạm dừng rồi quay lại.
        </p>
      </fieldset>
    </form>
  </div>
  <div class="route-drawer-footer">
    <button
      type="button"
      class="btn-secondary"
      :disabled="saving"
      @click="safeClose"
    >
      Hủy</button
    ><button
      type="submit"
      form="route-create-form"
      class="btn-primary"
      :disabled="saving || !isFormValid"
    >
      <template v-if="saving"
        ><RefreshCw
          :size="14"
          class="animate-spin"
        /><span>Đang tính toán lộ trình HERE...</span></template
      ><template v-else
        ><CheckCircle2 :size="14" /><span>{{
          initialRoute ? 'Tính & cập nhật tuyến' : 'Tính & lưu tuyến mới'
        }}</span></template
      >
    </button>
  </div>
</template>
