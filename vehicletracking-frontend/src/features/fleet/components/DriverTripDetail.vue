<script setup lang="ts">
import { computed } from 'vue';
import { BusFront, Clock3, Motorbike, Navigation, Route, UserRound, X } from '@lucide/vue';
import { RouterLink } from 'vue-router';
import { TRIP_STATUS_LABELS, type TripDetail, type TripStop } from '@/features/fleet/types/fleet';
import { scheduledStopArrivalAt, tripDispatchLabel } from '@/features/fleet/utils/tripTime';
import { formatDuration } from '@/shared/utils/format';
import SidePanel from '@/shared/components/SidePanel.vue';
import '@/features/fleet/styles/driver-trip-detail.css';

const props = defineProps<{
  detail: TripDetail;
  formatTime: (value: string | null) => string;
}>();
const emit = defineEmits<{ close: [] }>();
const trip = computed(() => props.detail.trip);
const isFixedSchedule = computed(() => trip.value.dispatchMode === 'FIXED_SCHEDULE');

function stopArrival(stop: TripStop) {
  if (isFixedSchedule.value)
    return props.formatTime(scheduledStopArrivalAt(trip.value.scheduledDepartureAt, stop.arrivalOffsetSeconds));
  if (!trip.value.startedAt)
    return `Sau ${formatDuration(stop.arrivalOffsetSeconds)} từ lúc khởi hành`;
  return props.formatTime(
    new Date(Date.parse(trip.value.startedAt) + stop.arrivalOffsetSeconds * 1000).toISOString(),
  );
}
</script>

<template>
  <SidePanel
    class-name="driver-trip-modal"
    :label="`Chi tiết chuyến #${trip.id}`"
    content-sized
    :on-close="() => emit('close')"
  >
    <section class="driver-trip-detail">
      <div class="driver-trip-detail-heading">
        <div class="trip-detail-heading-copy">
          <span class="driver-trip-eyebrow">CỔNG TÀI XẾ</span>
          <div class="trip-detail-heading-title">
            <h2>Chi tiết chuyến #{{ trip.id }}</h2>
            <span :class="`trip-status ${trip.status.toLowerCase()}`">
              {{ TRIP_STATUS_LABELS[trip.status] }}
            </span>
          </div>
          <p>Thông tin phân công, thời gian và các điểm dừng của bạn.</p>
        </div>
        <button
          type="button"
          class="driver-trip-close"
          aria-label="Đóng chi tiết chuyến"
          @click="emit('close')"
        >
          <X :size="19" />
        </button>
      </div>

      <div class="trip-detail-body driver-trip-detail-body">
        <section
          class="trip-summary"
          aria-label="Tổng quan chuyến đi"
        >
          <div class="trip-summary-vehicle">
            <span class="trip-summary-icon">
              <Motorbike
                v-if="trip.vehicleType === 'MOTORCYCLE'"
                :size="22"
              />
              <BusFront
                v-else
                :size="22"
              />
            </span>
            <div>
              <span class="trip-summary-label">Phương tiện thực hiện</span>
              <h3>{{ trip.vehiclePlateNumber }}</h3>
              <p>Chuyến #{{ trip.id }} · {{ tripDispatchLabel(trip) }}</p>
            </div>
          </div>
          <div class="trip-summary-meta">
            <div>
              <span class="trip-summary-meta-icon"><Route :size="17" /></span>
              <span
                ><small>Tuyến đường</small><strong>{{ trip.routeName }}</strong></span
              >
            </div>
            <div>
              <span class="trip-summary-meta-icon"><UserRound :size="17" /></span>
              <span>
                <small>Tài xế phụ trách</small>
                <strong>{{ trip.driver?.fullName ?? 'Chưa phân công' }}</strong>
              </span>
            </div>
          </div>
        </section>

        <section
          class="trip-traffic-details"
          aria-labelledby="driver-trip-time-heading"
        >
          <div class="trip-card-heading">
            <span><Clock3 :size="17" /></span>
            <div>
              <h3 id="driver-trip-time-heading">Thời gian vận hành</h3>
              <p>
                {{
                  isFixedSchedule
                    ? `Kế hoạch từ ${trip.scheduleName || 'lịch chạy tự động'} và thời gian thực tế`
                    : 'Chuyến không có giờ kế hoạch; hệ thống chỉ ghi nhận thời gian thực tế'
                }}
              </p>
            </div>
          </div>
          <dl
            class="trip-times"
            :data-fixed-schedule="isFixedSchedule"
          >
            <template v-if="isFixedSchedule">
              <div>
                <dt>Xuất phát kế hoạch</dt>
                <dd>{{ formatTime(trip.scheduledDepartureAt) }}</dd>
              </div>
              <div>
                <dt>Hoàn thành theo lịch</dt>
                <dd>{{ formatTime(trip.plannedEndAt) }}</dd>
              </div>
            </template>
            <div v-else>
              <dt>Tạo chuyến tức thời</dt>
              <dd>{{ formatTime(trip.createdAt) }}</dd>
            </div>
            <div>
              <dt>Khởi hành thực tế</dt>
              <dd>{{ formatTime(trip.startedAt) }}</dd>
            </div>
            <div>
              <dt>{{ trip.status === 'CANCELLED' ? 'Hủy lúc' : 'Kết thúc thực tế' }}</dt>
              <dd>{{ formatTime(trip.endedAt) }}</dd>
            </div>
          </dl>
        </section>
        <p
          v-if="trip.status === 'CANCELLED' && trip.cancellationReason"
          class="driver-trip-cancellation"
        >
          <strong>Lý do hủy:</strong> {{ trip.cancellationReason }}
        </p>

        <section
          class="trip-itinerary driver-trip-itinerary"
          aria-labelledby="driver-trip-itinerary-heading"
        >
          <div class="trip-itinerary-heading">
            <div>
              <span class="trip-summary-label">Hành trình</span>
              <h3 id="driver-trip-itinerary-heading">Các điểm dừng trên tuyến</h3>
              <p>
                {{
                  isFixedSchedule
                    ? 'Thời gian dự kiến theo lịch chạy.'
                    : 'Thời gian dự kiến theo tuyến, không phải ETA giao thông trực tiếp.'
                }}
              </p>
            </div>
            <strong>{{ detail.stops.length }} trạm</strong>
          </div>
          <ol
            v-if="detail.stops.length"
            class="trip-timeline"
          >
            <li
              v-for="(stop, index) in detail.stops"
              :key="stop.sequenceNumber"
            >
              <span
                :class="`stop-order ${index === 0 ? 'start' : index === detail.stops.length - 1 ? 'end' : 'stop'}`"
              >
                {{ stop.sequenceNumber }}
              </span>
              <div class="trip-stop-card">
                <div class="trip-stop-heading">
                  <strong class="driver-trip-stop-name">{{ stop.stationName }}</strong>
                  <span class="fleet-help">{{
                    index === 0
                      ? 'Điểm đầu'
                      : index === detail.stops.length - 1
                        ? 'Điểm cuối'
                        : 'Trạm dừng'
                  }}</span>
                </div>
                <div class="trip-stop-status">
                  <span class="trip-eta-stop"
                    >{{ isFixedSchedule ? 'Dự kiến đến (theo lịch)' : 'Dự kiến đến (theo tuyến)' }}:
                    {{ stopArrival(stop) }}</span
                  >
                </div>
              </div>
            </li>
          </ol>
          <p
            v-else
            class="driver-trip-empty"
            role="status"
          >
            Chuyến chưa có thông tin điểm dừng.
          </p>
        </section>
      </div>

      <div class="driver-trip-detail-footer">
        <RouterLink
          class="driver-trip-map-link"
          :to="`/driver/trips/${trip.id}/navigate`"
        >
          <Navigation :size="18" />
          {{ trip.status === 'SCHEDULED' ? 'Mở bản đồ và khởi hành' : 'Xem bản đồ chuyến' }}
        </RouterLink>
      </div>
    </section>
  </SidePanel>
</template>
