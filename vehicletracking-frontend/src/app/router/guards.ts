import { watch } from 'vue';
import type { Router } from 'vue-router';
import type { AuthState } from '@/features/auth/composables/authState';
import type { UserRole } from '@/features/auth/types/auth';

declare module 'vue-router' {
  interface RouteMeta { role?: UserRole; guestOnly?: boolean }
}
export const roleHome = (role: UserRole) => role === 'DRIVER' ? '/driver/today' : '/dashboard';
export function installAuthGuards(router: Router, auth: AuthState) {
  const removeGuard = router.beforeEach(async to => {
    await auth.initialize();
    if (to.meta.role && !auth.user) return { path: '/login', replace: true, state: { from: to.path } };
    if (auth.user && ((to.meta.role && auth.user.role !== to.meta.role) || to.meta.guestOnly)) {
      return { path: roleHome(auth.user.role), replace: true };
    }
  });
  // Session loss must leave protected content even when the URL is unchanged.
  const stop = watch(() => auth.user, user => {
    if (!user && !auth.loading && router.currentRoute.value.meta.role) {
      void router.replace({ path: '/login', state: { from: router.currentRoute.value.path } });
    }
  });
  return () => { removeGuard(); stop(); };
}
