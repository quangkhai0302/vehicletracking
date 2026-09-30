<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import type { TripSummary } from '@/features/fleet/types/fleet';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import {
  DispatchApiError, acceptDispatchOffer, declineDispatchOffer, fetchDispatchInbox,
  fetchDispatchOffers, fetchDriverDispatch, readDispatchInboxItem, readyForTrip, reportUnavailable,
} from '../api/dispatch';
import {
  DISPATCH_ATTENTION_LABELS, DISPATCH_STATE_LABELS,
  type DispatchOffer, type DriverDispatchDetail, type DriverDispatchInboxItem,
} from '../types/dispatch';
import '../styles/dispatch.css';

const props = defineProps<{ trips: TripSummary[] }>();
const emit = defineEmits<{ changed: [] }>();
const autoTrips = computed(() => props.trips.filter((trip) =>
  trip.status === 'SCHEDULED' && trip.dispatch?.startMode === 'AUTO_IF_READY'));
const details = shallowRef<Record<number, DriverDispatchDetail>>({});
const offers = shallowRef<DispatchOffer[]>([]);
const inbox = shallowRef<DriverDispatchInboxItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const busy = ref<string | null>(null);
const unavailableTrip = ref<number | null>(null);
const unavailableReason = ref('');
let controller: AbortController | null = null;
let timer: ReturnType<typeof setInterval> | null = null;

async function refresh() {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  try {
    const [nextOffers, nextInbox, nextDetails] = await Promise.all([
      fetchDispatchOffers(request.signal),
      fetchDispatchInbox(request.signal),
      Promise.all(autoTrips.value.map(async (trip) => {
        try {
          return [trip.id, await fetchDriverDispatch(trip.id, request.signal)] as const;
        } catch (cause) {
          // A reassignment can revoke access before the parent trip list refreshes.
          if (cause instanceof DispatchApiError && cause.status === 404) return null;
          throw cause;
        }
      })),
    ]);
    if (request.signal.aborted) return;
    offers.value = nextOffers;
    inbox.value = nextInbox;
    details.value = Object.fromEntries(nextDetails.filter((item) => item !== null));
    error.value = null;
  } catch (cause) {
    if (!request.signal.aborted) error.value = cause instanceof Error ? cause.message : 'Không thể tải điều phối.';
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}
watch(() => props.trips.map((trip) => `${trip.id}:${trip.dispatch?.revision ?? ''}`).join(','),
  () => { void refresh(); }, { immediate: true });
onMounted(() => {
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
function confirmUnavailable() {
  const tripId = unavailableTrip.value;
  const dispatch = tripId === null ? null : details.value[tripId];
  if (tripId === null || !dispatch || unavailableReason.value.trim().length < 3) return;
  const reason = unavailableReason.value.trim();
  unavailableTrip.value = null;
  unavailableReason.value = '';
  void mutate(`busy:${tripId}`, () => reportUnavailable(tripId, dispatch.revision, reason), true);
}
</script>

<template>
  <section class="driver-dispatch-workspace" aria-label="Công việc điều phối">
    <div class="driver-dispatch-heading">
      <div><small>ĐIỀU PHỐI CHUYẾN</small><h2>Xác nhận và lời mời</h2></div>
      <button type="button" :disabled="loading || !!busy" @click="refresh">Làm mới</button>
    </div>
    <p v-if="loading" role="status">Đang tải lời mời và trạng thái chuyến…</p>
    <p v-if="error" class="dispatch-error" role="alert">{{ error }}</p>
    <div v-if="!loading && !autoTrips.length && !offers.length && !inbox.length" class="dispatch-empty">
      Chưa có yêu cầu xác nhận hoặc lời mời mới.
    </div>
    <div v-if="autoTrips.length" class="driver-dispatch-cards">
      <article v-for="trip in autoTrips" :key="trip.id" class="driver-dispatch-card">
        <div><strong>#{{ trip.id }} · {{ trip.routeName }}</strong>
          <span>{{ trip.vehiclePlateNumber }} · {{ time(trip.scheduledDepartureAt) }}</span></div>
        <template v-if="details[trip.id]">
          <span class="dispatch-badge">{{ DISPATCH_STATE_LABELS[details[trip.id]!.state] }}</span>
          <p v-if="details[trip.id]!.attentionCode" class="dispatch-attention">
            {{ DISPATCH_ATTENTION_LABELS[details[trip.id]!.attentionCode!] }}
          </p>
          <p v-if="details[trip.id]!.readyAt">Đã xác nhận lúc {{ time(details[trip.id]!.readyAt!) }}.</p>
          <p v-else>Hạn tự khởi hành: {{ time(details[trip.id]!.cutoffAt) }}.</p>
          <div class="driver-dispatch-actions">
            <button type="button" class="dispatch-primary" :disabled="!!busy || !details[trip.id]!.canReady"
              @click="mutate(`ready:${trip.id}`, () => readyForTrip(trip.id, details[trip.id]!.revision), true)">
              {{ busy === `ready:${trip.id}` ? 'Đang xác nhận…' : 'Sẵn sàng' }}
            </button>
            <button type="button" class="dispatch-secondary"
              :disabled="!!busy || !details[trip.id]!.canReportUnavailable"
              @click="unavailableTrip = trip.id; unavailableReason = ''">Báo bận</button>
          </div>
        </template>
      </article>
    </div>
    <div v-if="offers.length" class="driver-dispatch-offers">
      <h3>Lời mời nhận chuyến</h3>
      <article v-for="offer in offers" :key="offer.offerId">
        <strong>#{{ offer.tripId }} · {{ offer.routeName }}</strong>
        <p>{{ offer.vehiclePlate }} · Khởi hành {{ time(offer.scheduledDepartureAt) }}</p>
        <p>Hết hạn trả lời: {{ time(offer.expiresAt) }}</p>
        <div class="driver-dispatch-actions">
          <button type="button" class="dispatch-primary" :disabled="!!busy"
            @click="mutate(`accept:${offer.offerId}`, () => acceptDispatchOffer(offer.offerId, offer.revision), true)">Nhận chuyến</button>
          <button type="button" class="dispatch-secondary" :disabled="!!busy"
            @click="mutate(`decline:${offer.offerId}`, () => declineDispatchOffer(offer.offerId, offer.revision))">Từ chối</button>
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
    <FleetConfirmDialog v-if="unavailableTrip !== null" title="Báo bận chuyến này?"
      message="Bạn sẽ không còn được phân công chuyến này. Điều phối viên sẽ được thông báo và hệ thống có thể mời tài xế dự phòng."
      confirm-label="Xác nhận báo bận" :busy="!!busy" :confirm-disabled="unavailableReason.trim().length < 3"
      :on-close="() => (unavailableTrip = null)" :on-confirm="confirmUnavailable">
      <label class="dispatch-reason">Lý do *<textarea v-model="unavailableReason" maxlength="500" rows="3"
        placeholder="Ví dụ: Tôi có việc đột xuất và không thể nhận chuyến" /></label>
    </FleetConfirmDialog>
  </section>
</template>
