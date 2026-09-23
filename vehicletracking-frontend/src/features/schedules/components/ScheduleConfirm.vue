<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, shallowRef } from 'vue';
import type { TripSchedule } from '../types/schedule';
const props = defineProps<{
  schedule: TripSchedule;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onConfirm: () => void;
}>();
const dialog = shallowRef<HTMLDialogElement | null>(null);
const action = computed(() => (props.schedule.enabled ? 'Tạm dừng' : 'Bật lại'));
onMounted(() => dialog.value?.showModal());
onBeforeUnmount(() => dialog.value?.close());
</script>
<template>
  <dialog
    ref="dialog"
    class="schedule-confirm"
    aria-labelledby="schedule-confirm-title"
    @cancel.prevent="!busy && onClose()"
  >
    <h2 id="schedule-confirm-title">{{ action }} lịch chạy?</h2>
    <p>
      {{
        schedule.enabled
          ? 'Hệ thống sẽ không tạo thêm chuyến mới từ lịch này. Những chuyến đã tạo vẫn được giữ nguyên.'
          : 'Hệ thống sẽ xét tạo chuyến mới theo cấu hình lịch này.'
      }}
    </p>
    <p
      v-if="error"
      class="schedule-inline-error"
      role="alert"
    >
      {{ error }}
    </p>
    <div>
      <button
        type="button"
        class="schedule-button-secondary"
        :disabled="busy"
        autofocus
        @click="onClose"
      >
        Quay lại</button
      ><button
        type="button"
        :class="schedule.enabled ? 'schedule-button-danger' : 'schedule-button-primary'"
        :disabled="busy"
        @click="onConfirm"
      >
        {{ busy ? 'Đang xử lý…' : action }}
      </button>
    </div>
  </dialog>
</template>
