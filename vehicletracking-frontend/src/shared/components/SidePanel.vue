<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
import { registerToastModal } from '@/shared/notifications/toastLayer';
const props = withDefaults(
  defineProps<{
    className: string;
    label: string;
    busy?: boolean;
    contentSized?: boolean;
    onClose: () => void;
  }>(),
  { busy: false, contentSized: false },
);
const dialog = shallowRef<HTMLDialogElement | null>(null);
let opener: HTMLElement | null = null;
let releaseToastLayer: (() => void) | null = null;
onMounted(() => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  if (dialog.value) {
    dialog.value.showModal();
    releaseToastLayer = registerToastModal(dialog.value);
  }
});
onBeforeUnmount(() => {
  releaseToastLayer?.();
  releaseToastLayer = null;
  dialog.value?.close();
  if (opener?.isConnected) opener.focus();
});
function cancel() {
  if (!props.busy) props.onClose();
}
</script>
<template>
  <dialog
    ref="dialog"
    :class="['business-side-panel', className, { 'is-content-sized': contentSized }]"
    :aria-label="label"
    @cancel.prevent="cancel"
  >
    <slot />
  </dialog>
</template>
