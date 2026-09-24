<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  Clock,
  Eye,
  MapPin,
  Plus,
  Route as RouteIcon,
  Search,
  Trash2,
  X,
} from '@lucide/vue';
import PageHeading from '@/shared/components/PageHeading.vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import {
  createRoute,
  deactivateRoute,
  fetchRouteById,
  fetchRoutes,
} from '@/features/routes/api/routes';
import { fetchStations } from '@/features/stations/api/stations';
import type {
  RouteCreateInput,
  RouteDetail,
  RouteSummary,
} from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';

const location = useRoute();
const router = useRouter();
const requestedRouteId = computed(() => {
  const raw = Array.isArray(location.query.routeId) ? location.query.routeId[0] : location.query.routeId;
  const value = Number(raw);
  return raw && Number.isSafeInteger(value) && value > 0 ? value : null;
});
const routes = ref<RouteSummary[]>([]);
const stations = ref<Station[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const searchQuery = ref('');
const sortBy = ref<'name_asc' | 'name_desc' | 'dist_asc' | 'dist_desc'>('name_asc');

// Detail / View state
const viewingRoute = ref<RouteDetail | null>(null);
const loadingDetail = ref(false);
const detailError = ref<string | null>(null);
const detailDrawerOpen = ref(false);

// Create route drawer state
const createDrawerOpen = ref(false);
const saving = ref(false);
const formError = ref<string | null>(null);
const form = reactive({
  name: '',
  stops: [] as { stationId: number; dwellDurationSeconds: number }[],
});

// Deactivate confirm state
const deactivatingRoute = ref<RouteSummary | null>(null);
const deactivating = ref(false);
const deactivateError = ref<string | null>(null);

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const [r, s] = await Promise.all([fetchRoutes(), fetchStations()]);
    routes.value = r;
    stations.value = s.filter((item) => item.active !== false);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Không thể tải danh sách tuyến đường.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});

const metrics = computed(() => {
  const list = routes.value;
  const active = list.filter((r) => r.active !== false).length;
  const totalMeters = list.reduce((acc, r) => acc + (r.totalDistanceMeters || 0), 0);
  return {
    total: list.length,
    active,
    totalKm: (totalMeters / 1000).toFixed(1),
  };
});

const filteredRoutes = computed(() => {
  let list = routes.value.slice();

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim();
    list = list.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.startStationName?.toLowerCase().includes(q) ||
        r.endStationName?.toLowerCase().includes(q),
    );
  }

  list.sort((a, b) => {
    if (sortBy.value === 'name_asc') return a.name.localeCompare(b.name, 'vi');
    if (sortBy.value === 'name_desc') return b.name.localeCompare(a.name, 'vi');
    if (sortBy.value === 'dist_asc') return a.totalDistanceMeters - b.totalDistanceMeters;
    if (sortBy.value === 'dist_desc') return b.totalDistanceMeters - a.totalDistanceMeters;
    return 0;
  });

  return list;
});

function formatDuration(seconds: number) {
  const mins = Math.round(seconds / 60);
  if (mins < 60) return `${mins} phút`;
  const hrs = Math.floor(mins / 60);
  const remMins = mins % 60;
  return `${hrs}h ${remMins}m`;
}

function formatDistance(meters: number) {
  return `${(meters / 1000).toFixed(1)} km`;
}

let detailRequestId = 0;
let openedRouteId: number | null = null;
async function loadDetailById(id: number) {
  const requestId = ++detailRequestId;
  openedRouteId = id;
  detailDrawerOpen.value = true;
  loadingDetail.value = true;
  viewingRoute.value = null;
  detailError.value = null;
  try {
    const detail = await fetchRouteById(id);
    if (requestId === detailRequestId) viewingRoute.value = detail;
  } catch (e) {
    if (requestId === detailRequestId)
      detailError.value = e instanceof Error ? e.message : 'Không thể tải chi tiết tuyến.';
  } finally {
    if (requestId === detailRequestId) loadingDetail.value = false;
  }
}
function openDetail(route: RouteSummary) {
  void router.push({ path: '/routes', query: { ...location.query, routeId: String(route.id) } });
}
watch(requestedRouteId, (id) => {
  if (id !== null && openedRouteId !== id) void loadDetailById(id);
  if (id === null && openedRouteId !== null) {
    detailRequestId++;
    openedRouteId = null;
    detailDrawerOpen.value = false;
  }
}, { immediate: true });
function closeDetail() {
  detailRequestId++;
  openedRouteId = null;
  detailDrawerOpen.value = false;
  detailError.value = null;
  if (requestedRouteId.value !== null)
    void router.replace({ path: '/routes', query: { ...location.query, routeId: undefined } });
}
function createTripFromRoute() {
  if (viewingRoute.value)
    void router.push({ path: '/trips', query: { routeId: String(viewingRoute.value.id), create: '1' } });
}

function openCreate() {
  form.name = '';
  form.stops = [
    { stationId: stations.value[0]?.id || 0, dwellDurationSeconds: 0 },
    { stationId: stations.value[1]?.id || 0, dwellDurationSeconds: 0 },
  ];
  formError.value = null;
  createDrawerOpen.value = true;
}

function addStop() {
  form.stops.splice(form.stops.length - 1, 0, {
    stationId: stations.value[0]?.id || 0,
    dwellDurationSeconds: 30,
  });
}

function removeStop(index: number) {
  if (form.stops.length <= 2) {
    formError.value = 'Tuyến đường cần tối thiểu 2 trạm dừng (Điểm đầu & Điểm cuối).';
    return;
  }
  form.stops.splice(index, 1);
  form.stops[0].dwellDurationSeconds = 0;
  form.stops[form.stops.length - 1].dwellDurationSeconds = 0;
}

async function saveRoute() {
  if (saving.value) return;

  const name = form.name.trim();
  if (!name) {
    formError.value = 'Vui lòng nhập tên tuyến đường.';
    return;
  }
  if (form.stops.length < 2) {
    formError.value = 'Tuyến đường phải có ít nhất 2 trạm dừng.';
    return;
  }

  // Ensure all stations are selected
  if (form.stops.some((s) => !s.stationId)) {
    formError.value = 'Vui lòng chọn trạm dừng cho tất cả các điểm.';
    return;
  }

  saving.value = true;
  formError.value = null;

  const payload: RouteCreateInput = {
    name,
    stops: form.stops.map((s) => ({
      stationId: Number(s.stationId),
      dwellDurationSeconds: Number(s.dwellDurationSeconds) || 0,
    })),
  };

  try {
    const created = await createRoute(payload);
    // Convert RouteDetail to RouteSummary for list
    routes.value.unshift({
      id: created.id,
      name: created.name,
      transportMode: created.transportMode,
      routingProvider: created.routingProvider,
      startStationName: created.stops[0]?.stationName || '',
      endStationName: created.stops[created.stops.length - 1]?.stationName || '',
      stopCount: created.stops.length,
      totalDistanceMeters: created.totalDistanceMeters,
      estimatedTravelDurationSeconds: created.estimatedTravelDurationSeconds,
      totalDwellDurationSeconds: created.totalDwellDurationSeconds,
      estimatedTripDurationSeconds: created.estimatedTripDurationSeconds,
      calculatedAt: created.calculatedAt,
      createdAt: created.createdAt,
      active: true,
    });
    createDrawerOpen.value = false;
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'Không thể tạo tuyến đường.';
  } finally {
    saving.value = false;
  }
}

function promptDeactivate(route: RouteSummary) {
  deactivatingRoute.value = route;
  deactivateError.value = null;
}

async function confirmDeactivate() {
  if (!deactivatingRoute.value || deactivating.value) return;
  deactivating.value = true;
  deactivateError.value = null;
  try {
    await deactivateRoute(deactivatingRoute.value.id);
    const target = routes.value.find((r) => r.id === deactivatingRoute.value?.id);
    if (target) target.active = false;
    deactivatingRoute.value = null;
  } catch (e) {
    deactivateError.value = e instanceof Error ? e.message : 'Không thể dừng tuyến.';
  } finally {
    deactivating.value = false;
  }
}
</script>

<template>
  <div class="business-page routes-page">
    <PageHeading
      eyebrow="QUẢN LÝ VẬN HÀNH"
      title="Tuyến đường vận chuyển"
      description="Quản lý lộ trình xe buýt/xe khách, thứ tự đón trả tại các trạm dừng và cự ly."
    >
      <template #actions>
        <button
          type="button"
          class="business-button primary"
          @click="openCreate"
        >
          <Plus :size="16" /> Tạo tuyến mới
        </button>
      </template>
    </PageHeading>

    <!-- Metrics -->
    <div class="schedule-metrics">
      <article class="business-surface">
        <div>
          <span class="panel-eyebrow">TỔNG TUYẾN ĐƯỜNG</span>
          <strong>{{ metrics.total }}</strong>
          <small>Lộ trình đã thiết lập</small>
        </div>
        <RouteIcon :size="20" class="text-sky-500" />
      </article>

      <article class="business-surface">
        <div>
          <span class="panel-eyebrow">ĐANG KHAI THÁC</span>
          <strong>{{ metrics.active }}</strong>
          <small>Tuyến sẵn sàng gán xe</small>
        </div>
        <CheckCircle2 :size="20" class="text-emerald-500" />
      </article>

      <article class="business-surface">
        <div>
          <span class="panel-eyebrow">TỔNG CỰ LY MẠNG LƯỚI</span>
          <strong>{{ metrics.totalKm }} km</strong>
          <small>Chiều dài toàn bộ tuyến</small>
        </div>
        <Clock :size="20" class="text-blue-500" />
      </article>
    </div>

    <!-- Error state -->
    <div
      v-if="error"
      class="fleet-error"
      role="alert"
    >
      <AlertCircle :size="16" />
      <span>{{ error }}</span>
      <button
        type="button"
        @click="loadData"
      >
        Thử lại
      </button>
    </div>

    <!-- Table Section -->
    <section class="business-surface business-management-surface">
      <div class="fleet-list-tools">
        <label class="fleet-search">
          <Search :size="15" />
          <input
            v-model="searchQuery"
            placeholder="Tìm theo tên tuyến hoặc trạm đầu/cuối..."
            aria-label="Tìm kiếm tuyến đường"
          />
        </label>
        <select
          v-model="sortBy"
          aria-label="Sắp xếp tuyến"
        >
          <option value="name_asc">Tên tuyến: A → Z</option>
          <option value="name_desc">Tên tuyến: Z → A</option>
          <option value="dist_asc">Cự ly: Ngắn → Dài</option>
          <option value="dist_desc">Cự ly: Dài → Ngắn</option>
        </select>
        <button
          v-if="searchQuery"
          type="button"
          class="fleet-clear-filters"
          @click="searchQuery = ''"
        >
          Xóa tìm kiếm
        </button>
      </div>

      <div class="management-table-wrap">
        <table class="management-table">
          <thead>
            <tr>
              <th>Tuyến đường</th>
              <th>Hành trình (Đầu → Cuối)</th>
              <th>Số trạm</th>
              <th>Cự ly & Thời gian</th>
              <th>Trạng thái</th>
              <th style="text-align: right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td
                colspan="6"
                class="route-table-empty"
              >
                Đang tải danh sách tuyến đường...
              </td>
            </tr>
            <tr v-else-if="filteredRoutes.length === 0">
              <td
                colspan="6"
                class="route-table-empty"
              >
                {{ searchQuery ? 'Không tìm thấy tuyến phù hợp.' : 'Chưa có tuyến đường nào.' }}
              </td>
            </tr>
            <tr
              v-for="rt in filteredRoutes"
              v-else
              :key="rt.id"
            >
              <td>
                <div class="route-name-cell">
                  <div class="route-icon-chip">
                    <RouteIcon :size="14" />
                  </div>
                  <div>
                    <strong>{{ rt.name }}</strong>
                    <span class="route-id-code">Mã: #{{ rt.id }}</span>
                  </div>
                </div>
              </td>
              <td>
                <div class="route-endpoints-flow">
                  <span class="endpoint-name">{{ rt.startStationName || 'Trạm đầu' }}</span>
                  <ArrowRight :size="12" class="endpoint-arrow" />
                  <span class="endpoint-name">{{ rt.endStationName || 'Trạm cuối' }}</span>
                </div>
              </td>
              <td>
                <span class="route-stop-count-pill">
                  <MapPin :size="12" /> {{ rt.stopCount }} trạm
                </span>
              </td>
              <td>
                <div class="route-metrics-cell">
                  <strong>{{ formatDistance(rt.totalDistanceMeters) }}</strong>
                  <small>{{ formatDuration(rt.estimatedTripDurationSeconds) }}</small>
                </div>
              </td>
              <td>
                <span
                  class="schedule-status-badge"
                  :class="rt.active !== false ? 'active' : 'inactive'"
                >
                  {{ rt.active !== false ? 'Đang hoạt động' : 'Tạm dừng' }}
                </span>
              </td>
              <td style="text-align: right">
                <div class="route-row-actions">
                  <button
                    type="button"
                    class="card-action-btn"
                    title="Xem chi tiết các điểm dừng"
                    @click="openDetail(rt)"
                  >
                    <Eye :size="15" />
                  </button>
                  <button
                    v-if="rt.active !== false"
                    type="button"
                    class="card-action-btn danger"
                    title="Tạm dừng tuyến"
                    @click="promptDeactivate(rt)"
                  >
                    <Trash2 :size="15" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- SidePanel Route Detail View -->
    <SidePanel
      v-if="detailDrawerOpen"
      class-name="schedule-editor"
      :label="viewingRoute?.name || 'Chi tiết tuyến đường'"
      :busy="loadingDetail"
      :on-close="closeDetail"
    >
      <header>
        <div>
          <span>CHI TIẾT LỘ TRÌNH</span>
          <h2>{{ viewingRoute?.name || 'Đang tải…' }}</h2>
        </div>
        <button
          type="button"
          aria-label="Đóng chi tiết"
          @click="closeDetail"
        >
          <X :size="18" />
        </button>
      </header>

      <div class="schedule-form-content">
        <div v-if="detailError" class="fleet-error" role="alert">
          {{ detailError }}
          <button type="button" @click="requestedRouteId && loadDetailById(requestedRouteId)">Thử lại</button>
        </div>
        <div v-if="loadingDetail" class="route-table-empty">
          Đang tải dữ liệu lộ trình…
        </div>
        <div v-else-if="viewingRoute" class="route-detail-flow">
          <!-- Summary chips -->
          <div class="route-detail-summary-chips">
            <div class="detail-summary-item">
              <span class="panel-eyebrow">TỔNG CỰ LY</span>
              <strong>{{ formatDistance(viewingRoute.totalDistanceMeters) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span class="panel-eyebrow">DỰ KIẾN</span>
              <strong>{{ formatDuration(viewingRoute.estimatedTripDurationSeconds) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span class="panel-eyebrow">SỐ TRẠM</span>
              <strong>{{ viewingRoute.stops.length }} điểm</strong>
            </div>
          </div>

          <!-- Stops Timeline List -->
          <h3 class="route-timeline-heading">Thứ tự các điểm dừng</h3>
          <div class="route-stops-timeline">
            <div
              v-for="(stop, index) in viewingRoute.stops"
              :key="stop.sequenceNumber"
              class="timeline-stop-item"
            >
              <div class="timeline-stop-marker">
                <span class="stop-seq-number">{{ index + 1 }}</span>
                <div v-if="index < viewingRoute.stops.length - 1" class="timeline-line" />
              </div>
              <div class="timeline-stop-content">
                <div class="timeline-stop-header">
                  <strong>{{ stop.stationName }}</strong>
                  <span
                    class="stop-role-badge"
                    :class="stop.role.toLowerCase()"
                  >
                    {{ stop.role === 'START' ? 'ĐIỂM ĐẦU' : stop.role === 'END' ? 'ĐIỂM CUỐI' : 'TRẠM DỪNG' }}
                  </span>
                </div>
                <div class="timeline-stop-meta">
                  <small>Dừng đón/trả: {{ stop.dwellDurationSeconds }} giây</small>
                  <small v-if="stop.distanceFromPreviousMeters > 0">
                    Cách trạm trước: {{ formatDistance(stop.distanceFromPreviousMeters) }}
                  </small>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <footer class="schedule-editor-footer">
        <button
          type="button"
          class="schedule-button-secondary"
          @click="closeDetail"
        >
          Đóng
        </button>
        <button
          v-if="viewingRoute && routes.some((route) => route.id === viewingRoute?.id)"
          type="button"
          class="schedule-button-primary"
          @click="createTripFromRoute"
        >
          Tạo chuyến từ tuyến này
        </button>
      </footer>
    </SidePanel>

    <!-- SidePanel Create Route -->
    <SidePanel
      v-if="createDrawerOpen"
      class-name="schedule-editor"
      label="Tạo tuyến đường mới"
      :busy="saving"
      :on-close="() => (createDrawerOpen = false)"
    >
      <header>
        <div>
          <span>THIẾT LẬP LỘ TRÌNH MỚI</span>
          <h2>Tạo tuyến đường mới</h2>
        </div>
        <button
          type="button"
          aria-label="Đóng biểu mẫu"
          :disabled="saving"
          @click="createDrawerOpen = false"
        >
          <X :size="18" />
        </button>
      </header>

      <form
        class="schedule-editor-form"
        @submit.prevent="saveRoute"
      >
        <div class="schedule-form-content">
          <p
            v-if="formError"
            class="schedule-inline-error"
            role="alert"
          >
            {{ formError }}
          </p>

          <label>
            <span class="schedule-label-title">Tên tuyến đường *</span>
            <input
              v-model="form.name"
              required
              maxlength="150"
              placeholder="Ví dụ: Tuyến số 1: BX Miền Đông - Đầm Sen"
            />
          </label>

          <div class="route-create-stops-section">
            <div class="route-create-stops-header">
              <span class="schedule-label-title">Danh sách trạm dừng theo thứ tự *</span>
              <button
                type="button"
                class="add-stop-inline-btn"
                @click="addStop"
              >
                <Plus :size="14" /> Thêm điểm dừng
              </button>
            </div>

            <div
              v-for="(st, idx) in form.stops"
              :key="idx"
              class="stop-input-row"
            >
              <div class="stop-row-seq">{{ idx + 1 }}</div>
              <div class="stop-row-select">
                <select
                  v-model.number="st.stationId"
                  required
                >
                  <option value="0" disabled>Chọn trạm dừng...</option>
                  <option
                    v-for="s in stations"
                    :key="s.id"
                    :value="s.id"
                  >
                    {{ s.name }} ({{ s.address || 'Không có địa chỉ' }})
                  </option>
                </select>
              </div>
              <div class="stop-row-dwell">
                <input
                  v-model.number="st.dwellDurationSeconds"
                  type="number"
                  :disabled="idx === 0 || idx === form.stops.length - 1"
                  min="0"
                  max="600"
                  step="5"
                  title="Thời gian dừng (giây)"
                  placeholder="Dừng (s)"
                />
              </div>
              <button
                v-if="form.stops.length > 2"
                type="button"
                class="remove-stop-btn"
                title="Xóa điểm này"
                @click="removeStop(idx)"
              >
                <X :size="15" />
              </button>
            </div>
          </div>
        </div>

        <footer class="schedule-editor-footer">
          <button
            type="button"
            class="schedule-button-secondary"
            :disabled="saving"
            @click="createDrawerOpen = false"
          >
            Hủy
          </button>
          <button
            type="submit"
            class="schedule-button-primary"
            :disabled="saving"
          >
            {{ saving ? 'Đang tính toán tuyến…' : 'Tạo tuyến đường' }}
          </button>
        </footer>
      </form>
    </SidePanel>

    <!-- Confirm Deactivate Dialog -->
    <dialog
      v-if="deactivatingRoute"
      open
      class="schedule-confirm"
      aria-label="Xác nhận dừng tuyến"
    >
      <h2>Xác nhận tạm dừng tuyến</h2>
      <p>
        Bạn có chắc muốn tạm dừng tuyến <strong>{{ deactivatingRoute.name }}</strong>? Các chuyến đi theo lịch trình của tuyến này sẽ không thể khởi hành.
      </p>
      <p
        v-if="deactivateError"
        class="schedule-inline-error"
        role="alert"
        style="margin-top: 12px"
      >
        {{ deactivateError }}
      </p>
      <div class="dialog-actions">
        <button
          type="button"
          class="schedule-button-secondary"
          :disabled="deactivating"
          @click="deactivatingRoute = null"
        >
          Hủy
        </button>
        <button
          type="button"
          class="schedule-button-danger"
          :disabled="deactivating"
          @click="confirmDeactivate"
        >
          {{ deactivating ? 'Đang xử lý…' : 'Tạm dừng tuyến' }}
        </button>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.routes-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.route-table-empty {
  text-align: center;
  padding: 40px !important;
  color: var(--text-muted);
  font-size: 14px;
}

.route-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.route-icon-chip {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #f0f9ff;
  color: #0284c7;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.route-id-code {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.route-endpoints-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.endpoint-name {
  max-width: 140px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.endpoint-arrow {
  color: #94a3b8;
  flex-shrink: 0;
}

.route-stop-count-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  color: #0369a1;
  background: #f0f9ff;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid #bae6fd;
}

.route-metrics-cell strong {
  display: block;
  font-size: 13.5px;
  color: var(--text-primary);
}

.route-metrics-cell small {
  display: block;
  font-size: 11.5px;
  color: var(--text-muted);
}

.route-row-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* Detail Timeline */
.route-detail-summary-chips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.detail-summary-item strong {
  display: block;
  font-size: 16px;
  color: #0284c7;
  margin-top: 4px;
}

.route-timeline-heading {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 8px;
}

.route-stops-timeline {
  display: flex;
  flex-direction: column;
}

.timeline-stop-item {
  display: flex;
  gap: 14px;
  position: relative;
  padding-bottom: 20px;
}

.timeline-stop-item:last-child {
  padding-bottom: 0;
}

.timeline-stop-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 28px;
  flex-shrink: 0;
}

.stop-seq-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #0284c7;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
  z-index: 1;
}

.timeline-line {
  position: absolute;
  top: 24px;
  bottom: 0;
  left: 13px;
  width: 2px;
  background: #cbd5e1;
}

.timeline-stop-content {
  flex: 1;
  background: #ffffff;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 10px 14px;
}

.timeline-stop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.timeline-stop-header strong {
  font-size: 13.5px;
  color: var(--text-primary);
}

.stop-role-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.stop-role-badge.start {
  color: #0284c7;
  background: #e0f2fe;
}

.stop-role-badge.end {
  color: #16a34a;
  background: #dcfce7;
}

.stop-role-badge.stop {
  color: #64748b;
  background: #f1f5f9;
}

.timeline-stop-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  color: var(--text-muted);
}

/* Route Create Stops */
.route-create-stops-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.route-create-stops-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.add-stop-inline-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #0284c7;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.add-stop-inline-btn:hover {
  background: #e0f2fe;
}

.stop-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stop-row-seq {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.stop-row-select {
  flex: 1;
}

.stop-row-dwell {
  width: 90px;
}

.remove-stop-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  border: 1px solid #fecdd3;
  cursor: pointer;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.remove-stop-btn:hover {
  background: #fee2e2;
}
</style>
