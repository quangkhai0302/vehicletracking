import { watch } from 'vue';
import type { Router } from 'vue-router';
import type { AuthState } from '@/features/auth/composables/authState';
import type { AuthUser, UserRole } from '@/features/auth/types/auth';

declare module 'vue-router' {
  interface RouteMeta { role?: UserRole; guestOnly?: boolean }
}
export const roleHome = (role: UserRole) => role === 'DRIVER' ? '/driver/today' : '/dashboard';
export const authHome = (user: AuthUser) => user.role === 'DRIVER' && user.passwordChangeRequired
  ? '/driver/change-password'
  : roleHome(user.role);
export function installAuthGuards(router: Router, auth: AuthState) {
  const removeGuard = router.beforeEach(async to => {
    await auth.initialize();
    if (auth.user?.role === 'DRIVER' && auth.user.passwordChangeRequired && to.path !== '/driver/change-password') {
      return { path: '/driver/change-password', replace: true };
    }
    if (to.meta.role && !auth.user) return { path: '/login', replace: true, state: { from: to.path } };
    if (auth.user && ((to.meta.role && auth.user.role !== to.meta.role) || to.meta.guestOnly)) {
      return { path: authHome(auth.user), replace: true };
    }
  });
  // Session loss must leave protected content even when the URL is unchanged.
  const stop = watch(() => auth.user, user => {
    if (!user && !auth.loading && router.currentRoute.value.meta.role) {
      void router.replace({ path: '/login', state: { from: router.currentRoute.value.path } });
    } else if (user?.role === 'DRIVER' && user.passwordChangeRequired && router.currentRoute.value.path !== '/driver/change-password') {
      void router.replace('/driver/change-password');
    }
  });
  return () => { removeGuard(); stop(); };
}
