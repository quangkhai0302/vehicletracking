<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowLeft, CarFront, Save, Scooter } from '@lucide/vue';
import type { Driver, FleetVehicle, VehicleInput, VehicleType } from '@/features/fleet/types/fleet';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
const props = defineProps<{
  vehicle: FleetVehicle | null;
  drivers: Driver[];
  busy: boolean;
  onSave: (input: VehicleInput, id?: number) => Promise<boolean>;
  onClose: () => void;
}>();
const plateNumber = ref(props.vehicle?.plateNumber ?? ''),
  name = ref(props.vehicle?.name ?? ''),
  description = ref(props.vehicle?.description ?? '');
const vehicleType = ref<VehicleType>(props.vehicle?.vehicleType ?? 'CAR'),
  driverId = ref(props.vehicle?.driver ? String(props.vehicle.driver.id) : ''),
  confirm = ref(false);
const dirty = computed(
  () =>
    plateNumber.value !== (props.vehicle?.plateNumber ?? '') ||
    name.value !== (props.vehicle?.name ?? '') ||
    description.value !== (props.vehicle?.description ?? '') ||
    vehicleType.value !== (props.vehicle?.vehicleType ?? 'CAR') ||
    (props.vehicle !== null &&
      driverId.value !== (props.vehicle.driver ? String(props.vehicle.driver.id) : '')),
);
const normalized = computed(() => plateNumber.value.toUpperCase().replace(/[\s.-]/g, ''));
const valid = computed(() => !!name.value.trim() && /^[A-Z0-9]{1,20}$/.test(normalized.value));
function close() {
  if (dirty.value) confirm.value = true;
  else props.onClose();
}
function submit() {
  if (!props.busy && valid.value)
    void props.onSave(
      {
        plateNumber: plateNumber.value,
        name: name.value.trim(),
        description: description.value.trim() || null,
        vehicleType: vehicleType.value,
        driverId: props.vehicle && driverId.value ? Number(driverId.value) : null,
      },
      props.vehicle?.id,
    );
}
</script>
<template>
  <section
    class="fleet-editor"
    :aria-label="vehicle ? 'Chỉnh sửa xe' : 'Thêm xe mới'"
  >
    <div class="fleet-heading">
      <button
        class="fleet-icon-button"
        aria-label="Đóng biểu mẫu xe"
        :disabled="busy"
        @click="close"
      >
        <ArrowLeft :size="18" />
      </button>
      <div>
        <span class="panel-eyebrow">DANH MỤC PHƯƠNG TIỆN</span>
        <h2>{{ vehicle ? 'Chỉnh sửa xe' : 'Thêm xe mới' }}</h2>
      </div>
    </div>
    <form
      id="vehicle-form"
      class="fleet-form-body"
      @submit.prevent="submit"
    >
      <fieldset :disabled="busy">
        <fieldset class="vehicle-type-field">
          <legend>Loại phương tiện *</legend>
          <div class="vehicle-type-options">
            <label :data-selected="vehicleType === 'CAR'"
              ><input
                v-model="vehicleType"
                type="radio"
                name="vehicleType"
                value="CAR"
              /><CarFront :size="22" /><span
                ><strong>Ô tô</strong><small>Xe con, xe tải hoặc xe buýt</small></span
              ></label
            ><label :data-selected="vehicleType === 'MOTORCYCLE'"
              ><input
                v-model="vehicleType"
                type="radio"
                name="vehicleType"
                value="MOTORCYCLE"
              /><Scooter :size="22" /><span
                ><strong>Xe máy</strong><small>Xe mô tô hoặc xe gắn máy</small></span
              ></label
            >
          </div>
        </fieldset>
        <label
          >Biển số *<input
            v-model="plateNumber"
            name="plateNumber"
            maxlength="20"
            required
            pattern="[A-Za-z0-9 .\-]+"
            placeholder="Ví dụ: 51B-123.45"
        /></label>
        <p class="fleet-help">Biển số được lưu viết hoa, bỏ khoảng trắng, dấu chấm và gạch nối.</p>
        <label
          >Tên xe *<input
            v-model="name"
            name="name"
            maxlength="100"
            required
            placeholder="Ví dụ: Xe buýt 01"
        /></label>
        <label v-if="vehicle"
          >Tài xế mặc định<select v-model="driverId">
            <option value="">Chưa gán tài xế</option>
            <option
              v-for="driver in drivers.filter(
                (item) => item.active || item.id === vehicle?.driver?.id,
              )"
              :key="driver.id"
              :value="String(driver.id)"
            >
              {{ driver.fullName }} · {{ driver.licenseNumber }}
            </option>
          </select></label
        >
        <label
          >Mô tả<textarea
            v-model="description"
            name="description"
            maxlength="255"
            rows="3"
            placeholder="Thông tin nhận diện xe"
          />
        </label>
        <p class="availability-note">Vị trí và vận tốc sẽ hiển thị khi có nguồn dữ liệu xe.</p>
      </fieldset>
    </form>
    <div class="fleet-footer">
      <button
        class="btn-secondary"
        :disabled="busy"
        @click="close"
      >
        Hủy</button
      ><button
        class="btn-primary"
        type="submit"
        form="vehicle-form"
        :disabled="busy || !valid"
      >
        <Save :size="15" />{{ busy ? 'Đang lưu…' : vehicle ? 'Lưu xe' : 'Thêm xe' }}
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirm"
      title="Bỏ thay đổi của xe?"
      message="Thông tin chưa lưu sẽ bị xóa."
      confirm-label="Bỏ thay đổi"
      :busy="false"
      :on-confirm="onClose"
      :on-close="() => (confirm = false)"
    />
  </section>
</template>
