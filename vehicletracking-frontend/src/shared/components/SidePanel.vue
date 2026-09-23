<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
const props = withDefaults(
  defineProps<{ className: string; label: string; busy?: boolean; onClose: () => void }>(),
  { busy: false },
);
const dialog = shallowRef<HTMLDialogElement | null>(null);
let opener: HTMLElement | null = null;
onMounted(() => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  dialog.value?.showModal();
});
onBeforeUnmount(() => {
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
    :class="`business-side-panel ${className}`"
    :aria-label="label"
    @cancel.prevent="cancel"
  >
    <slot />
  </dialog>
</template>
