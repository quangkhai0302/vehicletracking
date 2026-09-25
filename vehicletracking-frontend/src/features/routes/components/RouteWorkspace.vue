<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import type L from 'leaflet';
import type {
  RouteCreateInput,
  RouteDetail,
  RouteDraftStop,
  RouteSummary,
} from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
import {
  createRoute,
  updateRoute,
  deactivateRoute,
  fetchRouteById,
  fetchRoutes,
} from '@/features/routes/api/routes';
import RoutePanel from './RoutePanel.vue';
import RouteDrawer from './RouteDrawer.vue';
import RouteShapeEditor from './RouteShapeEditor.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
const props = withDefaults(
  defineProps<{
    map: L.Map | null;
    stations: Station[];
    loadingStations: boolean;
    onDraftStopsChange: (stops: RouteDraftStop[]) => void;
    selectedDraftStopId: string | null;
    onFocusDraftStop: (id: string) => void;
    onFocusStop: (position: [number, number], zoom?: number) => void;
    onPlannedRouteDisplay: (route: RouteDetail | null) => void;
    onShowToast: (message: string) => void;
    refreshToken?: number;
  }>(),
  { refreshToken: 0 },
);
const routes = shallowRef<RouteSummary[]>([]),
  loadingRoutes = ref(true),
  routeError = ref<string | null>(null);
const selectedRouteId = ref<number | null>(null),
  routeDetail = shallowRef<RouteDetail | null>(null),
  loadingRouteDetail = ref(false);
const mode = ref<'closed' | 'create' | 'edit' | 'view'>('closed'),
  saving = ref(false),
  shaping = ref(false),
  listAttempt = ref(0);
useErrorToast(routeError);
let alive = true,
  detailAbort: AbortController | null = null,
  detailRequestId = 0,
  mutationId = 0,
  lastRefreshToken = props.refreshToken;
watch(
  [() => props.refreshToken, listAttempt],
  (_value, _previous, cleanup) => {
    const controller = new AbortController();
    fetchRoutes(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          routes.value = data;
          routeError.value = null;
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          routeError.value =
            error instanceof Error ? error.message : 'Không thể tải danh sách tuyến đường';
      })
      .finally(() => {
        if (!controller.signal.aborted) loadingRoutes.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
const loadDetail = (id: number, clearOnFailure: boolean) => {
  detailAbort?.abort();
  const controller = new AbortController();
  detailAbort = controller;
  const requestId = ++detailRequestId;
  const current = () => alive && !controller.signal.aborted && requestId === detailRequestId;
  fetchRouteById(id, controller.signal)
    .then((detail) => {
      if (current()) {
        routeDetail.value = detail;
        props.onPlannedRouteDisplay(detail);
      }
    })
    .catch((error: unknown) => {
      if (!current()) return;
      routeError.value =
        error instanceof Error ? error.message : 'Không thể tải chi tiết tuyến đường';
      if (clearOnFailure) {
        routeDetail.value = null;
        props.onPlannedRouteDisplay(null);
      }
    })
    .finally(() => {
      if (current()) loadingRouteDetail.value = false;
    });
  return controller;
};
watch(
  [() => props.refreshToken, selectedRouteId, mode],
  ([token, selected, currentMode], _previous, cleanup) => {
    if (token === 0 || token === lastRefreshToken) return;
    if (selected === null) {
      lastRefreshToken = token;
      return;
    }
    if (currentMode === 'edit') return;
    lastRefreshToken = token;
    const controller = loadDetail(selected, false);
    cleanup(() => {
      controller.abort();
      if (detailAbort === controller) detailAbort = null;
    });
  },
);
onBeforeUnmount(() => {
  alive = false;
  detailAbort?.abort();
  props.onPlannedRouteDisplay(null);
});
const retry = () => {
  loadingRoutes.value = true;
  routeError.value = null;
  listAttempt.value++;
};
const clearSelection = () => {
  detailAbort?.abort();
  detailRequestId++;
  mutationId++;
  saving.value = false;
  selectedRouteId.value = null;
  routeDetail.value = null;
  props.onPlannedRouteDisplay(null);
};
const selectRoute = (route: RouteSummary) => {
  if (saving.value) return;
  clearSelection();
  selectedRouteId.value = route.id;
  mode.value = 'view';
  loadingRouteDetail.value = true;
  routeError.value = null;
  loadDetail(route.id, true);
};
const beginCreate = () => {
  if (saving.value) return;
  clearSelection();
  mode.value = 'create';
  routeError.value = null;
};
const close = () => {
  if (saving.value) return;
  clearSelection();
  mode.value = 'closed';
};
const saveRoute = async (input: RouteCreateInput) => {
  if (saving.value) return;
  const editingId = mode.value === 'edit' ? routeDetail.value?.id : undefined,
    requestId = ++mutationId;
  saving.value = true;
  routeError.value = null;
  try {
    const created =
      editingId === undefined ? await createRoute(input) : await updateRoute(editingId, input);
    if (!alive) return;
    const summary: RouteSummary = {
      id: created.id,
      name: created.name,
      transportMode: created.transportMode,
      routingProvider: created.routingProvider,
      startStationName: created.stops[0]?.stationName ?? '',
      endStationName: created.stops[created.stops.length - 1]?.stationName ?? '',
      stopCount: created.stops.length,
      totalDistanceMeters: created.totalDistanceMeters,
      estimatedTravelDurationSeconds: created.estimatedTravelDurationSeconds,
      totalDwellDurationSeconds: created.totalDwellDurationSeconds,
      estimatedTripDurationSeconds: created.estimatedTripDurationSeconds,
      calculatedAt: created.calculatedAt,
      createdAt: created.createdAt,
    };
    routes.value = [summary, ...routes.value.filter((route) => route.id !== created.id)];
    if (mutationId === requestId) {
      selectedRouteId.value = created.id;
      routeDetail.value = created;
      props.onPlannedRouteDisplay(created);
      mode.value = 'view';
      props.onShowToast(
        `Đã ${editingId === undefined ? 'tạo' : 'cập nhật'} tuyến đường "${created.name}" thành công!`,
      );
    } else props.onShowToast(`Đã tạo tuyến đường "${created.name}" thành công!`);
  } catch (error) {
    if (alive && mutationId === requestId)
      routeError.value = error instanceof Error ? error.message : 'Lỗi khi tạo tuyến đường';
  } finally {
    if (alive && mutationId === requestId) saving.value = false;
  }
};
const deactivate = async () => {
  const detail = routeDetail.value;
  if (!detail || saving.value) return false;
  saving.value = true;
  routeError.value = null;
  try {
    await deactivateRoute(detail.id);
    if (!alive) return true;
    routes.value = routes.value.filter((route) => route.id !== detail.id);
    saving.value = false;
    close();
    props.onShowToast('Đã ngừng sử dụng tuyến. Lịch sử vẫn được giữ lại.');
    return true;
  } catch (error) {
    if (alive) routeError.value = error instanceof Error ? error.message : 'Không thể ngừng tuyến.';
    return false;
  } finally {
    if (alive) saving.value = false;
  }
};
const edit = () => {
  if (!saving.value && routeDetail.value) {
    routeError.value = null;
    mode.value = 'edit';
    props.onPlannedRouteDisplay(null);
  }
};
const shape = () => {
  shaping.value = true;
  props.onPlannedRouteDisplay(null);
};
const closeShape = () => {
  shaping.value = false;
  props.onPlannedRouteDisplay(routeDetail.value);
};
const savedShape = (detail: RouteDetail) => {
  shaping.value = false;
  routeDetail.value = detail;
  selectedRouteId.value = detail.id;
  mode.value = 'view';
  props.onPlannedRouteDisplay(detail);
  retry();
  props.onShowToast('Đã lưu đường đi được chỉnh trên bản đồ.');
};
</script>
<template>
  <div
    class="panel-list-slot"
    :hidden="mode !== 'closed'"
  >
    <RoutePanel
      :routes="routes"
      :selected-route-id="selectedRouteId"
      :loading="loadingRoutes"
      :error="mode === 'closed' ? routeError : null"
      :on-select-route="selectRoute"
      :on-begin-create="beginCreate"
      :create-disabled="loadingStations"
      :on-retry="retry"
    />
  </div>
  <RouteShapeEditor
    v-if="shaping && routeDetail"
    :key="routeDetail.id"
    :route="routeDetail"
    :map="map"
    :on-close="closeShape"
    :on-saved="savedShape"
  />
  <RouteDrawer
    v-else
    :mode="mode"
    :route-detail="routeDetail"
    :stations="stations"
    :on-draft-stops-change="onDraftStopsChange"
    :selected-draft-stop-id="selectedDraftStopId"
    :on-focus-draft-stop="onFocusDraftStop"
    :on-focus-stop="onFocusStop"
    :loading-detail="loadingRouteDetail"
    :saving="saving"
    :on-close="close"
    :on-save-route="saveRoute"
    :on-edit="edit"
    :on-deactivate="deactivate"
    :on-shape="shape"
  />
</template>
