import { reactive } from 'vue';
import { changePassword as changePasswordRequest, fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth';
import type { AuthUser, ChangePasswordInput, LoginInput } from '../types/auth';

/** One session owner per application; no token/session state in browser storage. */
export function createAuthState() {
  const state = reactive<{ user: AuthUser | null; loading: boolean }>({ user: null, loading: true });
  let bootstrap: Promise<void> | null = null;
  let revision = 0;
  const clearSession = () => { state.user = null; state.loading = false; };
  const initialize = () => {
    if (!bootstrap) {
      const requestRevision = revision;
      bootstrap = fetchCurrentUser().then(user => {
        if (requestRevision === revision) state.user = user;
      }).catch(() => {
        if (requestRevision === revision) state.user = null;
      }).finally(() => { if (requestRevision === revision) state.loading = false; });
    }
    return bootstrap;
  };
  const login = async (input: LoginInput) => {
    const requestRevision = ++revision;
    const next = await loginRequest(input).catch(reason => {
      if (requestRevision === revision) state.loading = false;
      throw reason;
    });
    if (requestRevision !== revision) throw new Error('Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.');
    state.user = next;
    state.loading = false;
    return next;
  };
  const logout = async () => {
    const requestRevision = ++revision;
    try { await logoutRequest(); }
    finally { if (requestRevision === revision) clearSession(); }
  };
  const changePassword = async (input: ChangePasswordInput) => {
    const requestRevision = ++revision;
    try {
      await changePasswordRequest(input);
      if (requestRevision === revision) clearSession();
    } catch (reason) {
      if (requestRevision === revision && (reason as { status?: number } | null)?.status === 401) clearSession();
      throw reason;
    }
  };
  return Object.assign(state, { initialize, login, logout, changePassword });
}
export type AuthState = ReturnType<typeof createAuthState>;
