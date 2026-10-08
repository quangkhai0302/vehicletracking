<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef } from 'vue';
import { registerToastModal } from '@/shared/notifications/toastLayer';
const note = defineModel<string>({ required: true });
const props = defineProps<{ busy: boolean; error: string | null; onSubmit: () => Promise<void>; onClose: () => void }>();
const dialog = shallowRef<HTMLDialogElement | null>(null);
let release: (() => void) | undefined;
onMounted(() => { dialog.value?.showModal(); if (dialog.value) release = registerToastModal(dialog.value); });
onBeforeUnmount(() => { release?.(); dialog.value?.close(); });
</script>
<template>
  <dialog ref="dialog" class="simulation-incident-dialog" aria-labelledby="incident-resolution-title" @cancel.prevent="!busy && onClose()">
    <form @submit.prevent="props.onSubmit">
      <h2 id="incident-resolution-title">Xác nhận đã xử lý xong</h2>
      <p>Admin sẽ nhận thông tin xử lý. Chuyến tự tiếp tục khi không còn sự cố đang chờ xử lý.</p>
      <label><span>Ghi chú xử lý <small>(không bắt buộc)</small></span><textarea v-model="note" maxlength="500" rows="3" :disabled="busy" /></label>
      <p v-if="error" class="simulation-incident-error" role="alert">{{ error }}</p>
      <div class="simulation-incident-actions">
        <button type="button" :disabled="busy" @click="onClose">Quay lại</button>
        <button type="submit" :disabled="busy">{{ busy ? 'Đang lưu…' : 'Đã xử lý xong' }}</button>
      </div>
    </form>
  </dialog>
</template>
