<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import {
  DispatchApiError, fetchDispatchInbox,
  fetchDriverAssignmentRequests, readDispatchInboxItem,
  acceptDriverAssignmentRequest, declineDriverAssignmentRequest,
} from '../api/dispatch';
import {
  type DriverAssignmentRequest,
  type DriverDispatchInboxItem,
} from '../types/dispatch';
import '../styles/dispatch.css';

const emit = defineEmits<{ changed: [] }>();
const assignmentRequests = shallowRef<DriverAssignmentRequest[]>([]);
const inbox = shallowRef<DriverDispatchInboxItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const busy = ref<string | null>(null);
const assignmentToDecline = ref<string | null>(null);
const assignmentReason = ref('');
let controller: AbortController | null = null;
let timer: ReturnType<typeof setInterval> | null = null;

async function refresh() {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  try {
    const [inboxResult, assignmentsResult] = await Promise.allSettled([
      fetchDispatchInbox(request.signal),
      fetchDriverAssignmentRequests(request.signal),
    ]);
    if (request.signal.aborted) return;
    const problems: string[] = [];
    if (inboxResult.status === 'fulfilled') inbox.value = inboxResult.value;
    else problems.push('Không thể tải hộp công việc.');
    if (assignmentsResult.status === 'fulfilled') assignmentRequests.value = assignmentsResult.value;
    else problems.push('Không thể tải yêu cầu nhận chuyến.');
    error.value = problems.length ? problems.join(' ') : null;
  } catch (cause) {
    if (!request.signal.aborted) error.value = cause instanceof Error ? cause.message : 'Không thể tải điều phối.';
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}
onMounted(() => {
  void refresh();
  timer = setInterval(() => {
    if (!busy.value && document.visibilityState === 'visible') void refresh();
  }, 5000);
});
onBeforeUnmount(() => {
  controller?.abort();
  if (timer) clearInterval(timer);
});
function time(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(value));
}
async function mutate(key: string, action: () => Promise<unknown>, refreshTrips = false) {
  if (busy.value) return;
  busy.value = key;
  let message: string | null = null;
  try {
    await action();
    error.value = null;
    if (refreshTrips) emit('changed');
  } catch (cause) {
    message = cause instanceof DispatchApiError && cause.status === 409
      ? `Dữ liệu điều phối đã thay đổi. ${cause.message} Đã tải lại trạng thái mới.`
      : cause instanceof Error ? cause.message : 'Không thể thực hiện thao tác.';
  } finally {
    busy.value = null;
    await refresh();
    if (message) error.value = message;
  }
}
function requestDecline(requestId: string) {
  assignmentToDecline.value = requestId;
  assignmentReason.value = '';
}
function confirmAssignmentDecline() {
  const requestId = assignmentToDecline.value;
  const reason = assignmentReason.value.trim();
  if (!requestId || reason.length < 3) return;
  assignmentToDecline.value = null;
  assignmentReason.value = '';
  void mutate(
    `decline-assignment:${requestId}`,
    () => declineDriverAssignmentRequest(requestId, reason),
  );
}
</script>

<template>
  <section class="driver-dispatch-workspace" aria-label="Công việc điều phối">
    <div class="driver-dispatch-heading">
      <div><small>ĐIỀU PHỐI CHUYẾN</small><h2>Yêu cầu nhận chuyến</h2></div>
      <button type="button" :disabled="loading || !!busy" @click="refresh">Làm mới</button>
    </div>
    <p v-if="loading" role="status">Đang tải yêu cầu nhận chuyến…</p>
    <p v-if="error" class="dispatch-error" role="alert">{{ error }}</p>
    <div v-if="!loading && !assignmentRequests.length && !inbox.length" class="dispatch-empty">
      Chưa có yêu cầu nhận chuyến mới.
    </div>
    <div v-if="assignmentRequests.length" class="driver-assignment-requests">
      <h3>Yêu cầu nhận chuyến tức thời</h3>
      <article v-for="assignment in assignmentRequests" :key="assignment.requestId">
        <div>
          <strong>#{{ assignment.tripId }} · {{ assignment.routeName }}</strong>
          <span>{{ assignment.vehiclePlate }} · Tạo {{ time(assignment.tripCreatedAt) }} · Gửi lúc
            {{ time(assignment.requestedAt) }}</span>
        </div>
        <p>Điều phối viên đang chờ bạn xác nhận nhận chuyến.</p>
        <div class="driver-dispatch-actions">
          <button type="button" class="dispatch-primary" :disabled="!!busy"
            @click="mutate(`accept-assignment:${assignment.requestId}`, () => acceptDriverAssignmentRequest(assignment.requestId), true)">
            {{ busy === `accept-assignment:${assignment.requestId}` ? 'Đang nhận chuyến…' : 'Nhận chuyến' }}
          </button>
          <button type="button" class="dispatch-secondary" :disabled="!!busy"
            @click="requestDecline(assignment.requestId)">Từ chối</button>
        </div>
      </article>
    </div>
    <div class="driver-dispatch-inbox">
      <h3>Hộp công việc</h3>
      <p v-if="!inbox.length">Chưa có thông báo điều phối.</p>
      <ul v-else><li v-for="item in inbox" :key="item.id" :class="{ unread: !item.readAt }">
        <div><strong>{{ item.title }}</strong><small>{{ time(item.createdAt) }}</small></div>
        <p v-if="item.detail">{{ item.detail }}</p>
        <button v-if="!item.readAt" type="button" :disabled="!!busy"
          @click="mutate(`read:${item.id}`, () => readDispatchInboxItem(item.id))">Đã đọc</button>
      </li></ul>
    </div>
    <FleetConfirmDialog v-if="assignmentToDecline !== null" title="Từ chối nhận chuyến?"
      message="Yêu cầu sẽ được chuyển cho điều phối viên để chọn tài xế khác."
      confirm-label="Xác nhận từ chối" :busy="!!busy" :confirm-disabled="assignmentReason.trim().length < 3"
      :on-close="() => (assignmentToDecline = null)" :on-confirm="confirmAssignmentDecline">
      <label class="dispatch-reason">Lý do *<textarea v-model="assignmentReason" maxlength="500" rows="3"
        placeholder="Nhập lý do từ chối (ít nhất 3 ký tự)" /></label>
    </FleetConfirmDialog>
  </section>
</template>
