<script setup lang="ts">
import { computed, onMounted, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  MapPin,
  Plus,
  RefreshCw,
  Route as RouteIcon,
  Ruler,
  Search,
  SearchX,
  Trash2,
  X,
} from '@lucide/vue';
import PageHeading from '@/shared/components/PageHeading.vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import RouteDeactivateConfirm from '@/features/routes/components/RouteDeactivateConfirm.vue';
import RouteMapDetail from '@/features/routes/components/RouteMapDetail.vue';
import {
  createRoute,
  deactivateRoute,
  fetchRouteById,
  fetchRoutes,
} from '@/features/routes/api/routes';
import { fetchStations } from '@/features/stations/api/stations';
import type { RouteCreateInput, RouteDetail, RouteSummary } from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';

const location = useRoute();
const router = useRouter();
const requestedRouteId = computed(() => {
  const raw = Array.isArray(location.query.routeId)
    ? location.query.routeId[0]
    : location.query.routeId;
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
useErrorToast(error);
useErrorToast(detailError);
useErrorToast(deactivateError);

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
let detailAbort: AbortController | null = null;
onScopeDispose(() => {
  detailRequestId++;
  detailAbort?.abort();
});
let openedRouteId: number | null = null;
async function loadDetailById(id: number) {
  const requestId = ++detailRequestId;
  detailAbort?.abort();
  detailAbort = new AbortController();
  openedRouteId = id;
  detailDrawerOpen.value = true;
  loadingDetail.value = true;
  viewingRoute.value = null;
  detailError.value = null;
  try {
    const detail = await fetchRouteById(id, detailAbort.signal);
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
watch(
  requestedRouteId,
  (id) => {
    if (id !== null && openedRouteId !== id) void loadDetailById(id);
    if (id === null && openedRouteId !== null) {
      detailRequestId++;
      detailAbort?.abort();
      openedRouteId = null;
      detailDrawerOpen.value = false;
    }
  },
  { immediate: true },
);
function closeDetail() {
  detailRequestId++;
  detailAbort?.abort();
  openedRouteId = null;
  detailDrawerOpen.value = false;
  detailError.value = null;
  if (requestedRouteId.value !== null)
    void router.replace({ path: '/routes', query: { ...location.query, routeId: undefined } });
}
function createTripFromRoute() {
  if (viewingRoute.value)
    void router.push({
      path: '/trips',
      query: { routeId: String(viewingRoute.value.id), create: '1' },
    });
}

function savedRoute(detail: RouteDetail) {
  viewingRoute.value = detail;
  openedRouteId = detail.id;
  const current = routes.value.find((route) => route.id === detail.id);
  const summary: RouteSummary = {
    id: detail.id,
    name: detail.name,
    transportMode: detail.transportMode,
    routingProvider: detail.routingProvider,
    startStationName: detail.stops[0]?.stationName || '',
    endStationName: detail.stops[detail.stops.length - 1]?.stationName || '',
    stopCount: detail.stops.length,
    totalDistanceMeters: detail.totalDistanceMeters,
    estimatedTravelDurationSeconds: detail.estimatedTravelDurationSeconds,
    totalDwellDurationSeconds: detail.totalDwellDurationSeconds,
    estimatedTripDurationSeconds: detail.estimatedTripDurationSeconds,
    calculatedAt: detail.calculatedAt,
    createdAt: detail.createdAt,
    active: current?.active ?? true,
  };
  if (current)
    routes.value = routes.value.map((route) => (route.id === detail.id ? summary : route));
  else routes.value.unshift(summary);
  void router.replace({
    path: '/routes',
    query: { ...location.query, routeId: String(detail.id) },
  });
  notifySuccess(`Đã lưu tuyến đường “${detail.name}”.`);
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
    notifySuccess(`Đã tạo tuyến đường “${created.name}”.`);
  } catch (e) {
    notifyError(e instanceof Error ? e.message : 'Không thể tạo tuyến đường.');
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
    notifySuccess('Đã tạm dừng tuyến đường.');
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
      title="Tuyến đường"
      description="Quản lý lộ trình xe buýt/xe khách, thứ tự đón trả tại các trạm dừng và cự ly."
    >
      <template #actions>
        <button
          type="button"
          class="route-refresh-button"
          :disabled="loading"
          aria-label="Tải lại danh sách tuyến đường"
          title="Tải lại danh sách tuyến đường"
          @click="loadData"
        >
          <RefreshCw
            :size="16"
            :class="{ 'is-spinning': loading }"
          />
        </button>
        <button
          type="button"
          class="business-button primary"
          :disabled="loading"
          @click="openCreate"
        >
          <Plus :size="16" /> Tạo tuyến mới
        </button>
      </template>
    </PageHeading>

    <div
      class="route-summary-grid"
      aria-label="Tổng quan tuyến đường"
    >
      <article class="route-summary-card total">
        <span class="route-summary-icon"><RouteIcon :size="20" /></span>
        <div>
          <span>Tổng tuyến đường</span>
          <strong>{{ metrics.total }}</strong>
          <small>Lộ trình đã thiết lập</small>
        </div>
      </article>

      <article class="route-summary-card active">
        <span class="route-summary-icon"><CheckCircle2 :size="20" /></span>
        <div>
          <span>Đang khai thác</span>
          <strong>{{ metrics.active }}</strong>
          <small>Tuyến sẵn sàng gán xe</small>
        </div>
      </article>

      <article class="route-summary-card distance">
        <span class="route-summary-icon"><Ruler :size="20" /></span>
        <div>
          <span>Tổng cự ly mạng lưới</span>
          <strong>{{ metrics.totalKm }} km</strong>
          <small>Chiều dài toàn bộ tuyến</small>
        </div>
      </article>
    </div>

    <section class="business-management-surface route-management-section">
      <header class="route-list-heading">
        <div>
          <span class="panel-eyebrow">DANH SÁCH TUYẾN</span>
          <h3>{{ filteredRoutes.length }} kết quả{{ searchQuery ? ' phù hợp' : '' }}</h3>
        </div>
        <span class="route-data-state"><CheckCircle2 :size="14" /> Dữ liệu hiện tại</span>
      </header>

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

      <div
        class="route-list-body"
        :aria-busy="loading"
      >
        <div
          v-if="loading"
          class="route-empty-state"
          role="status"
        >
          <RefreshCw
            :size="30"
            class="is-spinning"
          />
          <h3>Đang tải tuyến đường</h3>
          <p>Hệ thống đang đồng bộ danh sách tuyến và trạm dừng.</p>
        </div>
        <div
          v-else-if="filteredRoutes.length === 0"
          class="route-empty-state"
        >
          <SearchX :size="32" />
          <h3>{{ searchQuery ? 'Không tìm thấy tuyến phù hợp' : 'Chưa có tuyến đường' }}</h3>
          <p>
            {{
              searchQuery
                ? 'Thử thay đổi từ khóa hoặc xóa tìm kiếm để xem toàn bộ tuyến.'
                : 'Tạo tuyến đầu tiên từ các trạm dừng đang hoạt động.'
            }}
          </p>
        </div>
        <div
          v-else
          class="management-table-wrap"
        >
          <table class="management-table">
            <caption class="business-sr-only">
              Danh sách tuyến đường
            </caption>
            <thead>
              <tr>
                <th>Tuyến đường</th>
                <th>Hành trình (Đầu → Cuối)</th>
                <th>Số trạm</th>
                <th>Cự ly & Thời gian</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="rt in filteredRoutes"
                :key="rt.id"
                :class="{ 'is-inactive': rt.active === false }"
              >
                <td data-label="Tuyến đường">
                  <div class="management-identity route-name-cell">
                    <span class="management-avatar route-icon-chip">
                      <RouteIcon :size="16" />
                    </span>
                    <div>
                      <strong>{{ rt.name }}</strong>
                      <small class="route-id-code">Mã tuyến #{{ rt.id }}</small>
                    </div>
                  </div>
                </td>
                <td data-label="Hành trình">
                  <div class="route-endpoints-flow">
                    <span class="endpoint-name">{{ rt.startStationName || 'Trạm đầu' }}</span>
                    <ArrowRight
                      :size="13"
                      class="endpoint-arrow"
                    />
                    <span class="endpoint-name">{{ rt.endStationName || 'Trạm cuối' }}</span>
                  </div>
                </td>
                <td data-label="Số trạm">
                  <span class="route-stop-count-pill">
                    <MapPin :size="13" /> {{ rt.stopCount }} trạm
                  </span>
                </td>
                <td data-label="Cự ly / thời gian">
                  <div class="route-metrics-cell">
                    <strong>{{ formatDistance(rt.totalDistanceMeters) }}</strong>
                    <small>{{ formatDuration(rt.estimatedTripDurationSeconds) }}</small>
                  </div>
                </td>
                <td data-label="Trạng thái">
                  <span
                    class="business-status"
                    :class="rt.active !== false ? 'success' : 'neutral'"
                  >
                    <i aria-hidden="true" />
                    {{ rt.active !== false ? 'Đang hoạt động' : 'Tạm dừng' }}
                  </span>
                </td>
                <td data-label="Thao tác">
                  <div class="route-row-actions">
                    <button
                      type="button"
                      class="management-detail-button"
                      title="Xem chi tiết các điểm dừng"
                      @click="openDetail(rt)"
                    >
                      Chi tiết <ArrowUpRight :size="15" />
                    </button>
                    <button
                      v-if="rt.active !== false"
                      type="button"
                      class="route-deactivate-button"
                      title="Tạm dừng tuyến"
                      :aria-label="`Tạm dừng tuyến ${rt.name}`"
                      @click="promptDeactivate(rt)"
                    >
                      <Trash2 :size="15" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <footer class="management-table-footer">
            Hiển thị {{ filteredRoutes.length }} / {{ routes.length }} tuyến đường
          </footer>
        </div>
      </div>
    </section>

    <RouteMapDetail
      v-if="detailDrawerOpen"
      :key="requestedRouteId ?? 'detail'"
      :route="viewingRoute"
      :loading="loadingDetail"
      :error="detailError"
      :editable="routes.some((route) => route.id === viewingRoute?.id && route.active !== false)"
      :on-close="closeDetail"
      :on-retry="
        () => {
          if (openedRouteId !== null) void loadDetailById(openedRouteId);
        }
      "
      :on-saved="savedRoute"
      :on-create-trip="createTripFromRoute"
    />

    <!-- SidePanel Create Route -->
    <SidePanel
      v-if="createDrawerOpen"
      class-name="schedule-editor"
      label="Tạo tuyến đường mới"
      :busy="saving"
      content-sized
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
                  <option
                    value="0"
                    disabled
                  >
                    Chọn trạm dừng...
                  </option>
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

    <RouteDeactivateConfirm
      v-if="deactivatingRoute"
      :route="deactivatingRoute"
      :busy="deactivating"
      :on-close="() => (deactivatingRoute = null)"
      :on-confirm="confirmDeactivate"
    />
  </div>
</template>

<style scoped>
.routes-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.route-refresh-button {
  width: 40px;
  height: 40px;
  min-width: 40px;
  padding: 0;
  display: grid;
  place-items: center;
  color: #475569;
  background: #ffffff;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.03);
  transition: all 140ms ease;
  cursor: pointer;
}

.route-refresh-button:hover:not(:disabled) {
  background: #f8fafc;
  color: #0284c7;
  border-color: var(--border-strong);
  transform: none;
}

.route-refresh-button:disabled,
.routes-page .business-button.primary:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.route-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.route-summary-card {
  --route-summary-accent: #0284c7;
  --route-summary-soft: #e0f2fe;
  --route-summary-ink: #0369a1;
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 96px;
  gap: 13px;
  overflow: hidden;
  padding: 16px 18px;
  background: #ffffff;
  border: 1px solid var(--border-default);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    transform 150ms ease;
}

.route-summary-card::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  background: var(--route-summary-accent);
  content: '';
}

.route-summary-card:hover {
  border-color: #cbd8e6;
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.route-summary-card.active {
  --route-summary-accent: #10b981;
  --route-summary-soft: #d1fae5;
  --route-summary-ink: #047857;
}

.route-summary-card.distance {
  --route-summary-accent: #8b5cf6;
  --route-summary-soft: #ede9fe;
  --route-summary-ink: #6d28d9;
}

.route-summary-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  color: var(--route-summary-ink);
  background: var(--route-summary-soft);
  border-radius: 11px;
}

.route-summary-card > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.route-summary-card > div > span {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-summary-card strong {
  margin-top: 2px;
  color: var(--text-primary);
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 1.1;
}

.route-summary-card small {
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 10.5px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-management-section {
  min-width: 0;
}

.route-list-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
}

.route-list-heading .panel-eyebrow {
  display: none;
}

.route-list-heading h3 {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 700;
}

.route-data-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: 12px;
}

.route-data-state svg {
  color: #059669;
}

.route-list-body {
  min-height: 160px;
  overflow: hidden;
  background: rgb(255 255 255 / 0.96);
  border: 1px solid var(--border-default);
  border-radius: 0 0 16px 16px;
  box-shadow: var(--shadow-card);
}

.route-empty-state {
  display: grid;
  min-height: 220px;
  padding: 36px 24px;
  place-items: center;
  align-content: center;
  color: var(--text-muted);
  text-align: center;
}

.route-empty-state > svg {
  margin-bottom: 12px;
  color: #0284c7;
}

.route-empty-state h3 {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 700;
}

.route-empty-state p {
  max-width: 430px;
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.55;
}

.is-spinning {
  animation: route-spin 850ms linear infinite;
}

@keyframes route-spin {
  to {
    transform: rotate(360deg);
  }
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
  min-width: 190px;
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
  margin-top: 2px;
  white-space: nowrap;
}

.route-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.route-row-actions .management-detail-button {
  float: none;
}

.route-deactivate-button {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  transition: all 150ms ease;
}

.route-deactivate-button:hover {
  color: #e11d48;
  background: #fff1f2;
  border-color: #fecdd3;
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

@media (max-width: 900px) {
  .route-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .route-summary-card.distance {
    grid-column: 1 / -1;
  }
}

@media (max-width: 700px) {
  .route-summary-grid {
    gap: 10px;
  }

  .route-summary-card {
    min-height: 82px;
    gap: 10px;
    padding: 14px;
  }

  .route-summary-icon {
    width: 36px;
    height: 36px;
    flex-basis: 36px;
  }

  .route-summary-card > div > span {
    font-size: 10.5px;
    white-space: normal;
  }

  .route-summary-card strong {
    font-size: 20px;
  }

  .route-summary-card small,
  .route-data-state {
    display: none;
  }

  .route-list-heading {
    padding-bottom: 12px;
  }

  .route-endpoints-flow {
    justify-content: flex-end;
    min-width: 0;
    text-align: right;
  }

  .endpoint-name {
    max-width: min(28vw, 150px);
  }

  .route-row-actions {
    margin-left: auto;
  }
}

@media (max-width: 480px) {
  .route-summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .route-summary-card.distance {
    grid-column: auto;
  }

  .route-endpoints-flow {
    align-items: flex-end;
    flex-direction: column;
    gap: 3px;
  }

  .endpoint-arrow {
    display: none;
  }
}
</style>
