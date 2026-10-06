import {
  computed,
  onScopeDispose,
  reactive,
  ref,
  shallowRef,
  toValue,
  watch,
  type MaybeRefOrGetter,
} from 'vue';
import { fetchTrip } from '@/features/fleet/api/fleet';
import { controlSimulation, setSimulationScenario } from '@/features/tracking/api/operations';
import type { TripDetail } from '@/features/fleet/types/fleet';
import type {
  OperationsSnapshot,
  SimulationAction,
  SimulationRun,
  SimulationScenario,
} from '@/features/tracking/types/operations';
import { SIMULATION_SCENARIOS } from '../utils/scenarios';

export function useSimulator(
  snapshot: MaybeRefOrGetter<OperationsSnapshot | null>,
  onToast: (message: string) => void,
) {
  const tripId = ref<number | null>(null),
    loadedDetail = shallowRef<TripDetail | null>(null);
  const localRun = shallowRef<SimulationRun | null>(null);
  const loading = ref(false),
    busy = ref(false),
    error = ref<string | null>(null),
    attempt = ref(0);
  let alive = true;
  onScopeDispose(() => {
    alive = false;
  });
  const remoteTrip = computed(() =>
    toValue(snapshot)?.trips.find((item) => item.id === tripId.value),
  );
  const replayNumber = computed(() =>
    Math.max(
      remoteTrip.value?.attemptNumber ?? 1,
      localRun.value?.tripId === tripId.value ? (localRun.value?.attemptNumber ?? 1) : 1,
    ),
  );
  watch(
    [tripId, attempt, replayNumber],
    ([id], _previous, cleanup) => {
      if (id === null) return;
      const controller = new AbortController();
      fetchTrip(id, controller.signal)
        .then((data) => {
          if (!controller.signal.aborted) {
            loadedDetail.value = data;
            error.value = null;
          }
        })
        .catch((reason: unknown) => {
          if (!controller.signal.aborted)
            error.value = reason instanceof Error ? reason.message : 'Không tải được tuyến chuyến.';
        })
        .finally(() => {
          if (!controller.signal.aborted) loading.value = false;
        });
      cleanup(() => controller.abort());
    },
    { immediate: true },
  );
  const select = (id: number | null) => {
    if (busy.value) return;
    tripId.value = id;
    loadedDetail.value = null;
    localRun.value = null;
    error.value = null;
    loading.value = id !== null;
    attempt.value++;
  };
  const retry = () => {
    if (!busy.value) {
      loading.value = true;
      error.value = null;
      attempt.value++;
    }
  };
  const run = computed(() => {
    const remote =
      toValue(snapshot)?.simulations.find((item) => item.tripId === tripId.value) ?? null;
    const local = localRun.value;
    return local?.tripId === tripId.value &&
      (!remote ||
        (local.attemptNumber ?? 1) > (remote.attemptNumber ?? 1) ||
        ((local.attemptNumber ?? 1) === (remote.attemptNumber ?? 1) &&
          Date.parse(local.updatedAt) > Date.parse(remote.updatedAt)))
      ? local
      : remote;
  });
  const detail = computed(() =>
    loadedDetail.value?.trip.id === tripId.value &&
    (loadedDetail.value.trip.attemptNumber ?? 1) === replayNumber.value
      ? loadedDetail.value
      : null,
  );
  const trip = computed(() =>
    remoteTrip.value && (remoteTrip.value.attemptNumber ?? 1) === replayNumber.value
      ? remoteTrip.value
      : (detail.value?.trip ?? null),
  );
  const command = async (action: SimulationAction, multiplier?: 1 | 5 | 10) => {
    const id = tripId.value;
    if (!id || busy.value) return false;
    busy.value = true;
    error.value = null;
    try {
      const response = await controlSimulation(id, action, multiplier);
      if (!alive) return true;
      localRun.value = response;
      if (response.tripId !== id) {
        tripId.value = response.tripId;
        loadedDetail.value = null;
        loading.value = true;
      }
      if (action === 'reset') {
        loadedDetail.value = null;
        loading.value = true;
        attempt.value++;
      }
      onToast(
        action === 'reset'
          ? `Đã chuẩn bị lượt mới cho chuyến #${response.tripId}. Bấm Bắt đầu để chạy; lịch sử cũ được giữ.`
          : action === 'stop'
            ? 'Đã dừng mô phỏng và hủy chuyến.'
            : action === 'pause'
              ? 'Đã tạm dừng mô phỏng.'
              : action === 'play'
                ? 'Đang mô phỏng theo tuyến đã lưu.'
                : `Tốc độ phát ${multiplier}×.`,
      );
      return true;
    } catch (reason) {
      if (alive)
        error.value = reason instanceof Error ? reason.message : 'Điều khiển chưa thành công.';
      return false;
    } finally {
      if (alive) busy.value = false;
    }
  };
  const setScenario = async (scenario: SimulationScenario) => {
    const id = tripId.value,
      current = run.value;
    if (
      !id ||
      !current ||
      busy.value ||
      (current.status !== 'RUNNING' && current.status !== 'PAUSED')
    )
      return false;
    const number = current.attemptNumber ?? replayNumber.value;
    busy.value = true;
    error.value = null;
    try {
      const response = await setSimulationScenario(id, scenario, number);
      if (!alive) return true;
      if (tripId.value !== id || replayNumber.value !== number) return true;
      localRun.value = response;
      onToast(`Đã chọn tình huống: ${SIMULATION_SCENARIOS[scenario].label}.`);
      return true;
    } catch (reason) {
      if (alive)
        error.value =
          reason instanceof Error ? reason.message : 'Không đổi được tình huống mô phỏng.';
      return false;
    } finally {
      if (alive) busy.value = false;
    }
  };
  return reactive({
    tripId,
    trip,
    detail,
    run,
    loading,
    busy,
    error,
    select,
    retry,
    command,
    setScenario,
  });
}
