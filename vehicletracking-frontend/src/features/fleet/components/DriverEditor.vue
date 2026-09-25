<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowLeft, Save } from '@lucide/vue';
import type { Driver, DriverInput } from '@/features/fleet/types/fleet';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
const props = defineProps<{
  driver: Driver | null;
  busy: boolean;
  onSave: (input: DriverInput, id?: number) => Promise<boolean>;
  onClose: () => void;
}>();
const fullName = ref(props.driver?.fullName ?? ''),
  phoneNumber = ref(props.driver?.phoneNumber ?? ''),
  licenseNumber = ref(props.driver?.licenseNumber ?? ''),
  confirm = ref(false);
const normalizedLicense = computed(() => licenseNumber.value.trim().toUpperCase());
const valid = computed(
  () =>
    !!fullName.value.trim() &&
    /^\+?[0-9][0-9 .()-]{6,19}$/.test(phoneNumber.value.trim()) &&
    /^[A-Z0-9.-]{1,50}$/.test(normalizedLicense.value),
);
const dirty = computed(
  () =>
    fullName.value !== (props.driver?.fullName ?? '') ||
    phoneNumber.value !== (props.driver?.phoneNumber ?? '') ||
    licenseNumber.value !== (props.driver?.licenseNumber ?? ''),
);
function close() {
  if (dirty.value) confirm.value = true;
  else props.onClose();
}
function submit() {
  if (!props.busy && valid.value)
    void props.onSave(
      {
        fullName: fullName.value.trim(),
        phoneNumber: phoneNumber.value.trim(),
        licenseNumber: normalizedLicense.value,
      },
      props.driver?.id,
    );
}
</script>
<template>
  <section
    class="fleet-editor"
    :aria-label="driver ? 'Chỉnh sửa tài xế' : 'Thêm tài xế mới'"
  >
    <div class="fleet-heading">
      <button
        class="fleet-icon-button"
        aria-label="Đóng biểu mẫu tài xế"
        :disabled="busy"
        @click="close"
      >
        <ArrowLeft :size="18" />
      </button>
      <div>
        <span class="panel-eyebrow">NHÂN SỰ VẬN HÀNH</span>
        <h2>{{ driver ? 'Chỉnh sửa tài xế' : 'Thêm tài xế mới' }}</h2>
      </div>
    </div>
    <form
      id="driver-form"
      class="fleet-form-body"
      @submit.prevent="submit"
    >
      <fieldset :disabled="busy">
        <label
          >Họ và tên *<input
            v-model="fullName"
            name="fullName"
            maxlength="100"
            required
            placeholder="Ví dụ: Nguyễn Văn An"
        /></label>
        <label
          >Số điện thoại *<input
            v-model="phoneNumber"
            name="phoneNumber"
            minlength="7"
            maxlength="20"
            required
            placeholder="Ví dụ: 0901 234 567"
        /></label>
        <label
          >Số GPLX *<input
            v-model="licenseNumber"
            name="licenseNumber"
            maxlength="50"
            required
            pattern="[A-Za-z0-9.-]+"
            placeholder="Ví dụ: B2-123456"
        /></label>
        <p class="fleet-help">
          Số GPLX được chuẩn hóa viết hoa và không thể trùng với tài xế đã ngừng sử dụng.
        </p>
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
        form="driver-form"
        :disabled="busy || !valid"
      >
        <Save :size="15" />{{ busy ? 'Đang lưu…' : driver ? 'Lưu tài xế' : 'Thêm tài xế' }}
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirm"
      title="Bỏ thay đổi của tài xế?"
      message="Thông tin chưa lưu sẽ bị xóa."
      confirm-label="Bỏ thay đổi"
      :busy="false"
      :on-confirm="onClose"
      :on-close="() => (confirm = false)"
    />
  </section>
</template>
