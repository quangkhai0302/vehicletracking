<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
import { registerToastModal } from '@/shared/notifications/toastLayer';
const props = defineProps<{
  title: string;
  message: string;
  confirmLabel: string;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
  confirmDisabled?: boolean;
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
    class="confirmation-dialog fleet-confirm"
    aria-labelledby="fleet-confirm-title"
    @cancel.prevent="!props.busy && props.onClose()"
  >
    <h2 id="fleet-confirm-title">{{ title }}</h2>
    <p>{{ message }}</p>
    <slot />
    <div class="dialog-actions">
      <button
        class="btn-secondary"
        :disabled="busy"
        autofocus
        @click="onClose"
      >
        Quay lại</button
      ><button
        class="danger-action"
        :disabled="busy || confirmDisabled"
        @click="onConfirm"
      >
        {{ busy ? 'Đang xử lý…' : confirmLabel }}
      </button>
    </div>
  </dialog>
</template>
