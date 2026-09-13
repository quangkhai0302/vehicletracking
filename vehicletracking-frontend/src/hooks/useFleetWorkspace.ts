import { useEffect, useRef, useState } from 'react';
import * as fleet from '../services/fleet';
import type { FleetVehicle, VehicleInput, TripSummary, TripDetail, TripInput, TripAction } from '../types/fleet';
import type { OperationsSnapshot } from '../types/operations';

function newerTrip(local: TripSummary, remote?: TripSummary): TripSummary {
  if (!remote) return local;
  if (local.status === 'COMPLETED' || local.status === 'CANCELLED') return local;
  if (local.status === 'IN_PROGRESS' && remote.status === 'SCHEDULED') return local;
  return remote;
}

export type FleetScreen = { kind: 'list' } | { kind: 'vehicle-form'; vehicle: FleetVehicle | null }
  | { kind: 'trip-form'; vehicleId: number | null } | { kind: 'trip-detail'; id: number };

export function useFleetWorkspace(onToast: (message: string) => void, liveSnapshot?: OperationsSnapshot | null) {
  const [vehicles, setVehicles] = useState<FleetVehicle[]>([]);
  const [trips, setTrips] = useState<TripSummary[]>([]);
  const [tab, setTab] = useState<'vehicles' | 'trips'>('vehicles');
  const [vehicleFilter, setVehicleFilter] = useState<number | null>(null);
  const [screen, setScreen] = useState<FleetScreen>({ kind: 'list' });
  const [detail, setDetail] = useState<TripDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(false);
  const busyRef = useRef(false);
  const listAbort = useRef<AbortController | null>(null);
  const detailAbort = useRef<AbortController | null>(null);
  const detailId = useRef(0);
  const [loadAttempt, setLoadAttempt] = useState(0);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; listAbort.current?.abort(); detailAbort.current?.abort(); };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    listAbort.current = controller;
    Promise.all([fleet.fetchFleetVehicles(controller.signal), fleet.fetchTrips(controller.signal)])
    .then(([vehiclesData, tripsData]) => {
      if (mounted.current && !controller.signal.aborted) { setVehicles(vehiclesData); setTrips(tripsData); }
    }).catch((err: unknown) => {
      if (mounted.current && !controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu xe và chuyến.');
    }).finally(() => { if (mounted.current && !controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [loadAttempt]);
  const reload = () => {
    if (busyRef.current) return;
    setLoading(true); setError(null);
    setLoadAttempt(value => value + 1);
  };

  const clearSelection = () => {
    detailId.current++; detailAbort.current?.abort(); setDetail(null); setLoadingDetail(false); setError(null);
  };
  const close = () => { if (busyRef.current) return; clearSelection(); setScreen({ kind: 'list' }); };
  const openVehicleForm = (vehicle: FleetVehicle | null) => {
    if (busyRef.current) return;
    clearSelection(); setScreen({ kind: 'vehicle-form', vehicle });
  };
  const openTripForm = () => {
    if (busyRef.current) return;
    clearSelection(); setScreen({ kind: 'trip-form', vehicleId: vehicleFilter });
  };
  const selectTrip = async (id: number) => {
    if (busyRef.current) return;
    clearSelection();
    const token = detailId.current;
    const controller = new AbortController(); detailAbort.current = controller;
    setScreen({ kind: 'trip-detail', id }); setLoadingDetail(true);
    try {
      const data = await fleet.fetchTrip(id, controller.signal);
      if (mounted.current && token === detailId.current && !controller.signal.aborted) {
        setDetail(data); setTrips(current => current.map(trip => trip.id === data.trip.id ? data.trip : trip));
      }
    } catch (err) {
      if (mounted.current && token === detailId.current && !controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không thể tải chuyến.');
    } finally { if (mounted.current && token === detailId.current && !controller.signal.aborted) setLoadingDetail(false); }
  };
  const mutate = async (operation: () => Promise<void>) => {
    if (busyRef.current) return false;
    busyRef.current = true; listAbort.current?.abort(); setLoading(false);
    setBusy(true); setError(null);
    try { await operation(); return true; }
    catch (err) { if (mounted.current) setError(err instanceof Error ? err.message : 'Thao tác chưa thành công.'); return false; }
    finally { busyRef.current = false; if (mounted.current) setBusy(false); }
  };
  const saveVehicle = (input: VehicleInput, id?: number) => mutate(async () => {
    const saved = id === undefined ? await fleet.createVehicle(input) : await fleet.updateVehicle(id, input);
    if (!mounted.current) return;
    setVehicles(current => [...current.filter(item => item.id !== saved.id), saved].sort((a,b) => a.plateNumber.localeCompare(b.plateNumber)));
    setScreen({ kind: 'list' }); setTab('vehicles'); onToast(id === undefined ? 'Đã thêm xe.' : 'Đã cập nhật xe.');
  });
  const removeVehicle = (vehicle: FleetVehicle) => mutate(async () => {
    await fleet.deactivateVehicle(vehicle.id);
    if (!mounted.current) return;
    setVehicles(current => current.map(item => item.id === vehicle.id ? { ...item, active: false } : item));
    onToast(`Đã ngừng sử dụng xe ${vehicle.plateNumber}.`);
  });
  const saveTrip = (input: TripInput) => mutate(async () => {
    const saved = await fleet.createTrip(input);
    if (!mounted.current) return;
    setTrips(current => [saved.trip, ...current.filter(item => item.id !== saved.trip.id)]);
    setDetail(saved); setTab('trips'); setVehicleFilter(null); setScreen({ kind: 'trip-detail', id: saved.trip.id });
    onToast('Đã tạo chuyến đi và lưu lịch trình.');
  });
  const transition = (action: TripAction) => {
    if (!detail) return Promise.resolve(false);
    const id = detail.trip.id;
    return mutate(async () => {
      const saved = await fleet.changeTripStatus(id, action);
      if (!mounted.current) return;
      setDetail(saved); setTrips(current => current.map(item => item.id === id ? saved.trip : item));
      onToast(action === 'start' ? 'Đã ghi nhận khởi hành.' : action === 'complete' ? 'Đã hoàn thành chuyến đi.' : 'Đã hủy chuyến đi.');
    });
  };
  const showVehicleTrips = (id: number) => { close(); setVehicleFilter(id); setTab('trips'); };

  const remoteTrips = liveSnapshot?.trips ?? [];
  const mergedTrips = trips.map(trip => newerTrip(trip, remoteTrips.find(item => item.id === trip.id)));
  mergedTrips.push(...remoteTrips.filter(trip => !trips.some(item => item.id === trip.id)));
  mergedTrips.sort((a,b) => Date.parse(b.scheduledDepartureAt) - Date.parse(a.scheduledDepartureAt) || b.id - a.id);
  const visibleDetail = detail ? { ...detail, trip: newerTrip(detail.trip, remoteTrips.find(item => item.id === detail.trip.id)) } : null;
  return { vehicles, trips: mergedTrips, tab, setTab, vehicleFilter, setVehicleFilter, screen, detail: visibleDetail, loading,
    loadingDetail, busy, error, reload, close, openVehicleForm, openTripForm, selectTrip,
    saveVehicle, removeVehicle, saveTrip, transition, showVehicleTrips };
}
