<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef, ref } from 'vue';
import { registerToastModal } from '@/shared/notifications/toastLayer';
import type { NotificationSeverity } from '@/features/reports/types/notifications';
import type { SimulationIncidentType } from '../api/incidents';

const props = defineProps<{
  busy: boolean;
  error: string | null;
  onSubmit: (input: {
    type: SimulationIncidentType;
    severity: NotificationSeverity;
    detail: string;
  }) => Promise<boolean>;
  onClose: () => void;
}>();

const dialog = shallowRef<HTMLDialogElement | null>(null);
const type = ref<SimulationIncidentType>('VEHICLE_BREAKDOWN');
const severity = ref<NotificationSeverity>('MAJOR');
const detail = ref('');
let releaseToastLayer: (() => void) | null = null;

onMounted(() => {
  dialog.value?.showModal();
  if (dialog.value) releaseToastLayer = registerToastModal(dialog.value);
});
onBeforeUnmount(() => {
  releaseToastLayer?.();
  dialog.value?.close();
});

async function submit(event: Event) {
  event.preventDefault();
  const done = await props.onSubmit({ type: type.value, severity: severity.value, detail: detail.value.trim() });
  if (done) props.onClose();
}
</script>

<template>
  <dialog
    ref="dialog"
    class="simulation-incident-dialog"
    aria-labelledby="simulation-incident-title"
    @cancel.prevent="!busy && onClose()"
  >
    <form @submit="submit">
      <h2 id="simulation-incident-title">Ghi nhận sự cố mô phỏng</h2>
      <p>Xe sẽ tạm dừng tại vị trí mô phỏng hiện tại. Đây không phải vị trí GPS thực tế.</p>
      <label>
        <span>Loại sự cố</span>
        <select v-model="type" :disabled="busy">
          <option value="VEHICLE_BREAKDOWN">Xe gặp sự cố</option>
          <option value="EMERGENCY_STOP">Dừng khẩn cấp</option>
          <option value="ROAD_BLOCKED">Đường bị chặn</option>
          <option value="OTHER">Sự cố khác</option>
        </select>
      </label>
      <label>
        <span>Mức độ</span>
        <select v-model="severity" :disabled="busy">
          <option value="MAJOR">Cao</option>
          <option value="CRITICAL">Nghiêm trọng</option>
        </select>
      </label>
      <label>
        <span>Mô tả <small>(không bắt buộc)</small></span>
        <textarea v-model="detail" maxlength="500" rows="3" :disabled="busy" />
      </label>
      <p v-if="error" class="simulation-incident-error" role="alert">{{ error }}</p>
      <div class="simulation-incident-actions">
        <button type="button" :disabled="busy" @click="onClose">Quay lại</button>
        <button type="submit" :disabled="busy">{{ busy ? 'Đang ghi nhận…' : 'Ghi nhận sự cố' }}</button>
      </div>
    </form>
  </dialog>
</template>
