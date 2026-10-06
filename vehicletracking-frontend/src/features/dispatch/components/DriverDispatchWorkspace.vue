<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  useId,
  type CSSProperties,
} from 'vue';
import {
  ArrowLeft,
  Bell,
  ChevronDown,
  ChevronRight,
  KeyRound,
  LogOut,
  RefreshCw,
  UserRound,
  X,
} from '@lucide/vue';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import {
  DispatchApiError,
  fetchDispatchInbox,
  fetchDriverAssignmentRequests,
  readDispatchInboxItem,
  acceptDriverAssignmentRequest,
  declineDriverAssignmentRequest,
} from '../api/dispatch';
import { type DriverAssignmentRequest, type DriverDispatchInboxItem } from '../types/dispatch';
import '../styles/dispatch.css';

const props = defineProps<{ accountName: string }>();
const emit = defineEmits<{ changed: []; passwordChange: []; signOut: [] }>();
const assignmentRequests = shallowRef<DriverAssignmentRequest[]>([]);
const inbox = shallowRef<DriverDispatchInboxItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const busy = ref<string | null>(null);
const assignmentToDecline = ref<string | null>(null);
const assignmentReason = ref('');
const root = shallowRef<HTMLElement | null>(null);
const trigger = shallowRef<HTMLButtonElement | null>(null);
const panel = shallowRef<HTMLElement | null>(null);
const notificationEntry = shallowRef<HTMLButtonElement | null>(null);
const open = ref(false);
const notificationsOpen = ref(false);
const refreshing = ref(false);
const panelStyle = ref<CSSProperties>({});
const panelId = `driver-notifications-${useId()}`;
const unread = computed(() => inbox.value.filter((item) => item.readAt === null));
const attentionCount = computed(() => {
  const represented = new Set(
    unread.value
      .filter((item) => item.type === 'DIRECT_ASSIGNMENT_REQUESTED')
      .map((item) => item.assignmentRequestId),
  );
  return (
    unread.value.length +
    assignmentRequests.value.filter((item) => !represented.has(item.requestId)).length
  );
});
const triggerLabel = computed(
  () =>
    `Tài khoản ${props.accountName}, thông báo: ${unread.value.length} chưa đọc, ${assignmentRequests.value.length} yêu cầu chờ phản hồi${error.value ? ', có lỗi tải thông báo' : ''}`,
);
let controller: AbortController | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
let disposed = false;

function positionPanel() {
  if (!trigger.value) return;
  const anchor = trigger.value.getBoundingClientRect();
  const headerBottom =
    trigger.value.closest('.driver-portal-header')?.getBoundingClientRect().bottom ?? anchor.bottom;
  const viewportWidth = document.documentElement.clientWidth || window.innerWidth;
  const width = Math.min(notificationsOpen.value ? 420 : 320, viewportWidth - 24);
  const top = Math.max(
    12,
    Math.min(Math.max(anchor.bottom, headerBottom) + 10, window.innerHeight - 140),
  );
  panelStyle.value = {
    width: `${width}px`,
    left: `${Math.max(12, Math.min(anchor.right - width, viewportWidth - width - 12))}px`,
    top: `${top}px`,
    maxHeight: `${Math.min(600, Math.max(0, window.innerHeight - top - 12))}px`,
  };
}
function closePanel(restoreFocus = false) {
  open.value = false;
  notificationsOpen.value = false;
  if (restoreFocus) void nextTick(() => trigger.value?.focus());
}
async function togglePanel() {
  if (open.value) return closePanel(true);
  positionPanel();
  open.value = true;
  await nextTick();
  panel.value?.focus();
}
async function showNotifications(show: boolean) {
  notificationsOpen.value = show;
  await nextTick();
  positionPanel();
  await nextTick();
  if (show) panel.value?.focus();
  else notificationEntry.value?.focus();
}
function accountAction(action: 'passwordChange' | 'signOut') {
  closePanel();
  if (action === 'passwordChange') emit('passwordChange');
  else emit('signOut');
}
function outsideInteraction(event: Event) {
  if (!open.value || assignmentToDecline.value !== null) return;
  const target = event.target;
  if (target instanceof Node && !root.value?.contains(target) && !panel.value?.contains(target))
    closePanel();
}
function escapePanel(event: KeyboardEvent) {
  if (
    open.value &&
    assignmentToDecline.value === null &&
    event.key === 'Escape' &&
    !event.defaultPrevented
  ) {
    event.preventDefault();
    closePanel(true);
  }
}
function reposition(event: Event) {
  if (open.value && !(event.target instanceof Node && panel.value?.contains(event.target)))
    positionPanel();
}
async function refresh() {
  if (disposed) return;
  controller?.abort();
  const request = new AbortController();
  controller = request;
  refreshing.value = true;
  try {
    const [inboxResult, assignmentsResult] = await Promise.allSettled([
      fetchDispatchInbox(request.signal),
      fetchDriverAssignmentRequests(request.signal),
    ]);
    if (request.signal.aborted) return;
    const problems: string[] = [];
    if (inboxResult.status === 'fulfilled') inbox.value = inboxResult.value;
    else problems.push('Không thể tải hộp công việc.');
    if (assignmentsResult.status === 'fulfilled')
      assignmentRequests.value = assignmentsResult.value;
    else problems.push('Không thể tải yêu cầu nhận chuyến.');
    error.value = problems.length ? problems.join(' ') : null;
  } catch (cause) {
    if (!request.signal.aborted)
      error.value = cause instanceof Error ? cause.message : 'Không thể tải điều phối.';
  } finally {
    if (!request.signal.aborted) {
      loading.value = false;
      refreshing.value = false;
    }
  }
}
onMounted(() => {
  void refresh();
  document.addEventListener('pointerdown', outsideInteraction);
  document.addEventListener('focusin', outsideInteraction);
  document.addEventListener('keydown', escapePanel);
  document.addEventListener('scroll', reposition, true);
  window.addEventListener('resize', reposition);
  timer = setInterval(() => {
    if (!busy.value && document.visibilityState === 'visible') void refresh();
  }, 5000);
});
onBeforeUnmount(() => {
  disposed = true;
  controller?.abort();
  if (timer) clearInterval(timer);
  document.removeEventListener('pointerdown', outsideInteraction);
  document.removeEventListener('focusin', outsideInteraction);
  document.removeEventListener('keydown', escapePanel);
  document.removeEventListener('scroll', reposition, true);
  window.removeEventListener('resize', reposition);
});
function time(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value));
}
async function mutate(key: string, action: () => Promise<unknown>, refreshTrips = false) {
  if (busy.value) return;
  busy.value = key;
  let message: string | null = null;
  try {
    await action();
    if (disposed) return;
    error.value = null;
    if (refreshTrips) emit('changed');
  } catch (cause) {
    message =
      cause instanceof DispatchApiError && cause.status === 409
        ? `Dữ liệu điều phối đã thay đổi. ${cause.message} Đã tải lại trạng thái mới.`
        : cause instanceof Error
          ? cause.message
          : 'Không thể thực hiện thao tác.';
  } finally {
    if (!disposed) {
      await refresh();
      if (!disposed) {
        busy.value = null;
        if (message) error.value = message;
      }
    }
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
  void mutate(`decline-assignment:${requestId}`, () =>
    declineDriverAssignmentRequest(requestId, reason),
  );
}
</script>

<template>
  <div
    ref="root"
    class="driver-notifications"
  >
    <button
      ref="trigger"
      type="button"
      class="driver-account-trigger"
      :class="{ 'has-error': error }"
      :aria-label="triggerLabel"
      :title="triggerLabel"
      aria-haspopup="dialog"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="togglePanel"
    >
      <UserRound
        :size="18"
        aria-hidden="true"
      />
      <span class="driver-account-name">{{ accountName }}</span>
      <span class="driver-account-mobile-label">Tài khoản</span>
      <ChevronDown
        :size="15"
        aria-hidden="true"
      />
      <span
        v-if="attentionCount"
        class="driver-notifications-badge"
        aria-hidden="true"
      >
        {{ attentionCount > 99 ? '99+' : attentionCount }}
      </span>
      <span
        v-else-if="error"
        class="driver-notifications-badge"
        aria-hidden="true"
        >!</span
      >
    </button>
    <Teleport to="body">
      <section
        v-if="open"
        :id="panelId"
        ref="panel"
        class="driver-notifications-panel business-ui"
        role="dialog"
        :aria-labelledby="`${panelId}-title`"
        tabindex="-1"
        :style="panelStyle"
      >
        <div class="driver-dispatch-heading">
          <div class="driver-account-heading">
            <button
              v-if="notificationsOpen"
              type="button"
              class="driver-account-back"
              aria-label="Quay lại tài khoản"
              @click="showNotifications(false)"
            >
              <ArrowLeft :size="18" />
            </button>
            <div>
              <h2 :id="`${panelId}-title`">{{ notificationsOpen ? 'Thông báo' : 'Tài khoản' }}</h2>
              <p v-if="notificationsOpen">
                {{ unread.length }} chưa đọc · {{ assignmentRequests.length }} yêu cầu chờ
              </p>
              <p
                v-else
                class="driver-account-identity"
              >
                {{ accountName }} · Tài xế
              </p>
            </div>
          </div>
          <div class="driver-notifications-tools">
            <button
              v-if="notificationsOpen"
              type="button"
              :disabled="refreshing || !!busy"
              aria-label="Làm mới thông báo"
              title="Làm mới thông báo"
              @click="refresh"
            >
              <RefreshCw
                :size="17"
                :class="{ 'is-refreshing': refreshing }"
              />
            </button>
            <button
              type="button"
              :aria-label="notificationsOpen ? 'Đóng thông báo' : 'Đóng tài khoản'"
              @click="closePanel(true)"
            >
              <X :size="18" />
            </button>
          </div>
        </div>
        <div
          v-if="!notificationsOpen"
          class="driver-account-actions"
        >
          <button
            ref="notificationEntry"
            type="button"
            class="driver-account-notifications"
            @click="showNotifications(true)"
          >
            <Bell
              :size="19"
              aria-hidden="true"
            />
            <span
              ><strong>Thông báo</strong><small v-if="error">Có lỗi tải thông báo</small
              ><small v-else
                >{{ unread.length }} chưa đọc · {{ assignmentRequests.length }} yêu cầu chờ</small
              ></span
            >
            <span
              v-if="attentionCount || error"
              class="driver-account-count"
              :class="{ 'has-error': error }"
              >{{ attentionCount ? (attentionCount > 99 ? '99+' : attentionCount) : '!' }}</span
            >
            <ChevronRight
              :size="16"
              aria-hidden="true"
            />
          </button>
          <button
            type="button"
            @click="accountAction('passwordChange')"
          >
            <KeyRound
              :size="19"
              aria-hidden="true"
            /><span>Đổi mật khẩu</span>
          </button>
          <button
            type="button"
            class="driver-account-signout"
            @click="accountAction('signOut')"
          >
            <LogOut
              :size="19"
              aria-hidden="true"
            /><span>Đăng xuất</span>
          </button>
        </div>
        <div
          v-else
          class="driver-notifications-content"
        >
          <p
            v-if="loading"
            role="status"
          >
            Đang tải yêu cầu nhận chuyến…
          </p>
          <p
            v-if="error"
            class="dispatch-error"
            role="alert"
          >
            {{ error }}
          </p>
          <div
            v-if="!loading && !assignmentRequests.length && !inbox.length"
            class="dispatch-empty"
          >
            Chưa có thông báo hoặc yêu cầu nhận chuyến mới.
          </div>
          <div
            v-if="assignmentRequests.length"
            class="driver-assignment-requests"
          >
            <h3>Yêu cầu nhận chuyến tức thời</h3>
            <article
              v-for="assignment in assignmentRequests"
              :key="assignment.requestId"
            >
              <div>
                <strong>#{{ assignment.tripId }} · {{ assignment.routeName }}</strong>
                <span
                  >{{ assignment.vehiclePlate }} · Tạo {{ time(assignment.tripCreatedAt) }} · Gửi
                  lúc {{ time(assignment.requestedAt) }}</span
                >
              </div>
              <p>Điều phối viên đang chờ bạn xác nhận nhận chuyến.</p>
              <div class="driver-dispatch-actions">
                <button
                  type="button"
                  class="dispatch-primary"
                  :disabled="!!busy"
                  @click="
                    mutate(
                      `accept-assignment:${assignment.requestId}`,
                      () => acceptDriverAssignmentRequest(assignment.requestId),
                      true,
                    )
                  "
                >
                  {{
                    busy === `accept-assignment:${assignment.requestId}`
                      ? 'Đang nhận chuyến…'
                      : 'Nhận chuyến'
                  }}
                </button>
                <button
                  type="button"
                  class="dispatch-secondary"
                  :disabled="!!busy"
                  @click="requestDecline(assignment.requestId)"
                >
                  Từ chối
                </button>
              </div>
            </article>
          </div>
          <div class="driver-dispatch-inbox">
            <h3>Hộp công việc</h3>
            <p v-if="!inbox.length">Chưa có thông báo điều phối.</p>
            <ul v-else>
              <li
                v-for="item in inbox"
                :key="item.id"
                :class="{ unread: !item.readAt }"
              >
                <div>
                  <strong>{{ item.title }}</strong
                  ><small>{{ time(item.createdAt) }}</small>
                </div>
                <p v-if="item.detail">{{ item.detail }}</p>
                <button
                  v-if="!item.readAt"
                  type="button"
                  :disabled="!!busy"
                  @click="mutate(`read:${item.id}`, () => readDispatchInboxItem(item.id))"
                >
                  Đã đọc
                </button>
              </li>
            </ul>
          </div>
          <FleetConfirmDialog
            v-if="assignmentToDecline !== null"
            title="Từ chối nhận chuyến?"
            message="Yêu cầu sẽ được chuyển cho điều phối viên để chọn tài xế khác."
            confirm-label="Xác nhận từ chối"
            :busy="!!busy"
            :confirm-disabled="assignmentReason.trim().length < 3"
            :on-close="() => (assignmentToDecline = null)"
            :on-confirm="confirmAssignmentDecline"
          >
            <label class="dispatch-reason"
              >Lý do *<textarea
                v-model="assignmentReason"
                maxlength="500"
                rows="3"
                placeholder="Nhập lý do từ chối (ít nhất 3 ký tự)"
              />
            </label>
          </FleetConfirmDialog>
        </div>
      </section>
    </Teleport>
  </div>
</template>
