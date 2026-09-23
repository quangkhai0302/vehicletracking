import { computed, onScopeDispose, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import * as fleet from '@/features/fleet/api/fleet';
import type { Driver, DriverInput, FleetVehicle, VehicleInput, TripSummary, TripDetail, TripInput, TripAction } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';

function newerTrip(local: TripSummary, remote?: TripSummary): TripSummary {
  if (!remote) return local;
  if ((remote.attemptNumber ?? 1) !== (local.attemptNumber ?? 1)) return (remote.attemptNumber ?? 1) > (local.attemptNumber ?? 1) ? remote : local;
  if (local.status === 'COMPLETED' || local.status === 'CANCELLED') return local;
  if (local.status === 'IN_PROGRESS' && remote.status === 'SCHEDULED') return local;
  return remote;
}
export type FleetScreen = { kind: 'list' } | { kind: 'vehicle-form'; vehicle: FleetVehicle | null }
  | { kind: 'driver-form'; driver: Driver | null } | { kind: 'trip-form'; vehicleId: number | null } | { kind: 'trip-detail'; id: number };
export type FleetTab = 'vehicles' | 'drivers' | 'trips';
export interface FleetTripSelection { tripId: number | null }

export function useFleetWorkspace(onToast: (message: string) => void,
  liveSnapshot?: MaybeRefOrGetter<OperationsSnapshot | null>, tripSelection?: MaybeRefOrGetter<FleetTripSelection | null>,
  onTripCreated?: (detail: TripDetail) => void, initialTab: FleetTab = 'vehicles', initialVehicleFilter: number | null = null) {
  const vehicles = shallowRef<FleetVehicle[]>([]), drivers = shallowRef<Driver[]>([]), trips = shallowRef<TripSummary[]>([]);
  const tab = ref<FleetTab>(initialTab), vehicleFilter = ref<number | null>(initialVehicleFilter);
  const screen = shallowRef<FleetScreen>({ kind: 'list' }), detail = shallowRef<TripDetail | null>(null);
  const loading = ref(true), loadingDetail = ref(false), busy = ref(false), error = ref<string | null>(null), loadAttempt = ref(0);
  let alive = true, detailId = 0;
  let listAbort: AbortController | null = null, detailAbort: AbortController | null = null;
  onScopeDispose(() => { alive = false; listAbort?.abort(); detailAbort?.abort(); });
  watch(loadAttempt, (_, _old, cleanup) => {
    const controller = new AbortController(); listAbort = controller;
    Promise.all([fleet.fetchFleetVehicles(controller.signal), fleet.fetchDrivers(controller.signal), fleet.fetchTrips(controller.signal)])
      .then(([v, d, t]) => { if (alive && !controller.signal.aborted) { vehicles.value = v; drivers.value = d; trips.value = t; } })
      .catch(err => { if (alive && !controller.signal.aborted) error.value = err instanceof Error ? err.message : 'Không thể tải dữ liệu đội xe.'; })
      .finally(() => { if (alive && !controller.signal.aborted) loading.value = false; });
    cleanup(() => controller.abort());
  }, { immediate: true });
  function reload() { if (!busy.value) { loading.value = true; error.value = null; loadAttempt.value++; } }
  function clearSelection() { detailId++; detailAbort?.abort(); detail.value = null; loadingDetail.value = false; error.value = null; }
  function close() { if (!busy.value) { clearSelection(); screen.value = { kind: 'list' }; } }
  function openVehicleForm(vehicle: FleetVehicle | null) { if (!busy.value) { clearSelection(); screen.value = { kind: 'vehicle-form', vehicle }; } }
  function openDriverForm(driver: Driver | null) { if (!busy.value) { clearSelection(); screen.value = { kind: 'driver-form', driver }; } }
  function openTripForm() { if (!busy.value) { clearSelection(); screen.value = { kind: 'trip-form', vehicleId: vehicleFilter.value }; } }
  async function selectTrip(id: number) {
    if (busy.value) return;
    clearSelection(); const token = detailId;
    const controller = new AbortController(); detailAbort = controller;
    screen.value = { kind: 'trip-detail', id }; loadingDetail.value = true;
    try {
      const data = await fleet.fetchTrip(id, controller.signal);
      if (alive && token === detailId && !controller.signal.aborted) { detail.value = data; trips.value = trips.value.map(trip => trip.id === data.trip.id ? data.trip : trip); }
    } catch (err) { if (alive && token === detailId && !controller.signal.aborted) error.value = err instanceof Error ? err.message : 'Không thể tải chuyến.'; }
    finally { if (alive && token === detailId && !controller.signal.aborted) loadingDetail.value = false; }
  }
  let appliedSelection: FleetTripSelection | null | undefined;
  watch([() => toValue(tripSelection), busy], ([selection, isBusy]) => {
    if (!selection || isBusy || selection === appliedSelection) return;
    appliedSelection = selection; tab.value = 'trips'; vehicleFilter.value = null;
    if (selection.tripId === null) { clearSelection(); screen.value = { kind: 'list' }; }
    else void selectTrip(selection.tripId);
  }, { immediate: true });
  const remoteTrips = computed(() => toValue(liveSnapshot)?.trips ?? []);
  const replayNumber = computed(() => {
    const current = screen.value;
    return current.kind === 'trip-detail' ? remoteTrips.value.find(trip => trip.id === current.id)?.attemptNumber ?? 1 : 1;
  });
  watch([replayNumber, detail, busy], ([replay, loaded, isBusy]) => {
    if (!isBusy && loaded && replay > (loaded.trip.attemptNumber ?? 1)) void selectTrip(loaded.trip.id);
  });
  async function mutate(operation: () => Promise<void>) {
    if (busy.value || !alive) return false;
    busy.value = true; listAbort?.abort(); loading.value = false; error.value = null;
    try { await operation(); return true; }
    catch (err) { if (alive) error.value = err instanceof Error ? err.message : 'Thao tác chưa thành công.'; return false; }
    finally { if (alive) busy.value = false; }
  }
  const saveVehicle = (input: VehicleInput, id?: number) => mutate(async () => {
    let saved = id === undefined ? await fleet.createVehicle(input) : await fleet.updateVehicle(id, input);
    if (input.driverId !== (saved.driver?.id ?? null)) {
      try {
        if (input.driverId === null) { await fleet.unassignVehicleDriver(saved.id); saved = { ...saved, driver: null }; }
        else saved = await fleet.assignVehicleDriver(saved.id, input.driverId);
      } catch (assignmentError) {
        try { const refreshed = await fleet.fetchFleetVehicles(); if (alive) vehicles.value = refreshed; } catch { /* Preserve assignment error. */ }
        throw assignmentError;
      }
    }
    if (!alive) return;
    vehicles.value = [...vehicles.value.filter(item => item.id !== saved.id), saved].sort((a, b) => a.plateNumber.localeCompare(b.plateNumber));
    screen.value = { kind: 'list' }; tab.value = 'vehicles'; onToast(id === undefined ? 'Đã thêm xe.' : 'Đã cập nhật xe.');
  });
  const removeVehicle = (vehicle: FleetVehicle) => mutate(async () => {
    await fleet.deactivateVehicle(vehicle.id); if (!alive) return;
    vehicles.value = vehicles.value.map(item => item.id === vehicle.id ? { ...item, active: false, driver: null } : item);
    onToast(`Đã ngừng sử dụng xe ${vehicle.plateNumber}.`);
  });
  const saveDriver = (input: DriverInput, id?: number) => mutate(async () => {
    const saved = id === undefined ? await fleet.createDriver(input) : await fleet.updateDriver(id, input); if (!alive) return;
    drivers.value = [...drivers.value.filter(item => item.id !== saved.id), saved].sort((a, b) => a.licenseNumber.localeCompare(b.licenseNumber));
    vehicles.value = vehicles.value.map(vehicle => vehicle.driver?.id === saved.id ? { ...vehicle, driver: { id: saved.id, fullName: saved.fullName, phoneNumber: saved.phoneNumber, licenseNumber: saved.licenseNumber } } : vehicle);
    screen.value = { kind: 'list' }; tab.value = 'drivers'; onToast(id === undefined ? 'Đã thêm tài xế.' : 'Đã cập nhật tài xế.');
  });
  const removeDriver = (driver: Driver) => mutate(async () => {
    await fleet.deactivateDriver(driver.id); if (!alive) return;
    drivers.value = drivers.value.map(item => item.id === driver.id ? { ...item, active: false } : item);
    onToast(`Đã ngừng sử dụng tài xế ${driver.fullName}.`);
  });
  const saveTrip = (input: TripInput) => mutate(async () => {
    const saved = await fleet.createTrip(input); if (!alive) return;
    trips.value = [saved.trip, ...trips.value.filter(item => item.id !== saved.trip.id)]; detail.value = saved;
    tab.value = 'trips'; vehicleFilter.value = null; screen.value = { kind: 'trip-detail', id: saved.trip.id };
    onTripCreated?.(saved); onToast('Đã tạo chuyến đi và lưu lịch trình.');
  });
  function transition(action: TripAction, reason?: string) {
    if (!detail.value) return Promise.resolve(false);
    const id = detail.value.trip.id;
    return mutate(async () => {
      const saved = await fleet.changeTripStatus(id, action, reason); if (!alive) return;
      detail.value = saved; trips.value = trips.value.map(item => item.id === id ? saved.trip : item);
      onToast(action === 'start' ? 'Đã ghi nhận khởi hành.' : action === 'complete' ? 'Đã hoàn thành chuyến đi.' : 'Đã hủy chuyến đi.');
    });
  }
  const updateTripSchedule = (id: number, input: { scheduledDepartureAt: string }) => mutate(async () => {
    const saved = await fleet.updateTrip(id, input); if (!alive) return;
    detail.value = saved; trips.value = trips.value.map(item => item.id === id ? saved.trip : item);
    onToast('Đã cập nhật giờ xuất phát và lịch các điểm dừng.');
  });
  const removeTrip = (id: number) => mutate(async () => {
    await fleet.deleteTrip(id); if (!alive) return;
    trips.value = trips.value.filter(item => item.id !== id); detail.value = null; screen.value = { kind: 'list' };
    onToast('Đã xóa chuyến đi chưa khởi hành.');
  });
  const updateTripDriver = (id: number, driverId: number | null) => mutate(async () => {
    if (driverId === null) {
      await fleet.unassignTripDriver(id); if (!alive) return;
      if (detail.value?.trip.id === id) detail.value = { ...detail.value, trip: { ...detail.value.trip, driver: null } };
      trips.value = trips.value.map(item => item.id === id ? { ...item, driver: null } : item);
    } else {
      const saved = await fleet.assignTripDriver(id, driverId); if (!alive) return;
      detail.value = saved; trips.value = trips.value.map(item => item.id === id ? saved.trip : item);
    }
    onToast(driverId === null ? 'Đã bỏ gán tài xế khỏi chuyến.' : 'Đã cập nhật tài xế của chuyến.');
  });
  function showVehicleTrips(id: number) { close(); vehicleFilter.value = id; tab.value = 'trips'; }
  const mergedTrips = computed(() => {
    const merged = trips.value.map(trip => newerTrip(trip, remoteTrips.value.find(item => item.id === trip.id)));
    merged.push(...remoteTrips.value.filter(trip => !trips.value.some(item => item.id === trip.id)));
    return merged.sort((a, b) => Date.parse(b.scheduledDepartureAt) - Date.parse(a.scheduledDepartureAt) || b.id - a.id);
  });
  const visibleDetail = computed(() => detail.value && replayNumber.value <= (detail.value.trip.attemptNumber ?? 1)
    ? { ...detail.value, trip: newerTrip(detail.value.trip, remoteTrips.value.find(item => item.id === detail.value!.trip.id)) } : null);
  return reactive({ vehicles, drivers, trips: mergedTrips, tab, setTab: (value: FleetTab) => { tab.value = value; }, vehicleFilter,
    setVehicleFilter: (value: number | null) => { vehicleFilter.value = value; }, screen, detail: visibleDetail, loading, loadingDetail, busy, error,
    reload, close, openVehicleForm, openDriverForm, openTripForm, selectTrip, saveVehicle, removeVehicle, saveDriver, removeDriver, saveTrip,
    transition, updateTripSchedule, updateTripDriver, removeTrip, showVehicleTrips });
}
