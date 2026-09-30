<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import type { Driver, TripSummary } from '@/features/fleet/types/fleet';
import {
  DispatchApiError, fetchDispatchDetail, overrideTripStart, updateTripDispatchPolicy,
} from '../api/dispatch';
import {
  DISPATCH_ATTENTION_LABELS, DISPATCH_STATE_LABELS,
  type DispatchDetail, type DispatchStartMode,
} from '../types/dispatch';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import '../styles/dispatch.css';

const props = defineProps<{
  trip: TripSummary;
  drivers: Driver[];
  onChanged: () => void;
}>();
const detail = shallowRef<DispatchDetail | null>(null);
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const actionError = ref<string | null>(null);
const mode = ref<DispatchStartMode>('MANUAL');
const backupEnabled = ref(false);
const backupIds = ref<number[]>([]);
const selectedBackup = ref(0);
const draftRevision = ref<number | null>(null);
const editing = ref(false);
const overrideOpen = ref(false);
const overrideReason = ref('');
const now = ref(Date.now());
const activeDrivers = computed(() => props.drivers.filter((driver) => driver.active));
const availableBackup = computed(() => activeDrivers.value.filter((driver) =>
  driver.id !== detail.value?.primaryDriverId && !backupIds.value.includes(driver.id)));
const summary = computed(() => detail.value?.summary ?? props.trip.dispatch ?? null);
const offerSecondsLeft = computed(() => detail.value?.activeOffer
  ? Math.max(0, Math.ceil((Date.parse(detail.value.activeOffer.expiresAt) - now.value) / 1000))
  : null);
let controller: AbortController | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
let clockTimer: ReturnType<typeof setInterval> | null = null;

function setDraft(next: DispatchDetail) {
  mode.value = next.summary.startMode;
  backupIds.value = next.candidates.map((item) => item.driverId);
  backupEnabled.value = backupIds.value.length > 0;
  selectedBackup.value = 0;
  draftRevision.value = next.summary.revision;
}
async function load() {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  try {
    const next = await fetchDispatchDetail(props.trip.id, request.signal);
    if (request.signal.aborted) return;
    detail.value = next;
    if (!editing.value) setDraft(next);
    error.value = null;
  } catch (cause) {
    if (!request.signal.aborted) error.value = cause instanceof Error ? cause.message : 'Không thể tải điều phối.';
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}
watch(() => props.trip.id, () => {
  editing.value = false;
  actionError.value = null;
  error.value = null;
  detail.value = null;
  loading.value = true;
  void load();
}, { immediate: true });
onMounted(() => {
  clockTimer = setInterval(() => { now.value = Date.now(); }, 1000);
  timer = setInterval(() => {
    if (!busy.value && document.visibilityState === 'visible') void load();
  }, 5000);
});
onBeforeUnmount(() => {
  controller?.abort();
  if (timer) clearInterval(timer);
  if (clockTimer) clearInterval(clockTimer);
});
function addBackup() {
  const id = Number(selectedBackup.value);
  if (id && availableBackup.value.some((driver) => driver.id === id) && backupIds.value.length < 20)
    backupIds.value = [...backupIds.value, id];
  selectedBackup.value = 0;
  editing.value = true;
}
function moveBackup(index: number, offset: number) {
  const next = [...backupIds.value];
  const target = index + offset;
  if (target < 0 || target >= next.length) return;
  [next[index], next[target]] = [next[target]!, next[index]!];
  backupIds.value = next;
  editing.value = true;
}
function driverName(id: number) {
  return props.drivers.find((driver) => driver.id === id)?.fullName
    ?? detail.value?.candidates.find((candidate) => candidate.driverId === id)?.fullName
    ?? `#${id}`;
}
function actionErrorMessage(cause: unknown, action: 'policy' | 'override') {
  if (cause instanceof DispatchApiError) {
    if (cause.code === 'DISPATCH_INVALID_STATE')
      return action === 'policy'
        ? 'Không thể đổi chính sách: chuyến không còn ở trạng thái cho phép hoặc đã có dữ liệu mô phỏng/GPS. Hãy kiểm tra trạng thái chuyến.'
        : 'Không thể khởi hành ngoại lệ: chuyến không còn đủ điều kiện. Hãy kiểm tra trạng thái, tài xế và phiên mô phỏng.';
    if (cause.code === 'DISPATCH_STALE_REVISION')
      return 'Điều phối đã được thay đổi ở phiên khác. Trạng thái mới đã được tải lại; hãy kiểm tra trước khi thao tác tiếp.';
  }
  return cause instanceof Error ? cause.message : 'Không thể thực hiện thao tác.';
}
async function retry() {
  actionError.value = null;
  editing.value = false;
  await load();
  props.onChanged();
}
async function savePolicy() {
  if (!detail.value || busy.value || props.trip.status !== 'SCHEDULED') return;
  if (mode.value === 'AUTO_IF_READY' && backupEnabled.value && backupIds.value.length === 0) {
    actionError.value = 'Chọn ít nhất một tài xế dự phòng hoặc tắt chế độ dự phòng.';
    return;
  }
  busy.value = true;
  actionError.value = null;
  controller?.abort();
  try {
    detail.value = await updateTripDispatchPolicy(props.trip.id, {
      expectedRevision: draftRevision.value,
      startMode: mode.value,
      backupEnabled: mode.value === 'AUTO_IF_READY' && backupEnabled.value,
      backupDriverIds: mode.value === 'AUTO_IF_READY' && backupEnabled.value ? backupIds.value : [],
    });
    editing.value = false;
    setDraft(detail.value);
    error.value = null;
    props.onChanged();
  } catch (cause) {
    if (cause instanceof DispatchApiError && cause.status === 409) {
      editing.value = false;
      await load();
      props.onChanged();
    }
    actionError.value = actionErrorMessage(cause, 'policy');
  } finally { busy.value = false; }
}
async function startOverride() {
  if (!detail.value || busy.value || overrideReason.value.trim().length < 10) return;
  const revision = detail.value.summary.revision;
  if (revision === null) return;
  busy.value = true;
  actionError.value = null;
  controller?.abort();
  try {
    const response = await overrideTripStart(props.trip.id, revision, overrideReason.value.trim());
    detail.value = response.dispatch;
    overrideOpen.value = false;
    overrideReason.value = '';
    error.value = null;
    props.onChanged();
  } catch (cause) {
    overrideOpen.value = false;
    if (cause instanceof DispatchApiError && cause.status === 409) {
      await load();
      props.onChanged();
    }
    actionError.value = actionErrorMessage(cause, 'override');
  } finally { busy.value = false; }
}
</script>

<template>
  <section class="dispatch-admin-panel" aria-label="Điều phối khởi hành">
    <div class="dispatch-heading">
      <div><small>ĐIỀU PHỐI THEO LỊCH</small><h3>Khởi hành chuyến</h3></div>
      <span v-if="summary" class="dispatch-badge">{{ DISPATCH_STATE_LABELS[summary.state] }}</span>
    </div>
    <p v-if="loading" role="status">Đang tải điều phối…</p>
    <div v-if="error || actionError" role="alert" class="dispatch-error">
      <span>{{ actionError || error }}</span>
      <button type="button" @click="retry">Tải lại chi tiết</button>
    </div>
    <template v-if="detail">
      <p v-if="detail.summary.attentionCode" class="dispatch-attention">
        {{ DISPATCH_ATTENTION_LABELS[detail.summary.attentionCode] }}
      </p>
      <p>Chế độ: {{ detail.summary.startMode === 'AUTO_IF_READY' ? 'Tự chạy sau xác nhận' : 'Thủ công' }}
        · Tài xế hiện tại: {{ detail.currentDriverId ? driverName(detail.currentDriverId) : 'Chưa gán' }}</p>
      <p v-if="detail.activeOffer">Đang mời {{ driverName(detail.activeOffer.driverId) }}
        đến {{ new Date(detail.activeOffer.expiresAt).toLocaleTimeString('vi-VN') }}
        · còn {{ offerSecondsLeft }} giây để trả lời.</p>
      <div v-if="trip.status === 'SCHEDULED'" class="dispatch-policy-editor">
        <h4>Chính sách riêng của chuyến này</h4>
        <p class="dispatch-policy-note">Chỉ đổi được trước khi chuyến có phiên mô phỏng hoặc dữ liệu GPS.</p>
        <label><span>Cách khởi hành</span><select v-model="mode" :disabled="busy" @change="editing = true">
          <option value="MANUAL">Thủ công</option><option value="AUTO_IF_READY">Tự chạy khi sẵn sàng</option>
        </select></label>
        <label v-if="mode === 'AUTO_IF_READY'" class="dispatch-check">
          <input v-model="backupEnabled" type="checkbox" :disabled="busy" @change="editing = true" /> Mời tài xế dự phòng
        </label>
        <div v-if="mode === 'AUTO_IF_READY' && backupEnabled" class="dispatch-candidates">
          <ol><li v-for="(id, index) in backupIds" :key="id">
            <span>{{ driverName(id) }}</span>
            <button type="button" :disabled="busy || index === 0" @click="moveBackup(index, -1)">↑</button>
            <button type="button" :disabled="busy || index === backupIds.length - 1" @click="moveBackup(index, 1)">↓</button>
            <button type="button" :disabled="busy" @click="backupIds = backupIds.filter((item) => item !== id); editing = true">Xóa</button>
          </li></ol>
          <div><select v-model.number="selectedBackup" :disabled="busy">
            <option :value="0">Chọn tài xế</option>
            <option v-for="driver in availableBackup" :key="driver.id" :value="driver.id">{{ driver.fullName }}</option>
          </select><button type="button" :disabled="busy || !selectedBackup || backupIds.length >= 20" @click="addBackup">Thêm</button></div>
        </div>
        <div class="dispatch-policy-actions">
          <button type="button" class="dispatch-primary" :disabled="busy || !editing" @click="savePolicy">
            {{ busy ? 'Đang lưu…' : 'Lưu chính sách chuyến' }}
          </button>
          <button v-if="!editing && detail.summary.startMode === 'AUTO_IF_READY' && (trip.attemptNumber ?? 1) === 1 && now >= Date.parse(trip.scheduledDepartureAt)"
            type="button" class="dispatch-secondary" :disabled="busy" @click="overrideOpen = true">Khởi hành ngoại lệ</button>
        </div>
      </div>
      <div class="dispatch-history">
        <h4>Lịch sử điều phối</h4>
        <p v-if="!detail.history.length">Chưa có sự kiện.</p>
        <ol v-else><li v-for="item in detail.history" :key="item.revision">
          <strong>{{ item.kind }}</strong> · {{ new Date(item.createdAt).toLocaleString('vi-VN') }}
          <small v-if="item.reason">{{ item.reason }}</small>
        </li></ol>
      </div>
    </template>
    <FleetConfirmDialog v-if="overrideOpen" title="Khởi hành ngoại lệ?"
      message="Admin xác nhận đã kiểm tra tài xế, xe và lộ trình. Thao tác này sẽ chạy simulator ngay."
      confirm-label="Khởi hành chuyến" :busy="busy" :confirm-disabled="overrideReason.trim().length < 10"
      :on-close="() => (overrideOpen = false)" :on-confirm="startOverride">
      <label class="dispatch-reason">Lý do *<textarea v-model="overrideReason" maxlength="500" rows="3"
        placeholder="Nhập lý do điều phối ngoại lệ (ít nhất 10 ký tự)" /></label>
    </FleetConfirmDialog>
  </section>
</template>
