import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { applyDriverRouteOption, fetchDriverNavigation, fetchDriverRouteOptions, startDriverTrip } from '@/features/fleet/api/driverPortal';
import type { DriverNavigationSnapshot, DriverRouteOptions } from '@/features/fleet/types/driverNavigation';

export function useDriverNavigation(tripId: MaybeRefOrGetter<number>) {
  const snapshot = shallowRef<DriverNavigationSnapshot | null>(null);
  const options = shallowRef<DriverRouteOptions | null>(null);
  const selectedIndex = ref<number | null>(null), loading = ref(true), busy = ref(false);
  const readError = ref<string | null>(null), actionError = ref<string | null>(null);
  const now = ref(Date.now()), receivedAt = ref(0), attempt = ref(0);
  let generation = 0, alive = false;
  let read: AbortController | null = null, action: AbortController | null = null;
  let poll: ReturnType<typeof setTimeout> | undefined;
  let offset = 0;
  const message = (reason: unknown) => reason instanceof Error ? reason.message : 'Không thể cập nhật chuyến.';
  const clearOptions = () => { options.value = null; selectedIndex.value = null; };
  const accept = (next: DriverNavigationSnapshot) => {
    if (next.trip.id !== toValue(tripId)) return;
    const time = Date.parse(next.serverTime);
    if (!Number.isFinite(time)) throw new Error('Dữ liệu chuyến không hợp lệ.');
    if (snapshot.value && time < Date.parse(snapshot.value.serverTime)) return;
    if (options.value && (options.value.routeRevisionId !== next.routeRevisionId ||
      next.trip.attemptNumber !== snapshot.value?.trip.attemptNumber || next.simulation?.status !== 'RUNNING')) clearOptions();
    snapshot.value = next; offset = time - Date.now(); now.value = time;
    receivedAt.value = Date.now(); readError.value = null; loading.value = false;
  };
  const schedule = () => {
    clearTimeout(poll);
    if (alive) poll = setTimeout(() => { void refresh(); }, 1000);
  };
  async function refresh() {
    if (!alive || busy.value) { schedule(); return; }
    const version = generation;
    const controller = new AbortController(); read = controller;
    try {
      const next = await fetchDriverNavigation(toValue(tripId), controller.signal);
      if (alive && version === generation && !controller.signal.aborted) accept(next);
    } catch (reason) {
      if (alive && version === generation && !controller.signal.aborted) {
        readError.value = message(reason); loading.value = false;
      }
    } finally {
      if (read === controller) read = null;
      if (alive && version === generation) schedule();
    }
  }
  watch([() => toValue(tripId), attempt], (_value, _old, cleanup) => {
    generation++; alive = true; busy.value = false;
    snapshot.value = null; receivedAt.value = 0; loading.value = true;
    actionError.value = null; readError.value = null; clearOptions();
    void refresh();
    const clock = setInterval(() => { now.value = Date.now() + offset; }, 1000);
    cleanup(() => {
      alive = false; generation++; read?.abort(); action?.abort();
      clearTimeout(poll); clearInterval(clock);
    });
  }, { immediate: true });
  const connected = computed(() => receivedAt.value > 0 && !readError.value && now.value - offset - receivedAt.value < 5000);
  const canChange = computed(() => connected.value && snapshot.value?.trip.status === 'IN_PROGRESS' &&
    snapshot.value.simulation?.status === 'RUNNING' && !snapshot.value.simulation.frame?.dwelling && !busy.value);
  const optionsExpired = computed(() => !options.value || now.value >= Date.parse(options.value.expiresAt));

  async function mutate(kind: 'start' | 'options' | 'apply') {
    if (!alive || busy.value || !connected.value) return;
    if (kind !== 'start' && !canChange.value) return;
    const currentOptions = options.value, choice = selectedIndex.value;
    if (kind === 'apply' && (!currentOptions || choice === null || optionsExpired.value)) return;
    if (kind === 'start' && snapshot.value?.trip.status !== 'SCHEDULED') return;
    busy.value = true; actionError.value = null; generation++; read?.abort(); clearTimeout(poll);
    const version = generation, id = toValue(tripId), controller = new AbortController(); action = controller;
    try {
      if (kind === 'options') {
        const result = await fetchDriverRouteOptions(id, controller.signal);
        if (alive && version === generation && !controller.signal.aborted) {
          options.value = result; selectedIndex.value = result.options[0]?.optionIndex ?? null;
        }
      } else {
        const next = kind === 'start' ? await startDriverTrip(id, controller.signal)
          : await applyDriverRouteOption(id, currentOptions!.token, choice!, controller.signal);
        if (alive && version === generation && !controller.signal.aborted) { clearOptions(); accept(next); }
      }
    } catch (reason) {
      if (alive && version === generation && !controller.signal.aborted) {
        actionError.value = message(reason);
        if (kind === 'apply') clearOptions();
      }
    } finally {
      if (alive && version === generation) { busy.value = false; action = null; void refresh(); }
    }
  }
  return reactive({ snapshot, options, selectedIndex, loading, busy, now, connected, canChange, optionsExpired,
    error: computed(() => actionError.value ?? readError.value),
    selectedOption: computed(() => options.value?.options.find(o => o.optionIndex === selectedIndex.value) ?? null),
    start: () => mutate('start'), loadOptions: () => mutate('options'), apply: () => mutate('apply'),
    cancelOptions: clearOptions, retry: () => { attempt.value++; },
  });
}
