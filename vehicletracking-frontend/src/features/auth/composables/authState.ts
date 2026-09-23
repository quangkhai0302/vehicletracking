import { reactive } from 'vue';
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth';
import type { AuthUser, LoginInput } from '../types/auth';

/** One session owner per application; no token/session state in browser storage. */
export function createAuthState() {
  const state = reactive<{ user: AuthUser | null; loading: boolean }>({ user: null, loading: true });
  let bootstrap: Promise<void> | null = null;
  let revision = 0;
  const initialize = () => {
    if (!bootstrap) {
      const requestRevision = revision;
      bootstrap = fetchCurrentUser().then(user => {
        if (requestRevision === revision) state.user = user;
      }).catch(() => {
        if (requestRevision === revision) state.user = null;
      }).finally(() => { state.loading = false; });
    }
    return bootstrap;
  };
  const login = async (input: LoginInput) => {
    const next = await loginRequest(input);
    revision++;
    state.user = next;
    state.loading = false;
    return next;
  };
  const logout = async () => {
    try { await logoutRequest(); }
    finally { revision++; state.user = null; state.loading = false; }
  };
  return Object.assign(state, { initialize, login, logout });
}
export type AuthState = ReturnType<typeof createAuthState>;
