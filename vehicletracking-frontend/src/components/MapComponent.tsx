import { useEffect, useMemo, useRef, useState, type FC } from 'react';
import L from 'leaflet';
import { createStation, deleteStation, fetchStations, updateStation } from '../services/stations';
import type { MapTheme } from '../types/map';
import {
  EMPTY_STATION_FORM,
  type Station,
  type StationFormMode,
  type StationFormState,
  type StationInput,
} from '../types/station';
import type { Vehicle } from '../types/vehicle';
import type { WorkspaceMode } from '../types/workspace';
import { MapControls } from './MapControls';
import { StationDrawer } from './StationDrawer';
import { StationPanel } from './StationPanel';
import { TrackingPanel } from './TrackingPanel';
import { VehicleDrawer } from './VehicleDrawer';
import { Crosshair, X } from 'lucide-react';

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

interface MapComponentProps {
  workspace: WorkspaceMode;
  onWorkspaceChange: (workspace: WorkspaceMode) => void;
}


function createStationIcon(state: 'default' | 'selected' | 'muted' | 'draft'): L.DivIcon {
  return L.divIcon({
    className: 'station-div-icon',
    html: `<span class="station-map-marker ${state}"><span class="station-map-marker-core"></span></span>`,
    iconSize: [34, 42],
    iconAnchor: [17, 38],
    popupAnchor: [0, -36],
    tooltipAnchor: [0, -32],
  });
}

function createVehicleIcon(vehicle: Vehicle, selected: boolean): L.DivIcon {
  const isDelayed = vehicle.status === 'DELAYED';
  const statusClass = isDelayed ? 'delayed' : 'running';
  const selectedClass = selected ? 'selected' : '';

  const svg = `
    <div class="vehicle-map-marker ${statusClass} ${selectedClass}">
      <div class="vehicle-marker-pulse"></div>
      <div class="vehicle-marker-body" style="transform: rotate(${vehicle.heading}deg);">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6H6V6h12v5z"/>
        </svg>
      </div>
    </div>
  `;

  return L.divIcon({
    className: 'vehicle-div-icon',
    html: svg,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -22],
    tooltipAnchor: [0, -20],
  });
}

function validCoordinate(value: string, min: number, max: number): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
}

export const MapComponent: FC<MapComponentProps> = ({ workspace, onWorkspaceChange }) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const stationLayerRef = useRef<L.LayerGroup | null>(null);
  const draftLayerRef = useRef<L.LayerGroup | null>(null);
  const vehicleLayerRef = useRef<L.LayerGroup | null>(null);
  const routeLayerRef = useRef<L.LayerGroup | null>(null);
  const stationMarkerRef = useRef<Map<number, L.Marker>>(new Map());
  const vehicleMarkerRef = useRef<Map<string, L.Marker>>(new Map());
  const coordRef = useRef<HTMLSpanElement>(null);

  const [theme, setTheme] = useState<MapTheme>('google-roadmap');
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
  const [toast, setToast] = useState<string | null>(null);

  // Vehicle Tracking State (Dữ liệu thật, mặc định không có mock xe)
  const [vehicles] = useState<Vehicle[]>([]);
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(null);
  const [followingVehicle, setFollowingVehicle] = useState(false);

  const selectedStation = useMemo(
    () => stations.find((station) => station.id === selectedStationId) ?? null,
    [selectedStationId, stations]
  );

  const selectedVehicle = useMemo(
    () => vehicles.find((v) => v.id === selectedVehicleId) ?? null,
    [selectedVehicleId, vehicles]
  );

  const drawerVisible =
    (workspace === 'stations' && (formMode !== 'closed' || selectedStation !== null)) ||
    (workspace === 'tracking' && selectedVehicle !== null);

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

  // Auto hide toast
  useEffect(() => {
    if (!toast) return;
    const timeoutId = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  // Initialize Map
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

    routeLayerRef.current = L.layerGroup().addTo(map);
    stationLayerRef.current = L.layerGroup().addTo(map);
    draftLayerRef.current = L.layerGroup().addTo(map);
    vehicleLayerRef.current = L.layerGroup().addTo(map);

    let rafId: number | null = null;
    let pendingCoords: { lat: number; lng: number } | null = null;

    const onMouseMove = (event: L.LeafletMouseEvent) => {
      pendingCoords = { lat: event.latlng.lat, lng: event.latlng.lng };
      if (rafId) return;
      rafId = requestAnimationFrame(() => {
        if (coordRef.current && pendingCoords) {
          coordRef.current.textContent = `Tọa độ: ${pendingCoords.lat.toFixed(5)}, ${pendingCoords.lng.toFixed(5)}`;
        }
        rafId = null;
      });
    };

    const onMouseOut = () => {
      if (rafId) cancelAnimationFrame(rafId);
      rafId = null;
      if (coordRef.current) coordRef.current.textContent = 'Di chuột trên bản đồ để xem tọa độ';
    };

    // User drag starts -> Stop following vehicle
    const onDragStart = () => {
      setFollowingVehicle(false);
    };

    map.on('mousemove', onMouseMove);
    map.on('mouseout', onMouseOut);
    map.on('dragstart', onDragStart);
    mapInstanceRef.current = map;

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      map.off('mousemove', onMouseMove);
      map.off('mouseout', onMouseOut);
      map.off('dragstart', onDragStart);
      map.remove();
      mapInstanceRef.current = null;
      stationLayerRef.current = null;
      draftLayerRef.current = null;
      vehicleLayerRef.current = null;
      routeLayerRef.current = null;
    };
  }, []);

  // Map click for picking location
  useEffect(() => {
    const map = mapInstanceRef.current;
    const mapContainer = mapContainerRef.current;
    if (!map || workspace !== 'stations' || formMode === 'closed' || !pickingLocation) return;

    const onMapClick = (event: L.LeafletMouseEvent) => {
      setStationForm((current) => ({
        ...current,
        latitude: event.latlng.lat.toFixed(6),
        longitude: event.latlng.lng.toFixed(6),
      }));
      setPickingLocation(false);
    };

    map.on('click', onMapClick);
    mapContainer?.classList.add('station-picking');
    return () => {
      map.off('click', onMapClick);
      mapContainer?.classList.remove('station-picking');
    };
  }, [formMode, pickingLocation, workspace]);

  // Render Stations Layer
  useEffect(() => {
    const layer = stationLayerRef.current;
    const map = mapInstanceRef.current;
    if (!layer || !map) return;
    layer.clearLayers();
    stationMarkerRef.current.clear();

    stations.forEach((station) => {
      if (workspace === 'stations' && formMode === 'edit' && editingStation?.id === station.id) return;
      const selected = workspace === 'stations' && selectedStationId === station.id;
      const position: L.LatLngExpression = [station.latitude, station.longitude];

      if (selected) {
        L.circle(position, {
          radius: station.checkinRadiusMeters,
          color: '#0284c7',
          weight: 2,
          opacity: 0.85,
          fillColor: '#38bdf8',
          fillOpacity: 0.12,
          interactive: false,
        }).addTo(layer);
      }

      const marker = L.marker(position, {
        icon: createStationIcon(workspace === 'tracking' ? 'muted' : selected ? 'selected' : 'default'),
        zIndexOffset: selected ? 500 : 100,
        bubblingMouseEvents: false,
      });

      marker.bindTooltip(station.name, { direction: 'top', offset: [0, -28], opacity: 0.9 });

      if (workspace === 'tracking') {
        marker.on('click', () => {
          map.setView([station.latitude, station.longitude], 16, { animate: true });
        });
      } else {
        marker.on('click', () => {
          if (formMode !== 'closed') return;
          setSelectedStationId(station.id);
          setStationError(null);
          map.setView([station.latitude, station.longitude], 16, { animate: true });
        });
      }

      marker.addTo(layer);
      stationMarkerRef.current.set(station.id, marker);
    });
  }, [editingStation?.id, formMode, selectedStationId, stations, workspace]);

  // Render Draft Station (Create/Edit)
  useEffect(() => {
    const layer = draftLayerRef.current;
    if (!layer) return;
    layer.clearLayers();
    if (workspace !== 'stations' || formMode === 'closed') return;

    const latitude = validCoordinate(stationForm.latitude, -90, 90);
    const longitude = validCoordinate(stationForm.longitude, -180, 180);
    if (latitude === null || longitude === null) return;
    const position: L.LatLngExpression = [latitude, longitude];
    const radius = Number(stationForm.checkinRadiusMeters);

    if (Number.isFinite(radius) && radius >= 10 && radius <= 1000) {
      L.circle(position, {
        radius,
        color: '#f59e0b',
        dashArray: '6 6',
        weight: 2,
        opacity: 0.9,
        fillColor: '#f59e0b',
        fillOpacity: 0.14,
        interactive: false,
      }).addTo(layer);
    }

    const marker = L.marker(position, {
      icon: createStationIcon('draft'),
      draggable: true,
      zIndexOffset: 1000,
      bubblingMouseEvents: false,
    });

    marker.on('dragend', () => {
      const coordinates = marker.getLatLng();
      setStationForm((current) => ({
        ...current,
        latitude: coordinates.lat.toFixed(6),
        longitude: coordinates.lng.toFixed(6),
      }));
      setPickingLocation(false);
    });

    marker.bindTooltip('Kéo để tinh chỉnh vị trí trạm', { permanent: true, direction: 'top', offset: [0, -28] });
    marker.addTo(layer);
  }, [formMode, stationForm, workspace]);

  // Render Vehicles & Route Polyline (Chỉ render khi có xe thật)
  useEffect(() => {
    const vLayer = vehicleLayerRef.current;
    const rLayer = routeLayerRef.current;
    const map = mapInstanceRef.current;
    if (!vLayer || !rLayer || !map) return;

    vLayer.clearLayers();
    rLayer.clearLayers();
    vehicleMarkerRef.current.clear();

    if (workspace !== 'tracking' || vehicles.length === 0) {
      return;
    }

    vehicles.forEach((v) => {
      const isSelected = selectedVehicleId === v.id;
      const marker = L.marker([v.latitude, v.longitude], {
        icon: createVehicleIcon(v, isSelected),
        zIndexOffset: isSelected ? 800 : 400,
      });

      marker.bindTooltip(`${v.plateNumber} • ${v.speedKmh} km/h`, {
        direction: 'top',
        offset: [0, -18],
        opacity: 0.92,
      });

      marker.on('click', () => {
        setSelectedVehicleId(v.id);
        map.setView([v.latitude, v.longitude], 16, { animate: true });
      });

      marker.addTo(vLayer);
      vehicleMarkerRef.current.set(v.id, marker);
    });

    // Follow vehicle auto-pan
    if (followingVehicle && selectedVehicle) {
      map.panTo([selectedVehicle.latitude, selectedVehicle.longitude], {
        animate: true,
        duration: 0.6,
      });
    }
  }, [followingVehicle, selectedVehicle, selectedVehicleId, vehicles, workspace]);

  // Tile layer change (Themes)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    if (tileLayerRef.current) map.removeLayer(tileLayerRef.current);
    const layerType = theme === 'google-satellite' ? 'y' : 'm';
    const newTileLayer = L.tileLayer(
      `https://{s}.google.com/vt/lyrs=${layerType}&hl=vi&gl=VN&x={x}&y={y}&z={z}`,
      {
        maxZoom: 20,
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
        className: theme === 'google-dark' ? 'dark-map-tiles' : '',
        attribution: '&copy; Google Maps',
        updateWhenZooming: true,
        updateWhenIdle: false,
        keepBuffer: 6,
      }
    );
    newTileLayer.addTo(map).bringToBack();
    tileLayerRef.current = newTileLayer;
  }, [theme]);

  // Actions for Stations
  const handleBeginCreate = () => {
    setSelectedStationId(null);
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
    setStationError(null);
    setFormMode('create');
    setPickingLocation(true);
  };

  const handleSelectStation = (station: Station) => {
    if (formMode !== 'closed') return;
    setSelectedStationId(station.id);
    setStationError(null);
    mapInstanceRef.current?.setView([station.latitude, station.longitude], 16, { animate: true });
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
    mapInstanceRef.current?.setView([target.latitude, target.longitude], 16, { animate: true });
  };

  const handleCloseStationDrawer = () => {
    const closingMode = formMode;
    setFormMode('closed');
    setEditingStation(null);
    setStationForm(EMPTY_STATION_FORM);
    setPickingLocation(false);
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
      setToast(formMode === 'create' ? `Đã tạo trạm “${saved.name}”.` : `Đã cập nhật trạm “${saved.name}”.`);
      mapInstanceRef.current?.setView([saved.latitude, saved.longitude], 16, { animate: true });
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
      setToast(`Đã ngừng sử dụng trạm “${deleteCandidate.name}”.`);
    } catch (error) {
      setDeleteCandidate(null);
      setStationError(error instanceof Error ? error.message : 'Không thể ngừng sử dụng trạm');
    } finally {
      setDeletingStation(false);
    }
  };

  // Helper: Pick map center coordinates
  const handlePickMapCenter = () => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const center = map.getCenter();
    setStationForm((cur) => ({
      ...cur,
      latitude: center.lat.toFixed(6),
      longitude: center.lng.toFixed(6),
    }));
    setPickingLocation(false);
    setToast('Đã gán tọa độ tâm bản đồ vào biểu mẫu.');
  };

  // Actions for Vehicle Tracking
  const handleSelectVehicle = (vehicle: Vehicle) => {
    setSelectedVehicleId(vehicle.id);
    mapInstanceRef.current?.setView([vehicle.latitude, vehicle.longitude], 16, { animate: true });
  };

  return (
    <main className={`map-viewport ${drawerVisible ? 'has-right-drawer' : ''}`} data-workspace={workspace}>
      <div ref={mapContainerRef} className="map-canvas" />

      {/* WORKSPACE 1: THEO DÕI XE */}
      {workspace === 'tracking' && (
        <>
          <TrackingPanel
            vehicles={vehicles}
            selectedVehicleId={selectedVehicleId}
            onSelectVehicle={handleSelectVehicle}
            onManageStations={() => onWorkspaceChange('stations')}
          />
          <VehicleDrawer
            vehicle={selectedVehicle}
            following={followingVehicle}
            onToggleFollow={() => setFollowingVehicle((cur) => !cur)}
            onFitRoute={() => {}}
            onClose={() => {
              setSelectedVehicleId(null);
              setFollowingVehicle(false);
            }}
          />
        </>
      )}

      {/* WORKSPACE 2: QUẢN LÝ TRẠM */}
      {workspace === 'stations' && (
        <>
          <StationPanel
            stations={stations}
            selectedStationId={selectedStationId}
            loading={loadingStations}
            error={formMode === 'closed' ? stationError : null}
            selectionDisabled={formMode !== 'closed'}
            mode={formMode}
            onBeginCreate={handleBeginCreate}
            onSelect={handleSelectStation}
            onBeginEdit={handleBeginEdit}
            onDelete={(station) => setDeleteCandidate(station)}
          />
          <StationDrawer
            station={selectedStation}
            mode={formMode}
            form={stationForm}
            saving={savingStation}
            error={formMode !== 'closed' ? stationError : null}
            pickingLocation={pickingLocation}
            onClose={handleCloseStationDrawer}
            onBeginEdit={() => handleBeginEdit()}
            onPickLocation={() => setPickingLocation(true)}
            onFieldChange={handleFieldChange}
            onSave={handleSaveStation}
            onRequestDeactivate={() => selectedStation && setDeleteCandidate(selectedStation)}
          />
        </>
      )}

      {/* Banner hướng dẫn và tiện ích chọn vị trí trạm trên bản đồ */}
      {pickingLocation && workspace === 'stations' && (
        <div className="map-picking-banner" role="status">
          <Crosshair size={15} />
          <span>Nhấp chuột lên bản đồ để đặt vị trí trạm</span>
          <div className="picking-banner-actions">
            <button
              type="button"
              className="picking-center-btn"
              onClick={handlePickMapCenter}
              title="Lấy ngay tọa độ tâm màn hình bản đồ hiện tại"
            >
              Lấy tâm bản đồ
            </button>
            <button
              type="button"
              className="picking-cancel-btn"
              onClick={() => setPickingLocation(false)}
              title="Hủy chế độ chọn vị trí"
            >
              <X size={14} />
            </button>
          </div>
        </div>
      )}

      <MapControls
        theme={theme}
        onThemeChange={setTheme}
        onResetCenter={() => mapInstanceRef.current?.setView(HCMC_CENTER, 13, { animate: true })}
        coordRef={coordRef}
      />

      {/* Hộp thoại xác nhận ngừng sử dụng trạm */}
      {deleteCandidate && (
        <div className="dialog-backdrop" role="presentation">
          <section className="confirmation-dialog" role="dialog" aria-modal="true" aria-labelledby="deactivate-title">
            <div className="dialog-icon" aria-hidden="true">!</div>
            <h2 id="deactivate-title">Ngừng sử dụng trạm đón trả khách?</h2>
            <div className="dialog-station-info">
              <strong className="dialog-station-name">{deleteCandidate.name}</strong>
              {deleteCandidate.address && <p className="dialog-station-addr">{deleteCandidate.address}</p>}
              <span className="dialog-station-coords tabular-numbers">
                Tọa độ: {deleteCandidate.latitude.toFixed(5)}, {deleteCandidate.longitude.toFixed(5)}
              </span>
            </div>
            <p className="dialog-explanation">
              Trạm này sẽ chuyển sang trạng thái ngừng hoạt động và không hiển thị cho các chuyến đi mới. Dữ liệu vẫn được lưu trữ để phục vụ lịch sử vận hành.
            </p>
            <div className="dialog-actions">
              <button
                type="button"
                className="secondary-action"
                onClick={() => setDeleteCandidate(null)}
                disabled={deletingStation}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="danger-action"
                onClick={handleDeactivate}
                disabled={deletingStation}
              >
                {deletingStation ? 'Đang xử lý…' : 'Xác nhận ngừng sử dụng'}
              </button>
            </div>
          </section>
        </div>
      )}

      {toast && <div className="application-toast" role="status">{toast}</div>}
    </main>
  );
};
