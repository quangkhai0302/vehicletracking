<script setup lang="ts">
import { ClipboardList, Route as RouteIcon } from '@lucide/vue';
import { RouterLink } from 'vue-router';
import FleetManagementPage from './FleetManagementPage.vue';
import RouteManagementPage from './RouteManagementPage.vue';
import '@/features/planning/styles/planning-workspace.css';

defineProps<{ tab: 'routes' | 'trips' }>();
</script>

<template>
  <div class="planning-workspace">
    <nav class="planning-workspace-tabs" aria-label="Quản lý tuyến và chuyến">
      <RouterLink to="/routes" :aria-current="tab === 'routes' ? 'page' : undefined">
        <RouteIcon :size="18" />
        <span><strong>Tuyến đường</strong><small>Lộ trình và điểm dừng</small></span>
      </RouterLink>
      <RouterLink to="/trips" :aria-current="tab === 'trips' ? 'page' : undefined">
        <ClipboardList :size="18" />
        <span><strong>Chuyến đi</strong><small>Phân công và vận hành</small></span>
      </RouterLink>
    </nav>

    <RouteManagementPage v-if="tab === 'routes'" />
    <FleetManagementPage v-else tab="trips" />
  </div>
</template>
