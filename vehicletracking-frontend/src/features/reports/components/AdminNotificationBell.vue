<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, useId, watch, type CSSProperties } from 'vue';
import {
  Bell,
  BellOff,
  Check,
  CheckCheck,
  CircleCheck,
  MapPinned,
  RefreshCw,
  Route,
  Trash2,
  X,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import { notificationMonitoringLink } from '@/features/reroute/utils/notificationLink';
import { useAdminNotifications } from '../composables/useAdminNotifications';
import { alertDetail, formatDateTime } from '../utils/notificationPresentation';
import type { NotificationItem } from '../types/notifications';
import '../styles/admin-notifications.css';
const open = defineModel<boolean>({ default: false });
const props = defineProps<{ liveNotifications?: NotificationItem[] | null }>();
const {
  items,
  filtered,
  unread,
  loading,
  error,
  deleteError,
  typeFilter,
  severityFilter,
  busyId,
  confirmDelete,
  refresh,
  read,
  readAll,
  remove,
  acknowledge,
} = useAdminNotifications(() => props.liveNotifications ?? null);
const trigger = shallowRef<HTMLButtonElement | null>(null),
  panel = shallowRef<HTMLElement | null>(null);
const panelId = `admin-notifications-${useId()}`;
const panelStyle = ref<CSSProperties>({});
const page = ref(1);
const pageSize = 10;
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)));
const pagedItems = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
watch([typeFilter, severityFilter], () => (page.value = 1));
watch(pageCount, (count) => {
  if (page.value > count) page.value = count;
});
function positionPanel() {
  if (!trigger.value) return;
  const anchor = trigger.value.getBoundingClientRect();
  const viewportWidth = document.documentElement.clientWidth || window.innerWidth;
  const width = Math.min(440, viewportWidth - 24);
  const top = Math.max(12, Math.min(anchor.bottom + 10, window.innerHeight - 140));
  panelStyle.value = {
    width: `${width}px`,
    left: `${Math.max(12, Math.min(anchor.right - width, viewportWidth - width - 12))}px`,
    top: `${top}px`,
    maxHeight: `${Math.min(640, Math.max(0, window.innerHeight - top - 12))}px`,
  };
}
function close(restoreFocus = false) {
  if (confirmDelete.value || busyId.value !== null) return;
  open.value = false;
  if (restoreFocus) void nextTick(() => trigger.value?.focus({ preventScroll: true }));
}
watch(
  open,
  (visible, _old, cleanup) => {
    if (!visible) {
      confirmDelete.value = null;
      deleteError.value = null;
      return;
    }
    positionPanel();
    void nextTick(() => {
      if (open.value) {
        positionPanel();
        panel.value?.focus({ preventScroll: true });
      }
    });
    const outside = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !panel.value?.contains(event.target) &&
        !trigger.value?.contains(event.target)
      )
        close();
    };
    const keyboard = (event: KeyboardEvent) => {
      if (
        confirmDelete.value ||
        (event.target instanceof HTMLElement && event.target.closest('dialog'))
      )
        return;
      if (event.key === 'Escape') {
        event.preventDefault();
        close(true);
        return;
      }
      if (event.key !== 'Tab' || !panel.value?.contains(document.activeElement)) return;
      const controls = [
        ...panel.value.querySelectorAll<HTMLElement>(
          'a[href],button:not([disabled]),select:not([disabled])',
        ),
      ];
      const first = controls[0],
        last = controls[controls.length - 1];
      if (!first || !last) return;
      if (
        event.shiftKey &&
        (document.activeElement === first || document.activeElement === panel.value)
      ) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    const scroll = (event: Event) => {
      if (!(event.target instanceof Node) || !panel.value?.contains(event.target)) positionPanel();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', keyboard);
    window.addEventListener('resize', positionPanel);
    window.addEventListener('scroll', scroll, true);
    cleanup(() => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', keyboard);
      window.removeEventListener('resize', positionPanel);
      window.removeEventListener('scroll', scroll, true);
    });
  },
  { immediate: true },
);
</script>
<template>
  <button
    ref="trigger"
    type="button"
    class="admin-notification-trigger"
    aria-label="Thông báo admin"
    :title="error ? 'Có lỗi tải thông báo' : `${unread} thông báo chưa đọc`"
    aria-haspopup="dialog"
    :aria-expanded="open"
    :aria-controls="open ? panelId : undefined"
    @click="open ? close(true) : (open = true)"
  >
    <Bell :size="19" /><b
      v-if="unread"
      class="admin-notification-badge"
      >{{ unread > 99 ? '99+' : unread }}</b
    >
    <i
      v-if="error"
      class="admin-notification-error-dot"
      aria-label="Có lỗi tải thông báo"
    />
    <span class="admin-notification-sr-only">{{ unread }} chưa đọc</span>
  </button>
  <Teleport to="body">
    <section
      v-if="open"
      :id="panelId"
      ref="panel"
      class="admin-notification-panel"
      :style="panelStyle"
      role="dialog"
      :aria-labelledby="`${panelId}-title`"
      tabindex="-1"
    >
      <header class="admin-notification-heading">
        <div>
          <h2 :id="`${panelId}-title`">Thông báo</h2>
          <p>{{ unread }} chưa đọc · Tối đa 50 thông báo gần nhất</p>
        </div>
        <button
          type="button"
          :disabled="loading"
          aria-label="Làm mới thông báo admin"
          @click="refresh"
        >
          <RefreshCw :size="16" />
        </button>
        <button
          type="button"
          :disabled="busyId !== null || !!confirmDelete"
          aria-label="Đóng thông báo admin"
          @click="close(true)"
        >
          <X :size="18" />
        </button>
      </header>
      <div class="admin-notification-toolbar">
        <span>Cập nhật mỗi 15 giây</span
        ><button
          type="button"
          class="admin-notification-read-all"
          :disabled="busyId !== null || !unread"
          @click="readAll"
        >
          <CheckCheck :size="14" />Đọc tất cả
        </button>
      </div>
      <div class="admin-notification-filters">
        <label
          >Loại<select v-model="typeFilter">
            <option value="ALL">Tất cả</option>
            <option value="REROUTE">Đổi tuyến</option>
            <option value="DISPATCH">Điều phối</option>
            <option value="INCIDENT">Sự cố mô phỏng</option>
          </select></label
        >
        <label
          >Mức độ<select v-model="severityFilter">
            <option value="ALL">Tất cả</option>
            <option value="CRITICAL">Nghiêm trọng</option>
            <option value="MAJOR">Cao</option>
          </select></label
        >
      </div>
      <div
        class="admin-notification-list"
        :aria-busy="loading"
      >
        <p
          v-if="loading"
          class="admin-notification-state"
          role="status"
        >
          Đang tải thông báo…
        </p>
        <div
          v-if="error"
          class="admin-notification-error"
          role="alert"
        >
          <p>{{ error }}</p>
          <button
            type="button"
            :disabled="loading"
            @click="refresh"
          >
            Thử lại
          </button>
        </div>
        <div
          v-if="!loading && !error && !filtered.length"
          class="admin-notification-empty"
        >
          <BellOff :size="26" /><strong>{{
            items.length ? 'Không có thông báo phù hợp' : 'Chưa có thông báo'
          }}</strong>
          <p>
            {{
              items.length
                ? 'Thử thay đổi bộ lọc.'
                : 'Thông báo chuyến, tuyến và điều phối sẽ xuất hiện tại đây.'
            }}
          </p>
        </div>
        <article
          v-for="item in loading ? [] : pagedItems"
          :key="item.id"
          :class="[
            'admin-notification-card',
            item.readAt ? 'read' : 'unread',
            {
              'is-incident':
                item.type === 'SIMULATION_INCIDENT' || item.type === 'SIMULATION_INCIDENT_RESOLVED',
              'is-resolved': item.type === 'SIMULATION_INCIDENT_RESOLVED',
            },
          ]"
        >
          <div class="admin-notification-card-heading">
            <span :class="['admin-notification-severity', item.severity.toLowerCase()]">{{
              item.severity === 'CRITICAL' ? 'Nghiêm trọng' : 'Cao'
            }}</span
            ><time>{{ formatDateTime(item.createdAt) }}</time>
          </div>
          <h3>
            <MapPinned
              v-if="item.type === 'OFF_ROUTE_DETECTED'"
              :size="15"
            /><Route
              v-else-if="item.type === 'REROUTE_CREATED' || item.type === 'DRIVER_ROUTE_CHANGED'"
              :size="15"
            /><CircleCheck
              v-else-if="item.type === 'SIMULATION_INCIDENT_RESOLVED'"
              :size="17"
            /><Bell
              v-else
              :size="15"
            />{{ item.title }}
          </h3>
          <p class="admin-notification-trip">
            <strong>{{ item.vehiclePlateNumber }}</strong
            ><span>Chuyến #{{ item.tripId }}</span>
          </p>
          <template
            v-if="
              item.type === 'SIMULATION_INCIDENT' || item.type === 'SIMULATION_INCIDENT_RESOLVED'
            "
          >
            <div class="admin-notification-incident-meta">
              <span
                class="admin-incident-status"
                :class="{ 'is-resolved': item.simulationIncidentStatus === 'RESOLVED' }"
              >
                {{
                  item.simulationIncidentStatus === 'RESOLVED'
                    ? 'Đã xử lý'
                    : item.simulationIncidentStatus === 'ACKNOWLEDGED'
                      ? 'Đã tiếp nhận'
                      : 'Chờ tiếp nhận'
                }}
              </span>
              <span class="admin-incident-reporter"
                >Người báo:
                <strong>{{
                  item.simulationIncidentReportedByDriver ?? 'Chưa ghi nhận'
                }}</strong></span
              >
            </div>
            <dl class="admin-incident-content">
              <div>
                <dt>
                  {{
                    item.type === 'SIMULATION_INCIDENT_RESOLVED'
                      ? 'Kết quả xử lý'
                      : 'Nội dung sự cố'
                  }}
                </dt>
                <dd>{{ alertDetail(item) }}</dd>
              </div>
              <div
                v-if="item.simulationIncidentLocationLabel"
                class="admin-incident-location"
              >
                <dt><MapPinned :size="14" />Vị trí sự cố:</dt>
                <dd>{{ item.simulationIncidentLocationLabel }}</dd>
              </div>
              <div
                v-if="
                  item.simulationIncidentStatus === 'RESOLVED' &&
                  item.simulationIncidentResolutionNote &&
                  item.type !== 'SIMULATION_INCIDENT_RESOLVED'
                "
              >
                <dt>Kết quả xử lý</dt>
                <dd>{{ item.simulationIncidentResolutionNote }}</dd>
              </div>
            </dl>
          </template>
          <p
            v-else
            class="admin-notification-detail"
          >
            {{ alertDetail(item) }}
          </p>
          <div class="admin-notification-actions">
            <RouterLink :to="notificationMonitoringLink(item)">Mở giám sát</RouterLink>
            <button
              v-if="item.type === 'SIMULATION_INCIDENT' && item.simulationIncidentStatus === 'OPEN'"
              type="button"
              :disabled="busyId !== null"
              @click="acknowledge(item)"
            >
              Tiếp nhận
            </button>
            <button
              v-if="!item.readAt"
              type="button"
              :disabled="busyId !== null"
              @click="read(item)"
            >
              <Check :size="13" />Đã đọc
            </button>
            <button
              type="button"
              :disabled="busyId !== null"
              @click="
                deleteError = null;
                confirmDelete = item;
              "
            >
              <Trash2 :size="13" />Xóa
            </button>
          </div>
        </article>
        <nav
          v-if="!loading && !error && filtered.length > 0"
          class="admin-notification-pagination"
          aria-label="Phân trang thông báo"
        >
          <span>Trang {{ page }} / {{ pageCount }}</span>
          <div>
            <button
              type="button"
              :disabled="page <= 1"
              @click="page--"
            >
              Trước
            </button>
            <button
              type="button"
              :disabled="page >= pageCount"
              @click="page++"
            >
              Tiếp
            </button>
          </div>
        </nav>
      </div>
    </section>
    <FleetConfirmDialog
      v-if="confirmDelete"
      title="Xóa cảnh báo?"
      message="Cảnh báo sẽ bị xóa khỏi danh sách. Dữ liệu chuyến và lịch sử tuyến không bị ảnh hưởng."
      confirm-label="Xác nhận xóa"
      :busy="busyId !== null"
      :on-close="() => (confirmDelete = null)"
      :on-confirm="remove"
    >
      <p
        v-if="deleteError"
        class="admin-notification-error"
        role="alert"
      >
        {{ deleteError }}
      </p>
    </FleetConfirmDialog>
  </Teleport>
</template>
