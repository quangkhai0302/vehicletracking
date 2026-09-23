import { inject, type InjectionKey } from 'vue';
import type { AuthState } from './authState';
export const authKey: InjectionKey<AuthState> = Symbol('vehicletracking.auth');
export function useAuth(): AuthState {
  const auth = inject(authKey);
  if (!auth) throw new Error('useAuth must be used inside the application session provider');
  return auth;
}
