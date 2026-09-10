import { useEffect, useRef, useState, type FC } from 'react';
import L from 'leaflet';
import { MapTheme } from '../types/map';
import {
  EMPTY_STATION_FORM,
  type Station,
  type StationFormMode,
  type StationFormState,
  type StationInput,
} from '../types/station';
import { createStation, deleteStation, fetchStations, updateStation } from '../services/stations';
import { MapControls } from './MapControls';
import { StationPanel } from './StationPanel';

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

function sortStations(stations: Station[]): Station[] {
  return [...stations].sort((first, second) =>
    first.name.localeCompare(second.name, 'vi') || first.id - second.id
  );
}

export const MapComponent: FC = () => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const stationLayerRef = useRef<L.LayerGroup | null>(null);
  const stationMarkerRef = useRef<Map<number, L.CircleMarker>>(new Map());
  const coordRef = useRef<HTMLSpanElement>(null);

  const [theme, setTheme] = useState<MapTheme>('google-roadmap');
  const [stations, setStations] = useState<Station[]>([]);
  const [loadingStations, setLoadingStations] = useState(true);
  const [savingStation, setSavingStation] = useState(false);
  const [stationError, setStationError] = useState<string | null>(null);
  const [formMode, setFormMode] = useState<StationFormMode>('closed');
  const [editingStation, setEditingStation] = useState<Station | null>(null);
  const [stationForm, setStationForm] = useState<StationFormState>(EMPTY_STATION_FORM);

  useEffect(() => {
    let active = true;
    fetchStations()
      .then((data) => {
        if (active) {
          setStations(sortStations(data));
          setStationError(null);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setStationError(error instanceof Error ? error.message : 'Không thể tải danh sách trạm');
        }
      })
      .finally(() => {
        if (active) setLoadingStations(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: HCMC_CENTER,
      zoom: 13,
      zoomControl: false,
      preferCanvas: true,
      zoomSnap: 1,
      zoomDelta: 1,
      wheelPxPerZoomLevel: 120,
      inertia: true,
      inertiaDeceleration: 3400,
      inertiaMaxSpeed: 2000,
    });

    L.control.zoom({ position: 'topright' }).addTo(map);
    stationLayerRef.current = L.layerGroup().addTo(map);

    let rafId: number | null = null;
    let pendingCoords: { lat: number; lng: number } | null = null;

    const onMouseMove = (event: L.LeafletMouseEvent) => {
      pendingCoords = { lat: event.latlng.lat, lng: event.latlng.lng };
      if (!rafId) {
        rafId = requestAnimationFrame(() => {
          if (coordRef.current && pendingCoords) {
            coordRef.current.textContent = `Tọa độ: ${pendingCoords.lat.toFixed(5)}, ${pendingCoords.lng.toFixed(5)}`;
          }
          rafId = null;
        });
      }
    };

    const onMouseOut = () => {
      if (rafId) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      if (coordRef.current) {
        coordRef.current.textContent = 'Di chuột trên bản đồ để xem tọa độ';
      }
    };

    map.on('mousemove', onMouseMove);
    map.on('mouseout', onMouseOut);
    mapInstanceRef.current = map;
    const stationMarkers = stationMarkerRef.current;

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      map.off('mousemove', onMouseMove);
      map.off('mouseout', onMouseOut);
      map.remove();
      mapInstanceRef.current = null;
      stationLayerRef.current = null;
      stationMarkers.clear();
    };
  }, []);

  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const mapContainer = mapContainerRef.current;

    const onMapClick = (event: L.LeafletMouseEvent) => {
      setStationForm((current) => ({
        ...current,
        latitude: event.latlng.lat.toFixed(6),
        longitude: event.latlng.lng.toFixed(6),
      }));
    };

    if (formMode !== 'closed') {
      map.on('click', onMapClick);
      mapContainer?.classList.add('station-picking');
    }

    return () => {
      map.off('click', onMapClick);
      mapContainer?.classList.remove('station-picking');
    };
  }, [formMode]);

  useEffect(() => {
    const layer = stationLayerRef.current;
    if (!layer) return;

    layer.clearLayers();
    stationMarkerRef.current.clear();

    stations.forEach((station) => {
      const position: L.LatLngExpression = [station.latitude, station.longitude];
      const radius = L.circle(position, {
        radius: station.checkinRadiusMeters,
        color: '#0284c7',
        weight: 1,
        opacity: 0.55,
        fillColor: '#00f0ff',
        fillOpacity: 0.08,
        interactive: false,
      });
      const marker = L.circleMarker(position, {
        radius: 8,
        color: '#042f4a',
        weight: 3,
        fillColor: '#00f0ff',
        fillOpacity: 1,
      });

      const popup = document.createElement('div');
      const title = document.createElement('div');
      title.className = 'traffic-popup-title';
      title.textContent = station.name;
      popup.appendChild(title);

      const address = document.createElement('div');
      address.className = 'station-popup-detail';
      address.textContent = station.address || 'Chưa có địa chỉ';
      popup.appendChild(address);

      const checkinRadius = document.createElement('div');
      checkinRadius.className = 'station-popup-detail';
      checkinRadius.textContent = `Bán kính check-in: ${station.checkinRadiusMeters} m`;
      popup.appendChild(checkinRadius);

      marker.bindPopup(popup);
      radius.addTo(layer);
      marker.addTo(layer);
      stationMarkerRef.current.set(station.id, marker);
    });
  }, [stations]);

  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
      tileLayerRef.current = null;
    }

    const layerType = theme === 'google-satellite' ? 'y' : 'm';
    const tileUrl = `https://{s}.google.com/vt/lyrs=${layerType}&hl=vi&gl=VN&x={x}&y={y}&z={z}`;
    const newTileLayer = L.tileLayer(tileUrl, {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
      className: theme === 'google-dark' ? 'dark-map-tiles' : '',
      attribution: '&copy; Google Maps',
      updateWhenZooming: true,
      updateWhenIdle: false,
      keepBuffer: 6,
    });

    newTileLayer.addTo(map);
    newTileLayer.bringToBack();
    tileLayerRef.current = newTileLayer;
  }, [theme]);

  const handleResetCenter = () => {
    mapInstanceRef.current?.setView(HCMC_CENTER, 13, { animate: true });
  };

  const handleBeginCreate = () => {
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
    setStationError(null);
    setFormMode('create');
  };

  const handleBeginEdit = (station: Station) => {
    setEditingStation(station);
    setStationForm({
      name: station.name,
      address: station.address || '',
      latitude: String(station.latitude),
      longitude: String(station.longitude),
      checkinRadiusMeters: String(station.checkinRadiusMeters),
    });
    setStationError(null);
    setFormMode('edit');
    mapInstanceRef.current?.setView([station.latitude, station.longitude], 16, { animate: true });
  };

  const handleCancel = () => {
    setFormMode('closed');
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
  };

  const handleSave = async (input: StationInput) => {
    setSavingStation(true);
    setStationError(null);
    try {
      const saved = formMode === 'edit' && editingStation
        ? await updateStation(editingStation.id, input)
        : await createStation(input);
      setStations((current) => sortStations([
        ...current.filter((station) => station.id !== saved.id),
        saved,
      ]));
      setFormMode('closed');
      setEditingStation(null);
      setStationForm(EMPTY_STATION_FORM);
      mapInstanceRef.current?.setView([saved.latitude, saved.longitude], 16, { animate: true });
    } catch (error) {
      setStationError(error instanceof Error ? error.message : 'Không thể lưu trạm');
    } finally {
      setSavingStation(false);
    }
  };

  const handleDelete = async (station: Station) => {
    if (!window.confirm(`Xóa trạm “${station.name}”?`)) return;

    setStationError(null);
    try {
      await deleteStation(station.id);
      setStations((current) => current.filter((item) => item.id !== station.id));
      if (editingStation?.id === station.id) handleCancel();
    } catch (error) {
      setStationError(error instanceof Error ? error.message : 'Không thể xóa trạm');
    }
  };

  const handleFocus = (station: Station) => {
    mapInstanceRef.current?.setView([station.latitude, station.longitude], 16, { animate: true });
    stationMarkerRef.current.get(station.id)?.openPopup();
  };

  const handleFieldChange = (field: keyof StationFormState, value: string) => {
    setStationForm((current) => ({ ...current, [field]: value }));
  };

  return (
    <div className="map-viewport">
      <div
        ref={mapContainerRef}
        style={{
          width: '100%',
          height: '100%',
          background: theme === 'google-roadmap' ? '#aad3df' : '#070b14',
        }}
      />

      <StationPanel
        stations={stations}
        loading={loadingStations}
        saving={savingStation}
        error={stationError}
        mode={formMode}
        form={stationForm}
        onBeginCreate={handleBeginCreate}
        onBeginEdit={handleBeginEdit}
        onCancel={handleCancel}
        onFieldChange={handleFieldChange}
        onSave={handleSave}
        onDelete={handleDelete}
        onFocus={handleFocus}
      />

      <MapControls
        theme={theme}
        onThemeChange={setTheme}
        onResetCenter={handleResetCenter}
        coordRef={coordRef}
      />
    </div>
  );
};
