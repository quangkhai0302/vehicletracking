<script setup lang="ts">
import { computed, ref } from 'vue';
import { AlertCircle, Crosshair, Edit3, Save, Sliders, Trash2, X } from '@lucide/vue';
import type { Station, StationFormMode, StationFormState, StationInput } from '@/features/stations/types/station';
const props = defineProps<{
  station: Station | null;
  mode: StationFormMode;
  form: StationFormState;
  saving: boolean;
  error: string | null;
  pickingLocation: boolean;
  onClose: () => void;
  onBeginEdit: () => void;
  onPickLocation: () => void;
  onFieldChange: (field: keyof StationFormState, value: string) => void;
  onSave: (input: StationInput) => Promise<void>;
  onRequestDeactivate: () => void;
}>();
const presets = [30, 50, 100, 200, 500],
  showDiscardConfirm = ref(false);
const isFormOpen = computed(() => props.mode !== 'closed'),
  parsedRadius = computed(() => Number(props.form.checkinRadiusMeters));
const isRadiusValid = computed(
  () =>
    Number.isInteger(parsedRadius.value) && parsedRadius.value >= 10 && parsedRadius.value <= 1000,
);
const isDirty = computed(() => {
  const { mode, form, station } = props;
  return mode === 'create'
    ? Boolean(form.name.trim()) ||
        Boolean(form.address.trim()) ||
        Boolean(form.latitude.trim()) ||
        Boolean(form.longitude.trim()) ||
        (form.checkinRadiusMeters.trim() !== '' && form.checkinRadiusMeters.trim() !== '50')
    : mode === 'edit' && station
      ? form.name.trim() !== station.name.trim() ||
        (form.address.trim() || '') !== (station.address?.trim() || '') ||
        form.latitude.trim() !== String(station.latitude) ||
        form.longitude.trim() !== String(station.longitude) ||
        form.checkinRadiusMeters.trim() !== String(station.checkinRadiusMeters)
      : false;
});
const safeClose = () => {
  if (props.saving) return;
  if (isFormOpen.value && isDirty.value) showDiscardConfirm.value = true;
  else props.onClose();
};
const discard = () => {
  showDiscardConfirm.value = false;
  props.onClose();
};
const submit = async () => {
  if (props.saving || !isRadiusValid.value) return;
  showDiscardConfirm.value = false;
  await props.onSave({
    name: props.form.name.trim(),
    address: props.form.address.trim() || null,
    latitude: Number(props.form.latitude),
    longitude: Number(props.form.longitude),
    checkinRadiusMeters: parsedRadius.value,
  });
};
const sliderValue = computed(() =>
  Number.isFinite(parsedRadius.value) ? Math.min(1000, Math.max(10, parsedRadius.value)) : 50,
);
const field = (name: keyof StationFormState, event: Event) =>
  props.onFieldChange(name, (event.target as HTMLInputElement).value);
const revealInvalid = (event: Event) => {
  (event.currentTarget as HTMLDetailsElement).open = true;
};
</script>
<template>
  <aside
    v-if="isFormOpen || station"
    class="station-drawer"
    :aria-label="isFormOpen ? 'Biểu mẫu trạm' : 'Chi tiết trạm'"
  >
    <div class="drawer-header">
      <div>
        <div class="drawer-eyebrow-row">
          <span class="panel-eyebrow">{{
            mode === 'create'
              ? 'Thiết lập trạm mới'
              : mode === 'edit'
                ? 'Cập nhật trạm'
                : 'Thông tin trạm'
          }}</span
          ><span
            :class="`mode-badge ${mode === 'create' ? 'create' : mode === 'edit' ? 'edit' : 'browse'}`"
            >{{ mode === 'create' ? 'CREATE' : mode === 'edit' ? 'EDIT' : 'BROWSE' }}</span
          >
        </div>
        <h2>
          {{
            mode === 'create' ? 'Tạo trạm mới' : mode === 'edit' ? 'Chỉnh sửa trạm' : station?.name
          }}
        </h2>
      </div>
      <button
        type="button"
        class="icon-action"
        :disabled="saving"
        aria-label="Đóng panel"
        title="Đóng"
        @click="safeClose"
      >
        <X :size="18" />
      </button>
    </div>
    <div
      v-if="error"
      class="station-alert drawer-alert"
      role="alert"
    >
      {{ error }}
    </div>
    <div
      v-if="showDiscardConfirm"
      class="inline-discard-alert"
      role="alert"
    >
      <AlertCircle :size="16" />
      <div>
        <strong>Hủy các thay đổi chưa lưu?</strong>
        <p>Nội dung bạn đang nhập sẽ bị mất nếu đóng lúc này.</p>
      </div>
      <div class="discard-actions">
        <button
          type="button"
          class="discard-stay-btn"
          @click="showDiscardConfirm = false"
        >
          Ở lại</button
        ><button
          type="button"
          class="discard-confirm-btn"
          @click="discard"
        >
          Hủy thay đổi
        </button>
      </div>
    </div>
    <div
      v-if="!isFormOpen && station"
      class="station-details"
    >
      <div class="station-detail-header">
        <div>
          <h3 class="station-detail-name">{{ station.name }}</h3>
          <div class="station-detail-sub">
            <span class="active-status"><span /> Đang hoạt động</span>
          </div>
        </div>
      </div>
      <div class="station-property-list">
        <div class="station-property-group">
          <span class="station-property-label">ĐỊA CHỈ HOẠT ĐỘNG</span>
          <p class="station-property-value">
            {{ station.address || 'Chưa có địa chỉ mô tả cụ thể' }}
          </p>
        </div>
        <details class="trip-traffic-details">
          <summary>Thông số nâng cao</summary>
          <div class="station-property-group">
            <span class="station-property-label">TỌA ĐỘ VỊ TRÍ</span>
            <p class="station-property-value tabular-numbers">
              {{ station.latitude.toFixed(6) }}, {{ station.longitude.toFixed(6) }}
            </p>
          </div>
          <div class="station-property-group">
            <span class="station-property-label">BÁN KÍNH CHECK-IN</span>
            <p class="station-property-value tabular-numbers">
              {{ station.checkinRadiusMeters }} mét
            </p>
          </div>
          <div class="station-property-group">
            <span class="station-property-label">LẦN CẬP NHẬT GẦN NHẤT</span>
            <p class="station-property-value tabular-numbers">
              {{ new Date(station.updatedAt).toLocaleString('vi-VN') }}
            </p>
          </div>
        </details>
      </div>
      <div class="drawer-actions">
        <button
          type="button"
          class="secondary-action danger"
          @click="onRequestDeactivate"
        >
          <Trash2 :size="15" /> Ngừng sử dụng</button
        ><button
          type="button"
          class="primary-action"
          @click="onBeginEdit"
        >
          <Edit3 :size="15" /> Chỉnh sửa
        </button>
      </div>
    </div>
    <form
      v-if="isFormOpen"
      class="station-form"
      @submit.prevent="submit"
    >
      <fieldset :disabled="saving">
        <div :class="`location-picker-status ${pickingLocation ? 'picking' : ''}`">
          <Crosshair :size="18" />
          <div>
            <strong>{{ pickingLocation ? 'Chọn điểm trên bản đồ' : 'Vị trí trạm' }}</strong
            ><span>{{
              pickingLocation
                ? 'Kéo bản đồ để tìm vị trí, rồi xác nhận điểm nằm trong tâm ngắm.'
                : form.latitude && form.longitude
                  ? 'Đã chọn vị trí. Kéo dấu trạm để điều chỉnh.'
                  : 'Bấm chọn vị trí, kéo bản đồ đến khu vực cần đặt trạm rồi xác nhận.'
            }}</span>
          </div>
          <button
            type="button"
            :disabled="saving"
            aria-label="Chọn lại vị trí trên bản đồ"
            @click="onPickLocation"
          >
            {{ form.latitude ? 'Chọn lại' : 'Chọn vị trí' }}
          </button>
        </div>
        <label class="station-field"
          ><span>Tên trạm đón trả khách *</span
          ><input
            :value="form.name"
            :maxlength="150"
            required
            placeholder="Ví dụ: Bến xe Miền Đông, Ngã tư Hàng Xanh"
            @input="field('name', $event)"
        /></label>
        <label class="station-field"
          ><span>Địa chỉ mô tả</span
          ><input
            :value="form.address"
            :maxlength="255"
            placeholder="Ví dụ: 292 Đinh Bộ Lĩnh, Phường 26, Bình Thạnh"
            @input="field('address', $event)"
        /></label>
        <details
          class="trip-traffic-details"
          @invalid.capture="revealInvalid"
        >
          <summary>Nâng cao · tọa độ và vùng check-in</summary>
          <div class="station-field-row">
            <label class="station-field"
              ><span>Vĩ độ (Latitude) *</span
              ><input
                type="number"
                class="tabular-numbers"
                :value="form.latitude"
                :min="-90"
                :max="90"
                step="0.000001"
                required
                placeholder="10.814387"
                @input="field('latitude', $event)" /></label
            ><label class="station-field"
              ><span>Kinh độ (Longitude) *</span
              ><input
                type="number"
                class="tabular-numbers"
                :value="form.longitude"
                :min="-180"
                :max="180"
                step="0.000001"
                required
                placeholder="106.711822"
                @input="field('longitude', $event)"
            /></label>
          </div>
          <div class="radius-selector-card">
            <div class="radius-selector-header">
              <div class="radius-label-row">
                <Sliders
                  :size="14"
                  class="text-cyan"
                /><span>BÁN KÍNH CHECK-IN *</span>
              </div>
              <div class="radius-input-wrapper">
                <input
                  type="number"
                  class="radius-number-input tabular-numbers"
                  :min="10"
                  :max="1000"
                  :step="1"
                  :value="form.checkinRadiusMeters"
                  aria-label="Bán kính check-in tính bằng mét"
                  required
                  @input="field('checkinRadiusMeters', $event)"
                /><span class="radius-unit">m</span>
              </div>
            </div>
            <div
              class="radius-presets-row"
              role="group"
              aria-label="Các mốc bán kính nhanh"
            >
              <button
                v-for="preset in presets"
                :key="preset"
                type="button"
                :class="`preset-btn ${parsedRadius === preset ? 'active' : ''}`"
                :aria-pressed="parsedRadius === preset"
                @click="onFieldChange('checkinRadiusMeters', String(preset))"
              >
                {{ preset }}m
              </button>
            </div>
            <div class="radius-slider-box">
              <input
                type="range"
                class="radius-range-slider"
                :min="10"
                :max="1000"
                :step="5"
                :value="sliderValue"
                aria-label="Thanh trượt điều chỉnh bán kính"
                @input="field('checkinRadiusMeters', $event)"
              />
              <div class="slider-ticks">
                <span>10m</span><span>100m</span><span>300m</span><span>500m</span
                ><span>1000m</span>
              </div>
            </div>
            <span
              v-if="!isRadiusValid && form.checkinRadiusMeters.trim() !== ''"
              class="radius-error-text"
              role="alert"
              >Bán kính check-in hợp lệ từ 10 đến 1.000 mét.</span
            ><span class="radius-helper-text"
              >Vòng tròn thể hiện bán kính của trạm (10 – 1.000m). Check-in tự động được ghi nhận
              khi chuyến nhận telemetry đi qua vùng này.</span
            >
          </div>
        </details>
        <div class="drawer-actions">
          <button
            type="button"
            class="secondary-action"
            :disabled="saving"
            @click="safeClose"
          >
            Hủy</button
          ><button
            type="submit"
            class="primary-action"
            :disabled="
              saving || !form.name.trim() || !form.latitude || !form.longitude || !isRadiusValid
            "
          >
            <Save :size="15" />
            {{ saving ? 'Đang lưu…' : mode === 'create' ? 'Tạo trạm' : 'Lưu thay đổi' }}
          </button>
        </div>
      </fieldset>
    </form>
  </aside>
</template>
