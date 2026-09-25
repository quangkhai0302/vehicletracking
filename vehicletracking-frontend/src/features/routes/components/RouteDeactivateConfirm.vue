<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef } from 'vue';
import type { RouteSummary } from '@/features/routes/types/route';
import { registerToastModal } from '@/shared/notifications/toastLayer';

const props = defineProps<{
  route: RouteSummary;
  busy: boolean;
  onClose: () => void;
  onConfirm: () => void;
}>();

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
    class="schedule-confirm"
    aria-labelledby="route-deactivate-title"
    @cancel.prevent="!props.busy && props.onClose()"
  >
    <h2 id="route-deactivate-title">Xác nhận tạm dừng tuyến</h2>
    <p>
      Bạn có chắc muốn tạm dừng tuyến <strong>{{ route.name }}</strong
      >? Các chuyến đi theo lịch trình của tuyến này sẽ không thể khởi hành.
    </p>
    <div class="dialog-actions">
      <button
        type="button"
        class="schedule-button-secondary"
        :disabled="busy"
        autofocus
        @click="onClose"
      >
        Hủy
      </button>
      <button
        type="button"
        class="schedule-button-danger"
        :disabled="busy"
        @click="onConfirm"
      >
        {{ busy ? 'Đang xử lý…' : 'Tạm dừng tuyến' }}
      </button>
    </div>
  </dialog>
</template>
