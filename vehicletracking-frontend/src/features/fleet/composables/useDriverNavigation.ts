import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { applyDriverRouteOption, confirmDriverBoardingCount, fetchDriverNavigation, fetchDriverRouteOptions, startDriverTrip, pauseDriverTrip, resumeDriverTrip, resolveDriverIncident } from '@/features/fleet/api/driverPortal';
import type { DriverNavigationSnapshot, DriverRouteOptions } from '@/features/fleet/types/driverNavigation';
import { reportDriverSimulationIncident, type SimulationIncidentType } from '@/features/simulation/api/incidents';
import type { NotificationSeverity } from '@/features/reports/types/notifications';

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
  const activeIncidents = computed(() => snapshot.value?.activeIncidents ?? []);
  const canPause = computed(() => connected.value && !busy.value && snapshot.value?.trip.status === 'IN_PROGRESS' && snapshot.value.simulation?.status === 'RUNNING');
  const canResume = computed(() => connected.value && !busy.value && snapshot.value?.trip.status === 'IN_PROGRESS' && snapshot.value.simulation?.status === 'PAUSED' && activeIncidents.value.length === 0);
  const optionsExpired = computed(() => !options.value || now.value >= Date.parse(options.value.expiresAt));

  async function mutate(kind: 'start' | 'options' | 'apply' | 'pause' | 'resume') {
    if (!alive || busy.value || !connected.value) return;
    if ((kind === 'options' || kind === 'apply') && !canChange.value) return;
    if (kind === 'pause' && !canPause.value) return;
    if (kind === 'resume' && !canResume.value) return;
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
        const next = kind === 'pause' ? await pauseDriverTrip(id, controller.signal)
          : kind === 'resume' ? await resumeDriverTrip(id, controller.signal)
          : kind === 'start' ? await startDriverTrip(id, controller.signal)
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
  async function confirmBoarding(sequence: number, count: number) {
    if (!alive || busy.value || !connected.value) return false;
    busy.value = true; actionError.value = null;
    const id = toValue(tripId), controller = new AbortController(); action = controller;
    try {
      await confirmDriverBoardingCount(id, sequence, count, controller.signal);
      if (alive && snapshot.value?.trip.id === id) void refresh();
      return true;
    } catch (reason) {
      if (alive) actionError.value = message(reason);
      return false;
    } finally {
      if (alive) { busy.value = false; action = null; }
    }
  }
  async function reportIncident(input: {
    type: SimulationIncidentType;
    severity: NotificationSeverity;
    detail: string;
    idempotencyKey: string;
  }) {
    const current = snapshot.value, id = toValue(tripId);
    const run = current?.simulation;
    if (!alive || !current || current.trip.status !== 'IN_PROGRESS' || !run ||
      (run.status !== 'RUNNING' && run.status !== 'PAUSED') || busy.value || !connected.value)
      return false;
    busy.value = true; actionError.value = null; generation++; read?.abort(); clearTimeout(poll);
    const version = generation, controller = new AbortController(); action = controller;
    try {
      const result = await reportDriverSimulationIncident(id, {
        ...input,
        attemptNumber: run.attemptNumber ?? current.trip.attemptNumber ?? 1,
      }, controller.signal);
      if (!alive || version !== generation || controller.signal.aborted) return true;
      if (result.attemptNumber === current.trip.attemptNumber)
        snapshot.value = { ...current, simulation: result.simulation ?? current.simulation,
          activeIncidents: [...(current.activeIncidents ?? []).filter(item => item.id !== result.id), result] };
      return true;
    } catch (reason) {
      if (alive && version === generation && !controller.signal.aborted) actionError.value = message(reason);
      return false;
    } finally {
      if (alive && version === generation) { busy.value = false; action = null; void refresh(); }
    }
  }
  async function resolveIncident(incidentId: number, resolutionNote: string) {
    const current = snapshot.value;
    if (!alive || !current || current.trip.status !== 'IN_PROGRESS' || !connected.value || busy.value ||
      !activeIncidents.value.some(item => item.id === incidentId) || resolutionNote.length > 500) return false;
    busy.value = true; actionError.value = null; generation++; read?.abort(); clearTimeout(poll);
    const version = generation, controller = new AbortController(); action = controller;
    try {
      const next = await resolveDriverIncident(toValue(tripId), incidentId, current.simulation?.attemptNumber ?? current.trip.attemptNumber ?? 1, resolutionNote.trim(), controller.signal);
      if (alive && version === generation && !controller.signal.aborted) { clearOptions(); accept(next); }
      return true;
    } catch (reason) {
      if (alive && version === generation && !controller.signal.aborted) actionError.value = message(reason);
      return false;
    } finally {
      if (alive && version === generation) { busy.value = false; action = null; void refresh(); }
    }
  }
  return reactive({ snapshot, options, selectedIndex, loading, busy, now, connected, canChange, canPause, canResume, activeIncidents, optionsExpired,
    error: computed(() => actionError.value ?? readError.value),
    selectedOption: computed(() => options.value?.options.find(o => o.optionIndex === selectedIndex.value) ?? null),
    pause: () => mutate('pause'), resume: () => mutate('resume'), resolveIncident,
    start: () => mutate('start'), loadOptions: () => mutate('options'), apply: () => mutate('apply'),
    cancelOptions: clearOptions, retry: () => { attempt.value++; },
    confirmBoarding, reportIncident,
  });
}
