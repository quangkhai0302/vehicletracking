<script setup lang="ts">
import { onMounted, onBeforeUnmount, shallowRef } from 'vue';
const props = defineProps<{
  title: string;
  message: string;
  confirmLabel: string;
  busy: boolean;
  error?: string | null;
  onConfirm: () => void;
  onClose: () => void;
  confirmDisabled?: boolean;
}>();
const dialog = shallowRef<HTMLDialogElement | null>(null);
onMounted(() => dialog.value?.showModal());
onBeforeUnmount(() => dialog.value?.close());
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
    <p
      v-if="error"
      class="inline-error"
      role="alert"
    >
      {{ error }}
    </p>
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
