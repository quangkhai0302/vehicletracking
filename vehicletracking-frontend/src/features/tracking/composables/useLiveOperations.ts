import { hasInjectionContext, inject, reactive, ref, shallowRef, watch, type InjectionKey } from 'vue';
import { fetchOperations, subscribeOperations } from '@/features/tracking/api/operations';
import type { OperationsSnapshot, StreamConnection } from '@/features/tracking/types/operations';

export interface LiveOperationsState {
  snapshot: OperationsSnapshot | null;
  connection: StreamConnection;
  now: number;
  error: string | null;
  reconnect: () => void;
}
export const liveOperationsKey: InjectionKey<LiveOperationsState> = Symbol('live-operations');

export function useLiveOperations(): LiveOperationsState {
  const shared = hasInjectionContext() ? inject(liveOperationsKey, null) : null;
  if (shared) return shared;
  const snapshot = shallowRef<OperationsSnapshot | null>(null);
  const connection = ref<StreamConnection>('connecting');
  const now = ref(Date.now()), attempt = ref(0), error = ref<string | null>(null);
  let offset = 0;
  watch(attempt, (_value, _previous, cleanup) => {
    let alive = true, lastMessage = Date.now(), newest = 0;
    const abort = new AbortController();
    const accept = (data: OperationsSnapshot) => {
      const time = Date.parse(data.serverTime);
      if (!alive || !Number.isFinite(time) || time < newest) return;
      newest = time; offset = time - Date.now();
      snapshot.value = data; error.value = null;
    };
    const disconnect = () => { if (alive) connection.value = 'reconnecting'; };
    const stop = subscribeOperations(data => {
      if (!alive) return;
      lastMessage = Date.now(); accept(data); connection.value = 'live';
    }, disconnect);
    fetchOperations(abort.signal).then(accept).catch((reason: unknown) => {
      if (alive && !abort.signal.aborted && newest === 0) error.value = reason instanceof Error ? reason.message : 'Không thể tải vị trí xe.';
    });
    const timer = window.setInterval(() => {
      now.value = Date.now() + offset;
      if (Date.now() - lastMessage > 5000) disconnect();
    }, 1000);
    cleanup(() => { alive = false; abort.abort(); stop(); window.clearInterval(timer); });
  }, { immediate: true });
  return reactive({ snapshot, connection, now, error,
    reconnect: () => { connection.value = 'connecting'; error.value = null; attempt.value++; } });
}
