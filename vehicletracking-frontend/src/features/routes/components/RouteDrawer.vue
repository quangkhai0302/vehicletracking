<script setup lang="ts">
import type { RouteCreateInput, RouteDetail, RouteDraftStop } from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
import RouteCreateContent from './RouteCreateContent.vue';
import RouteViewContent from './RouteViewContent.vue';
defineProps<{
  onShape: () => void;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  mode: 'closed' | 'create' | 'edit' | 'view';
  routeDetail: RouteDetail | null;
  stations: Station[];
  loadingDetail: boolean;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
  onEdit: () => void;
  onDeactivate: () => Promise<boolean>;
  onDraftStopsChange: (stops: RouteDraftStop[]) => void;
  selectedDraftStopId: string | null;
  onFocusDraftStop: (id: string) => void;
}>();
</script>
<template>
  <aside
    v-if="mode !== 'closed'"
    class="route-drawer"
    :aria-label="
      mode === 'create'
        ? 'Tạo tuyến đường'
        : mode === 'edit'
          ? 'Sửa tuyến đường'
          : 'Chi tiết tuyến đường'
    "
  >
    <RouteCreateContent
      v-if="mode === 'create' || mode === 'edit'"
      :key="mode === 'edit' ? `edit-${routeDetail?.id}` : 'create'"
      :initial-route="mode === 'edit' ? (routeDetail ?? undefined) : undefined"
      :on-draft-stops-change="onDraftStopsChange"
      :selected-draft-stop-id="selectedDraftStopId"
      :on-focus-draft-stop="onFocusDraftStop"
      :stations="stations"
      :saving="saving"
      :error="error"
      :on-close="onClose"
      :on-save-route="onSaveRoute"
    />
    <RouteViewContent
      v-if="mode === 'view'"
      :key="routeDetail?.id ?? 'view'"
      :on-shape="onShape"
      :saving="saving"
      :on-edit="onEdit"
      :on-deactivate="onDeactivate"
      :on-focus-stop="onFocusStop"
      :route-detail="routeDetail"
      :loading-detail="loadingDetail"
      :error="error"
      :on-close="onClose"
    />
  </aside>
</template>
