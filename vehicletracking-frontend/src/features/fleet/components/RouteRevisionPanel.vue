<script setup lang="ts">
import { onScopeDispose, ref, shallowRef, watch } from 'vue';
import { GitBranch, RefreshCw } from '@lucide/vue';
import { fetchTripRevisions, supersedeTripRevision } from '@/features/reports/api/notifications';
import type { RouteRevision } from '@/features/reports/types/notifications';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifySuccess } from '@/shared/notifications/toast';
const props = defineProps<{ tripId: number }>();
const revisions = shallowRef<RouteRevision[]>([]),
  loading = ref(true),
  error = ref<string | null>(null),
  confirm = shallowRef<RouteRevision | null>(null),
  busy = ref(false),
  attempt = ref(0);
useErrorToast(error);
let disposed = false;
onScopeDispose(() => {
  disposed = true;
});
watch(
  [() => props.tripId, attempt],
  ([id], _old, cleanup) => {
    const controller = new AbortController();
    fetchTripRevisions(id, controller.signal)
      .then((next) => {
        if (!controller.signal.aborted) revisions.value = next;
      })
      .catch((err) => {
        if (!controller.signal.aborted)
          error.value = err instanceof Error ? err.message : 'Không thể tải phiên bản tuyến.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
function retry() {
  loading.value = true;
  error.value = null;
  attempt.value++;
}
async function supersede() {
  if (!confirm.value || busy.value) return;
  const id = props.tripId;
  busy.value = true;
  error.value = null;
  try {
    const updated = await supersedeTripRevision(id, confirm.value.id);
    if (!disposed && props.tripId === id) {
      revisions.value = revisions.value.map((item) => (item.id === updated.id ? updated : item));
      confirm.value = null;
      notifySuccess('Đã ngừng áp dụng lần đổi tuyến.');
    }
  } catch (err) {
    if (!disposed && props.tripId === id)
      error.value =
        err instanceof Error ? err.message : 'Không thể ngừng hiệu lực phiên bản tuyến.';
  } finally {
    if (!disposed) busy.value = false;
  }
}
</script>
<template>
  <section
    v-if="error || revisions.length > 0"
    class="trip-revision-panel"
    aria-label="Thay đổi tuyến đường"
  >
    <div class="trip-section-heading">
      <span><GitBranch :size="15" /> Thay đổi tuyến đường</span
      ><button
        class="fleet-icon-button"
        :disabled="loading"
        aria-label="Tải lại thay đổi tuyến"
        @click="retry"
      >
        <RefreshCw :size="14" />
      </button>
    </div>
    <p
      v-if="loading"
      class="fleet-loading"
    >
      Đang tải phiên bản…
    </p>
    <template v-if="!loading && !error"
      ><article
        v-for="item in revisions"
        :key="item.id"
        :class="['trip-revision-row', item.status.toLowerCase()]"
      >
        <div>
          <strong>Đổi tuyến lần {{ item.revisionNumber }}</strong
          ><span :class="`revision-status ${item.status.toLowerCase()}`">{{
            item.status === 'ACTIVE' ? 'Đang áp dụng' : 'Đã ngừng áp dụng'
          }}</span>
        </div>
        <p>
          {{
            item.reasonDetail ||
            (item.reasonCode === 'ROAD_CLOSURE' ? 'Đường bị đóng' : 'Chậm giao thông')
          }}
        </p>
        <small
          >{{ displayTripTime(item.createdAt) }} · Còn lại
          {{ Math.ceil(item.revisedRemainingSeconds / 60) }} phút</small
        ><button
          v-if="item.status === 'ACTIVE'"
          class="fleet-text-button"
          :disabled="busy"
          @click="confirm = item"
        >
          Ngừng áp dụng
        </button>
      </article></template
    >
    <FleetConfirmDialog
      v-if="confirm"
      :title="`Ngừng áp dụng lần đổi tuyến ${confirm.revisionNumber}?`"
      message="Thao tác chỉ ngừng áp dụng lần đổi tuyến này, không tạo tuyến mới và không xóa lịch sử."
      confirm-label="Xác nhận ngừng áp dụng"
      :busy="busy"
      :on-close="() => (confirm = null)"
      :on-confirm="supersede"
    />
  </section>
</template>
