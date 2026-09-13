import { useEffect, useMemo, useState } from 'react';
import type L from 'leaflet';
import { createStation, deleteStation, fetchStations, updateStation } from '../services/stations';
import { EMPTY_STATION_FORM, type Station, type StationFormMode, type StationFormState, type StationInput } from '../types/station';

export function useStationWorkspace({ focusLocation, onPickStart, onPickEnd, onToast }: {
  focusLocation: (position: L.LatLngExpression, zoom?: number) => void;
  onPickStart: () => void; onPickEnd: () => void; onToast: (message: string) => void;
}) {
  const [stations, setStations] = useState<Station[]>([]);
  const [loadingStations, setLoadingStations] = useState(true);
  const [savingStation, setSavingStation] = useState(false);
  const [deletingStation, setDeletingStation] = useState(false);
  const [stationError, setStationError] = useState<string | null>(null);
  const [selectedStationId, setSelectedStationId] = useState<number | null>(null);
  const [formMode, setFormMode] = useState<StationFormMode>('closed');
  const [editingStation, setEditingStation] = useState<Station | null>(null);
  const [stationForm, setStationForm] = useState<StationFormState>(EMPTY_STATION_FORM);
  const [pickingLocation, setPickingLocation] = useState(false);
  const [deleteCandidate, setDeleteCandidate] = useState<Station | null>(null);

  const selectedStation = useMemo(
    () => stations.find((station) => station.id === selectedStationId) ?? null,
    [selectedStationId, stations]
  );


  // Fetch Stations from backend database
  useEffect(() => {
    let active = true;
    fetchStations()
      .then((data) => {
        if (!active) return;
        setStations(data);
        setStationError(null);
      })
      .catch((error: unknown) => {
        if (active) setStationError(error instanceof Error ? error.message : 'Không thể tải danh sách trạm');
      })
      .finally(() => {
        if (active) setLoadingStations(false);
      });
    return () => {
      active = false;
    };
  }, []);


  // Actions for Stations
  const handleBeginCreate = () => {
    setSelectedStationId(null);
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
    setStationError(null);
    setFormMode('create');
    setPickingLocation(true);
    onPickStart();
  };

  const handleSelectStation = (station: Station) => {
    if (formMode !== 'closed') return;
    setSelectedStationId(station.id);
    setStationError(null);
    focusLocation([station.latitude, station.longitude], 16);
  };

  const handleBeginEdit = (targetStation?: Station) => {
    const target = targetStation && typeof targetStation.id === 'number' ? targetStation : selectedStation;
    if (!target) return;
    setSelectedStationId(target.id);
    setEditingStation(target);
    setStationForm({
      name: target.name,
      address: target.address || '',
      latitude: String(target.latitude),
      longitude: String(target.longitude),
      checkinRadiusMeters: String(target.checkinRadiusMeters),
    });
    setStationError(null);
    setFormMode('edit');
    setPickingLocation(false);
    focusLocation([target.latitude, target.longitude], 16);
  };

  const handleCloseStationDrawer = () => {
    const closingMode = formMode;
    setFormMode('closed');
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
    setPickingLocation(false);
    onPickEnd();
    if (closingMode !== 'edit') setSelectedStationId(null);
  };

  const handleSaveStation = async (input: StationInput) => {
    setSavingStation(true);
    setStationError(null);
    try {
      const saved =
        formMode === 'edit' && editingStation
          ? await updateStation(editingStation.id, input)
          : await createStation(input);
      setStations((current) => [...current.filter((station) => station.id !== saved.id), saved]);
      setSelectedStationId(saved.id);
      setFormMode('closed');
      setEditingStation(null);
      setStationForm(EMPTY_STATION_FORM);
      setPickingLocation(false);
      onPickEnd();
      onToast(formMode === 'create' ? `Đã tạo trạm “${saved.name}”.` : `Đã cập nhật trạm “${saved.name}”.`);
      focusLocation([saved.latitude, saved.longitude], 16);
    } catch (error) {
      setStationError(error instanceof Error ? error.message : 'Không thể lưu trạm');
    } finally {
      setSavingStation(false);
    }
  };

  const handleFieldChange = (field: keyof StationFormState, value: string) => {
    setStationForm((current) => ({ ...current, [field]: value }));
    if ((field === 'latitude' || field === 'longitude') && value.trim()) {
      setPickingLocation(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deleteCandidate) return;
    setDeletingStation(true);
    setStationError(null);
    try {
      await deleteStation(deleteCandidate.id);
      setStations((current) => current.filter((station) => station.id !== deleteCandidate.id));
      setSelectedStationId(null);
      setDeleteCandidate(null);
      onToast(`Đã ngừng sử dụng trạm “${deleteCandidate.name}”.`);
    } catch (error) {
      setDeleteCandidate(null);
      setStationError(error instanceof Error ? error.message : 'Không thể ngừng sử dụng trạm');
    } finally {
      setDeletingStation(false);
    }
  };


  return { stations, loadingStations, savingStation, deletingStation, stationError, selectedStationId,
    formMode, editingStation, stationForm, pickingLocation, deleteCandidate, selectedStation,
    setStationForm, setPickingLocation, setSelectedStationId, setStationError, setDeleteCandidate,
    handleBeginCreate, handleSelectStation, handleBeginEdit, handleCloseStationDrawer,
    handleSaveStation, handleFieldChange, handleDeactivate };
}
