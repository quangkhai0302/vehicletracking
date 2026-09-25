<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef } from 'vue';
import type { Station } from '@/features/stations/types/station';
import { registerToastModal } from '@/shared/notifications/toastLayer';
defineProps<{ station: Station; saving: boolean; onCancel: () => void; onConfirm: () => void }>();
const dialog = shallowRef<HTMLDialogElement | null>(null);
let releaseToastLayer: (() => void) | null = null;
onMounted(() => {
  if (!dialog.value) return;
  dialog.value.showModal();
  releaseToastLayer = registerToastModal(dialog.value);
});
onBeforeUnmount(() => {
  releaseToastLayer?.();
  dialog.value?.close();
});
</script>
<template>
  <dialog
    ref="dialog"
    class="confirmation-dialog"
    aria-labelledby="deactivate-title"
    @cancel.prevent="!saving && onCancel()"
  >
    <div
      class="dialog-icon"
      aria-hidden="true"
    >
      !
    </div>
    <h2 id="deactivate-title">Ngừng sử dụng trạm?</h2>
    <div class="dialog-station-info">
      <strong>{{ station.name }}</strong>
      <p>{{ station.address }}</p>
    </div>
    <p class="dialog-explanation">
      Trạm sẽ không được dùng cho tuyến mới. Dữ liệu lịch sử vẫn được lưu trữ.
    </p>
    <div class="dialog-actions">
      <button
        class="secondary-action"
        :disabled="saving"
        autofocus
        @click="onCancel"
      >
        Hủy bỏ</button
      ><button
        class="danger-action"
        :disabled="saving"
        @click="onConfirm"
      >
        {{ saving ? 'Đang xử lý…' : 'Xác nhận ngừng sử dụng' }}
      </button>
    </div>
  </dialog>
</template>
