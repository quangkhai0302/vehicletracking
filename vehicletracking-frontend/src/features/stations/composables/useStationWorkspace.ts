import { computed, nextTick, onScopeDispose, reactive, ref, shallowRef } from 'vue';
import type L from 'leaflet';
import { createStation, deleteStation, fetchStations, updateStation } from '@/features/stations/api/stations';
import { EMPTY_STATION_FORM, type Station, type StationFormMode, type StationFormState, type StationInput } from '@/features/stations/types/station';
import { useStationGeocoding } from '@/features/stations/composables/useStationGeocoding';

export function useStationWorkspace({ focusLocation, onPickStart, onPickEnd, onToast, onStationUpdated }: {
  focusLocation: (position: L.LatLngExpression, zoom?: number) => void;
  onPickStart: () => void; onPickEnd: () => void; onToast: (message: string) => void; onStationUpdated?: () => void;
}) {
  const stations = shallowRef<Station[]>([]), loadingStations = ref(true), savingStation = ref(false), deletingStation = ref(false);
  const stationError = ref<string | null>(null), selectedStationId = ref<number | null>(null);
  const formMode = ref<StationFormMode>('closed'), editingStation = shallowRef<Station | null>(null);
  const stationForm = ref<StationFormState>({ ...EMPTY_STATION_FORM }), pickingLocation = ref(false), deleteCandidate = shallowRef<Station | null>(null);
  const geocoding = useStationGeocoding(stationForm, formMode);
  const selectedStation = computed(() => stations.value.find(station => station.id === selectedStationId.value) ?? null);
  let alive = true;
  onScopeDispose(() => { alive = false; });
  fetchStations().then(data => { if (alive) { stations.value = data; stationError.value = null; } })
    .catch((error: unknown) => { if (alive) stationError.value = error instanceof Error ? error.message : 'Không thể tải danh sách trạm'; })
    .finally(() => { if (alive) loadingStations.value = false; });
  const resetForm = () => { geocoding.resetAddressLookup(); editingStation.value = null; stationForm.value = { ...EMPTY_STATION_FORM }; };
  const handleBeginCreate = () => {
    selectedStationId.value = null; resetForm(); stationError.value = null;
    formMode.value = 'create'; pickingLocation.value = true; onPickStart();
  };
  const handleSelectStation = (station: Station) => {
    if (formMode.value !== 'closed') return;
    selectedStationId.value = station.id; stationError.value = null;
    focusLocation([station.latitude, station.longitude], 16);
  };
  const handleBeginEdit = (targetStation?: Station) => {
    const target = targetStation && typeof targetStation.id === 'number' ? targetStation : selectedStation.value;
    if (!target) return;
    geocoding.resetAddressLookup(Boolean(target.address?.trim()));
    selectedStationId.value = target.id; editingStation.value = target;
    stationForm.value = { name: target.name, address: target.address || '', latitude: String(target.latitude),
      longitude: String(target.longitude), checkinRadiusMeters: String(target.checkinRadiusMeters) };
    stationError.value = null; formMode.value = 'edit'; pickingLocation.value = false;
    focusLocation([target.latitude, target.longitude], 16);
  };
  const handleCloseStationDrawer = () => {
    const closingMode = formMode.value;
    formMode.value = 'closed'; resetForm(); pickingLocation.value = false; onPickEnd();
    if (closingMode !== 'edit') selectedStationId.value = null;
  };
  const handleSaveStation = async (input: StationInput) => {
    // Flush coordinate watchers before deciding whether automatic address lookup is pending.
    await nextTick();
    if (!alive || savingStation.value || geocoding.addressLookupLoading.value || formMode.value === 'closed') return;
    const mode = formMode.value, editing = editingStation.value;
    savingStation.value = true; stationError.value = null;
    try {
      const saved = mode === 'edit' && editing ? await updateStation(editing.id, input) : await createStation(input);
      if (!alive) return;
      stations.value = [...stations.value.filter(station => station.id !== saved.id), saved]; selectedStationId.value = saved.id;
      formMode.value = 'closed'; resetForm(); pickingLocation.value = false; onPickEnd();
      if (mode === 'edit') onStationUpdated?.();
      onToast(mode === 'create' ? `Đã tạo trạm “${saved.name}”.` : `Đã cập nhật trạm “${saved.name}”.`);
      focusLocation([saved.latitude, saved.longitude], 16);
    } catch (error) { if (alive) stationError.value = error instanceof Error ? error.message : 'Không thể lưu trạm'; }
    finally { if (alive) savingStation.value = false; }
  };
  const handleFieldChange = (field: keyof StationFormState, value: string) => {
    if (field === 'address') geocoding.markAddressEdited();
    stationForm.value = { ...stationForm.value, [field]: value };
    if ((field === 'latitude' || field === 'longitude') && value.trim()) pickingLocation.value = false;
  };
  const handleDeactivate = async () => {
    const candidate = deleteCandidate.value;
    if (!candidate || deletingStation.value) return;
    deletingStation.value = true; stationError.value = null;
    try {
      await deleteStation(candidate.id);
      if (!alive) return;
      stations.value = stations.value.filter(station => station.id !== candidate.id); selectedStationId.value = null; deleteCandidate.value = null;
      onToast(`Đã ngừng sử dụng trạm “${candidate.name}”.`);
    } catch (error) {
      if (alive) { deleteCandidate.value = null; stationError.value = error instanceof Error ? error.message : 'Không thể ngừng sử dụng trạm'; }
    } finally { if (alive) deletingStation.value = false; }
  };
  return reactive({ stations, loadingStations, savingStation, deletingStation, stationError, selectedStationId,
    formMode, editingStation, stationForm, pickingLocation, deleteCandidate, selectedStation,
    ...geocoding,
    setStationForm: (value: StationFormState | ((current: StationFormState) => StationFormState)) => { stationForm.value = typeof value === 'function' ? value(stationForm.value) : value; },
    setPickingLocation: (value: boolean) => { pickingLocation.value = value; },
    setSelectedStationId: (value: number | null) => { selectedStationId.value = value; },
    setStationError: (value: string | null) => { stationError.value = value; },
    setDeleteCandidate: (value: Station | null) => { deleteCandidate.value = value; },
    handleBeginCreate, handleSelectStation, handleBeginEdit, handleCloseStationDrawer, handleSaveStation, handleFieldChange, handleDeactivate });
}
