<script setup lang="ts">
import { computed, onScopeDispose, provide, ref, shallowRef, watch } from 'vue';
import {
  BookOpen,
  LogOut,
  Menu,
  Navigation,
  PanelLeftClose,
  PanelLeftOpen,
  UserRound,
} from '@lucide/vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import {
  findRoute,
  matchesNavigationItem,
  navigationGroups,
  type NavigationItem,
} from '../navigation';
import { useAuth } from '@/features/auth/composables/useAuth';
import {
  liveOperationsKey,
  useLiveOperations,
} from '@/features/tracking/composables/useLiveOperations';
import { useDriverRouteNotifications } from '@/features/tracking/composables/useDriverRouteNotifications';
import './application-shell.css';
import AdminNotificationBell from '@/features/reports/components/AdminNotificationBell.vue';
import { adminNotificationMenuKey } from '@/features/reports/composables/adminNotificationMenu';
import { useAdminAssignmentNotifications } from '@/features/reports/composables/useAdminAssignmentNotifications';
const location = useRoute(),
  router = useRouter(),
  auth = useAuth();
const notificationsOpen = ref(false);
provide(adminNotificationMenuKey, () => {
  notificationsOpen.value = true;
});
watch(
  () => location.fullPath,
  () => {
    notificationsOpen.value = location.query.notifications === 'open';
  },
  { immediate: true },
);
watch(notificationsOpen, (open) => {
  if (!open && location.query.notifications === 'open') {
    const query = { ...location.query };
    delete query.notifications;
    void router.replace({ path: location.path, query, hash: location.hash });
  }
});
const live = useLiveOperations();
provide(liveOperationsKey, live);
useDriverRouteNotifications(() => live.snapshot);
useAdminAssignmentNotifications(() => auth.user?.role === 'ADMIN' ? live.snapshot : null);
const route = computed(() => findRoute(location.path));
const navigationOpen = ref(false),
  mapNavigationExpanded = ref(false);
const menuButton = shallowRef<HTMLButtonElement | null>(null),
  sidebar = shallowRef<HTMLElement | null>(null),
  sidebarCloseButton = shallowRef<HTMLButtonElement | null>(null);
let focusFrame = 0;
function closeNavigation() {
  const wasOpen = navigationOpen.value;
  navigationOpen.value = false;
  if (wasOpen) {
    cancelAnimationFrame(focusFrame);
    focusFrame = requestAnimationFrame(() => menuButton.value?.focus());
  }
}
watch(
  navigationOpen,
  (open, _old, cleanup) => {
    if (!open) return;
    sidebarCloseButton.value?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeNavigation();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = [
        ...(sidebar.value?.querySelectorAll<HTMLElement>('a[href],button:not([disabled])') ?? []),
      ].filter((el) => el.getClientRects().length > 0);
      const first = focusable[0],
        last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown', keydown);
    cleanup(() => window.removeEventListener('keydown', keydown));
  },
  { flush: 'post' },
);
onScopeDispose(() => cancelAnimationFrame(focusFrame));
const activePath = (item: NavigationItem) => matchesNavigationItem(item, location.path);
</script>
<template>
  <div
    :class="['business-shell', route.fullBleed ? 'business-shell-full' : 'business-ui']"
    :data-navigation-open="navigationOpen"
    :data-map-focus="route.fullBleed === true"
    :data-map-navigation-expanded="mapNavigationExpanded"
  >
    <a
      v-if="!route.fullBleed"
      class="business-skip-link"
      href="#main-content"
      >Đi đến nội dung chính</a
    >
    <button
      class="business-shell-overlay"
      aria-label="Đóng điều hướng"
      :tabindex="navigationOpen ? 0 : -1"
      @click="closeNavigation"
    />
    <aside
      ref="sidebar"
      class="business-sidebar"
      aria-label="Thanh điều hướng ứng dụng"
    >
      <div class="business-brand">
        <span class="business-brand-mark"><Navigation :size="19" /></span
        ><span><strong>Vehicle Tracking</strong><small>Operations Manager</small></span>
        <button
          class="business-map-sidebar-toggle"
          :aria-label="
            mapNavigationExpanded
              ? 'Thu gọn thanh điều hướng để mở rộng bản đồ'
              : 'Mở rộng thanh điều hướng'
          "
          :aria-expanded="mapNavigationExpanded"
          @click="mapNavigationExpanded = !mapNavigationExpanded"
        >
          <PanelLeftClose
            v-if="mapNavigationExpanded"
            :size="19"
          /><PanelLeftOpen
            v-else
            :size="19"
          />
        </button>
        <button
          ref="sidebarCloseButton"
          class="business-sidebar-close"
          aria-label="Đóng điều hướng"
          @click="closeNavigation"
        >
          <PanelLeftClose :size="19" />
        </button>
      </div>
      <nav
        class="business-navigation"
        aria-label="Điều hướng chính"
      >
        <section
          v-for="group in navigationGroups"
          :key="group.label"
        >
          <h2>{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :title="route.fullBleed && !mapNavigationExpanded ? item.label : undefined"
            :aria-label="route.fullBleed && !mapNavigationExpanded ? item.label : undefined"
            :class="activePath(item) ? 'active' : undefined"
            @click="closeNavigation"
            ><component
              :is="item.icon"
              :size="18"
            /><span>{{ item.label }}</span
            ><small v-if="item.planned">Roadmap</small></RouterLink
          >
        </section>
      </nav>
      <a
        class="business-guide-link"
        href="/huong-dan/index.html"
        target="_blank"
        rel="noreferrer"
        ><BookOpen :size="17" /><span>Hướng dẫn sử dụng</span></a
      >
    </aside>
    <div class="business-shell-main">
      <header class="business-topbar">
        <button
          ref="menuButton"
          class="business-menu-button"
          aria-label="Mở điều hướng"
          :aria-expanded="navigationOpen"
          @click="navigationOpen = true"
        >
          <Menu :size="20" />
        </button>
        <div class="business-topbar-copy">
          <h1>{{ route.title }}</h1>
        </div>
        <div class="business-topbar-actions">
          <AdminNotificationBell
            v-model="notificationsOpen"
            :live-notifications="live.snapshot?.notifications ?? null"
          />
          <div class="business-account">
            <div class="business-account-avatar"><UserRound :size="16" /></div>
            <div class="business-account-copy">
              <strong>{{ auth.user?.username }}</strong>
              <small>Quản trị viên</small>
            </div>
            <button
              type="button"
              class="business-logout-button"
              aria-label="Đăng xuất"
              title="Đăng xuất khỏi hệ thống"
              @click="auth.logout"
            >
              <LogOut :size="16" />
            </button>
          </div>
        </div>
      </header>
      <main
        id="main-content"
        :tabindex="route.fullBleed ? undefined : -1"
        :class="route.fullBleed ? 'business-content business-content-full' : 'business-content'"
      >
        <RouterView />
      </main>
    </div>
  </div>
</template>
