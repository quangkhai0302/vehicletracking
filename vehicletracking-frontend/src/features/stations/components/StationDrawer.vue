<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import {
  AlertCircle,
  BusFront,
  Clock3,
  Crosshair,
  Edit3,
  MapPin,
  Radio,
  Save,
  Sliders,
  Trash2,
  X,
} from '@lucide/vue';
import type {
  Station,
  StationFormMode,
  StationFormState,
  StationInput,
} from '@/features/stations/types/station';
const props = defineProps<{
  station: Station | null;
  mode: StationFormMode;
  form: StationFormState;
  saving: boolean;
  pickingLocation: boolean;
  addressLookupLoading?: boolean;
  addressLookupError?: string | null;
  addressSuggestion?: string | null;
  onRetryAddressLookup?: () => void;
  onApplyAddressSuggestion?: () => void;
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
const stay = () => {
  showDiscardConfirm.value = false;
};
watch(() => props.mode, stay);
const submit = async () => {
  if (props.saving || props.addressLookupLoading || !isRadiusValid.value) return;
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
const updatedAtLabel = computed(() =>
  props.station
    ? new Intl.DateTimeFormat('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      }).format(new Date(props.station.updatedAt))
    : '—',
);
const field = (name: keyof StationFormState, event: Event) =>
  props.onFieldChange(name, (event.target as HTMLInputElement).value);
const revealInvalid = (event: Event) => {
  (event.currentTarget as HTMLDetailsElement).open = true;
};
const canLookupAddress = computed(() => {
  const { latitude, longitude } = props.form;
  return (
    Boolean(latitude.trim() && longitude.trim()) &&
    Number.isFinite(Number(latitude)) &&
    Math.abs(Number(latitude)) <= 90 &&
    Number.isFinite(Number(longitude)) &&
    Math.abs(Number(longitude)) <= 180
  );
});
const hasDifferentSuggestion = computed(() =>
  Boolean(props.addressSuggestion && props.addressSuggestion !== props.form.address),
);
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
            >{{ mode === 'create' ? 'CREATE' : mode === 'edit' ? 'EDIT' : 'CHI TIẾT' }}</span
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
    <SidePanel
      v-if="showDiscardConfirm"
      class-name="station-discard-dialog"
      label="Hủy các thay đổi chưa lưu?"
      :busy="saving"
      content-sized
      :on-close="stay"
    >
      <div class="station-discard-content">
        <div
          class="dialog-icon"
          aria-hidden="true"
        >
          <AlertCircle :size="24" />
        </div>
        <h2>Hủy các thay đổi chưa lưu?</h2>
        <p>Nội dung bạn đang nhập sẽ bị mất nếu đóng lúc này.</p>
        <div class="dialog-actions">
          <button
            type="button"
            class="btn-secondary"
            :disabled="saving"
            autofocus
            @click="stay"
          >
            Ở lại
          </button>
          <button
            type="button"
            class="danger-action"
            :disabled="saving"
            @click="discard"
          >
            Hủy thay đổi
          </button>
        </div>
      </div>
    </SidePanel>
    <div
      v-if="!isFormOpen && station"
      class="station-details"
    >
      <section class="station-overview-card">
        <span class="station-overview-icon"><BusFront :size="22" /></span>
        <div class="station-overview-copy">
          <span>Điểm dừng vận hành</span>
          <strong>Trạm #{{ station.id }}</strong>
        </div>
        <span :class="['station-operating-badge', { inactive: !station.active }]">
          <span />{{ station.active ? 'Đang khai thác' : 'Ngừng khai thác' }}
        </span>
      </section>

      <section class="station-location-card">
        <span class="station-info-icon"><MapPin :size="18" /></span>
        <div>
          <span class="station-info-label">Địa điểm phục vụ</span>
          <strong>{{ station.address || 'Chưa có địa chỉ mô tả cụ thể' }}</strong>
        </div>
      </section>

      <section class="station-checkin-card">
        <div class="station-checkin-copy">
          <span class="station-info-label">Vùng tự động check-in</span>
          <strong>Bán kính nhận diện quanh trạm</strong>
          <small>Xe đi vào vùng này sẽ được hệ thống ghi nhận qua trạm.</small>
        </div>
        <div
          class="station-radius-visual"
          aria-label="Bán kính check-in"
        >
          <span class="station-radius-ring ring-outer" />
          <span class="station-radius-ring ring-inner" />
          <span class="station-radius-center"><Radio :size="15" /></span>
          <span class="station-radius-value">
            <strong>{{ station.checkinRadiusMeters }}</strong
            ><small>mét</small>
          </span>
        </div>
      </section>

      <div class="station-metric-grid">
        <section class="station-coordinate-card">
          <div class="station-card-heading">
            <span class="station-info-icon compact"><Crosshair :size="16" /></span>
            <div>
              <span class="station-info-label">Tọa độ bản đồ</span>
              <strong>Vị trí chính xác của trạm</strong>
            </div>
          </div>
          <div class="station-coordinate-values tabular-numbers">
            <span
              ><small>Vĩ độ</small><strong>{{ station.latitude.toFixed(6) }}</strong></span
            >
            <span
              ><small>Kinh độ</small><strong>{{ station.longitude.toFixed(6) }}</strong></span
            >
          </div>
        </section>

        <section class="station-updated-card">
          <span class="station-info-icon compact"><Clock3 :size="16" /></span>
          <div>
            <span class="station-info-label">Cập nhật gần nhất</span>
            <strong class="tabular-numbers">{{ updatedAtLabel }}</strong>
          </div>
        </section>
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
        <section
          v-if="addressLookupLoading || addressLookupError || hasDifferentSuggestion"
          class="address-lookup"
          aria-label="Địa chỉ từ bản đồ"
          :aria-busy="Boolean(addressLookupLoading)"
        >
          <p
            v-if="addressLookupLoading"
            role="status"
          >
            Đang lấy địa chỉ từ vị trí đã chọn…
          </p>
          <p
            v-else-if="addressLookupError"
            class="address-lookup-error"
            role="alert"
          >
            {{ addressLookupError }}
          </p>
          <p
            v-if="hasDifferentSuggestion && !addressLookupLoading"
            class="address-suggestion"
          >
            Gợi ý: {{ addressSuggestion }}
          </p>
          <div
            v-if="!addressLookupLoading && (addressLookupError || hasDifferentSuggestion)"
            class="address-lookup-actions"
          >
            <button
              v-if="addressLookupError && onRetryAddressLookup"
              type="button"
              :disabled="saving || addressLookupLoading || !canLookupAddress"
              @click="onRetryAddressLookup"
            >
              Thử lấy lại địa chỉ
            </button>
            <button
              v-if="hasDifferentSuggestion && onApplyAddressSuggestion"
              type="button"
              :disabled="saving || addressLookupLoading || (addressSuggestion?.length ?? 0) > 255"
              @click="onApplyAddressSuggestion"
            >
              Dùng địa chỉ này
            </button>
          </div>
        </section>
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
              saving ||
              addressLookupLoading ||
              !form.name.trim() ||
              !form.latitude ||
              !form.longitude ||
              !isRadiusValid
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

<style scoped>
dialog.station-discard-dialog {
  position: fixed;
  inset: 0;
  width: min(420px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 0;
  color: #f1f5f9;
  background: #0d1628;
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.45);
  overflow: hidden;
}
dialog.station-discard-dialog::backdrop {
  background: rgba(2, 6, 23, 0.65);
  backdrop-filter: blur(4px);
}
.station-discard-content {
  min-height: 0;
  padding: 24px;
  overflow-y: auto;
  text-align: center;
}
.station-discard-content h2 {
  font-size: 20px;
  line-height: 1.4;
}
.station-discard-content p {
  margin-top: 10px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}
.station-discard-content .dialog-actions {
  flex-wrap: wrap;
  justify-content: center;
}
.station-discard-content .dialog-actions button {
  min-height: 40px;
}
.station-discard-content .dialog-actions button:focus-visible {
  outline: 2px solid #67e8f9;
  outline-offset: 3px;
}
.address-lookup {
  padding: 12px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  border-radius: 12px;
  background: rgba(34, 211, 238, 0.04);
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}
.address-lookup p {
  margin: 0;
}
.address-lookup p + p {
  margin-top: 6px;
}
.address-lookup .address-lookup-error {
  color: #fbbf24;
}
.address-lookup .address-suggestion {
  color: #e2e8f0;
  overflow-wrap: anywhere;
}
.address-lookup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.address-lookup-actions button {
  padding: 6px 10px;
  border: 1px solid rgba(34, 211, 238, 0.25);
  border-radius: 8px;
  background: transparent;
  color: #67e8f9;
  font: inherit;
  cursor: pointer;
}
.address-lookup-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.address-lookup-actions button:focus-visible {
  outline: 2px solid #67e8f9;
  outline-offset: 2px;
}
</style>
