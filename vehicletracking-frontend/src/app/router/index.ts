import { h } from 'vue';
import { createRouter, createWebHistory, type RouterHistory } from 'vue-router';
import ApplicationShell from '../layouts/ApplicationShell.vue';
import DashboardPage from '@/pages/DashboardPage.vue';
import AlertsManagementPage from '@/pages/AlertsManagementPage.vue';
import FleetManagementPage from '@/pages/FleetManagementPage.vue';
import ReportsPage from '@/pages/ReportsPage.vue';
import ScheduleManagementPage from '@/pages/ScheduleManagementPage.vue';
import LoginPage from '@/pages/LoginPage.vue';
import DriverPortalPage from '@/pages/DriverPortalPage.vue';
import DriverNavigationPage from '@/pages/DriverNavigationPage.vue';
import UserManagementPage from '@/pages/UserManagementPage.vue';
import AdminRegistrationPage from '@/pages/AdminRegistrationPage.vue';
import NotFoundPage from '@/pages/NotFoundPage.vue';
import MapPage from '@/pages/MapPage.vue';
import PlanningManagementPage from '@/pages/PlanningManagementPage.vue';

export function createApplicationRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({ history, linkActiveClass: '', linkExactActiveClass: '', routes: [
    { path: '/login', component: LoginPage, meta: { guestOnly: true } },
    { path: '/register', component: AdminRegistrationPage, meta: { guestOnly: true } },
    { path: '/driver/:view(today|schedules)', component: DriverPortalPage, meta: { role: 'DRIVER' } },
    { path: '/driver/trips/:tripId(\\d+)/navigate', component: DriverNavigationPage, meta: { role: 'DRIVER' } },
    { path: '/', component: ApplicationShell, meta: { role: 'ADMIN' }, children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: DashboardPage },
      { path: 'operations', component: MapPage, props: { workspace: 'tracking', mapKey: 'operations-map' } },
      { path: 'routes', component: PlanningManagementPage, props: { tab: 'routes' } },
      { path: 'trips', component: PlanningManagementPage, props: { tab: 'trips' } },
      { path: 'stations', component: MapPage, props: { workspace: 'stations', mapKey: 'stations-map' } },
      // Distinct wrappers retain React's explicit per-tab remount/reset boundary.
      ...(['vehicles', 'drivers'] as const).map(tab => ({ path: tab, component: { render: () => h(FleetManagementPage, { key: `${tab}-page`, tab }) } })),
      { path: 'alerts', component: AlertsManagementPage },
      { path: 'reports', component: ReportsPage },
      { path: 'schedules', component: ScheduleManagementPage },
      { path: 'users', component: UserManagementPage },
      { path: ':pathMatch(.*)*', component: NotFoundPage },
    ] },
  ] });
}
