import React, { useEffect, useState, useCallback } from 'react';
import confetti from 'canvas-confetti';
import { api } from './services/api';
import { wsService } from './services/websocket';
import MapComponent from './components/MapComponent';
import SimulatorPanel from './components/SimulatorPanel';
import TimelinePanel from './components/TimelinePanel';
import ToastNotification from './components/ToastNotification';
import IncidentModal from './components/IncidentModal';
import StationModal from './components/StationModal';
import RouteModal from './components/RouteModal';
import { Station, Route, RouteRequest, Trip, TrafficIncident, VehicleTelemetry, ToastItem } from './types';

export default function App() {
  const [stations, setStations] = useState<Station[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [currentTrip, setCurrentTrip] = useState<Trip | null>(null);
  const [incidents, setIncidents] = useState<TrafficIncident[]>([]);
  const [telemetry, setTelemetry] = useState<VehicleTelemetry | null>(null);
  const [simStatus, setSimStatus] = useState<'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED'>('IDLE');
  const [multiplier, setMultiplier] = useState<number>(1);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const [clickMode, setClickMode] = useState<'ADD_STATION' | 'ADD_INCIDENT' | null>(null);
  const [pendingCoords, setPendingCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [isIncidentsModalOpen, setIsIncidentsModalOpen] = useState(false);
  const [isStationsModalOpen, setIsStationsModalOpen] = useState(false);
  const [isRoutesModalOpen, setIsRoutesModalOpen] = useState(false);

  // Thêm Toast thông báo
  const addToast = useCallback((toast: Omit<ToastItem, 'id' | 'time'>) => {
    const id = Date.now().toString() + Math.random();
    const time = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    setToasts((prev) => [ { ...toast, id, time }, ...prev.slice(0, 4) ]);

    // Tự động ẩn sau 5 giây
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 5500);
  }, []);

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Tải dữ liệu ban đầu
  const loadInitialData = async () => {
    try {
      const [sts, rts, trps, incs] = await Promise.all([
        api.getStations(),
        api.getRoutes(),
        api.getTrips(),
        api.getIncidents(),
      ]);

      setStations(sts);
      setRoutes(rts);
      setIncidents(incs);

      if (rts.length > 0) {
        setSelectedRoute(rts[0]);
      }

      if (trps.length > 0) {
        const trip = trps[0];
        setCurrentTrip(trip);
      }
    } catch (err) {
      console.error('Lỗi khi tải dữ liệu ban đầu:', err);
    }
  };

  useEffect(() => {
    loadInitialData();

    // Kết nối WebSocket STOMP
    wsService.connect(() => {
      console.log('STOMP connected ready');
    });

    // Lắng nghe dữ liệu Telemetry (Vị trí, Tốc độ, ETA)
    const unsubTelemetry = wsService.onTelemetry((data) => {
      setTelemetry(data);

      if (data.status === 'IDLE' && simStatus === 'RUNNING') {
        setSimStatus('COMPLETED');
        confetti({
          particleCount: 120,
          spread: 80,
          origin: { y: 0.6 }
        });
      }
    });

    // Lắng nghe sự kiện Auto Check-in tại các trạm
    const unsubCheckIn = wsService.onCheckIn((event) => {
      addToast({
        type: 'CHECK_IN',
        level: 'INFO',
        title: `Auto Check-in Trạm #${event.stopOrder}`,
        message: event.message,
      });
    });

    // Lắng nghe cảnh báo sự cố kẹt xe và thay đổi lịch trình
    const unsubAlert = wsService.onAlert((alert) => {
      addToast({
        type: 'DELAY_ALERT',
        level: alert.level,
        title: alert.title,
        message: alert.message,
      });
    });

    return () => {
      unsubTelemetry();
      unsubCheckIn();
      unsubAlert();
      wsService.disconnect();
    };
  }, [addToast, simStatus]);

  // Điều khiển Simulator
  const handleStart = async () => {
    if (!currentTrip) return;
    try {
      await api.startSimulator(currentTrip.id);
      setSimStatus('RUNNING');
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Bắt đầu chuyến đi',
        message: `Xe ${currentTrip.vehiclePlateNumber} đã khởi hành từ trạm đầu tiên!`,
      });
    } catch (err) {
      console.error(err);
    }
  };

  const handlePause = async () => {
    if (!currentTrip) return;
    await api.pauseSimulator(currentTrip.id);
    setSimStatus('PAUSED');
  };

  const handleResume = async () => {
    if (!currentTrip) return;
    await api.resumeSimulator(currentTrip.id);
    setSimStatus('RUNNING');
  };

  const handleReset = async () => {
    if (!currentTrip) return;
    await api.resetSimulator(currentTrip.id);
    setSimStatus('IDLE');
    setTelemetry(null);
    loadInitialData();
  };

  const handleSetMultiplier = async (m: number) => {
    if (!currentTrip) return;
    await api.setMultiplier(currentTrip.id, m);
    setMultiplier(m);
  };

  // Xử lý click trên bản đồ
  const handleToggleClickMode = (mode: 'ADD_STATION' | 'ADD_INCIDENT') => {
    if (clickMode === mode) {
      setClickMode(null);
    } else {
      setClickMode(mode);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Chế độ bản đồ',
        message: `Nhấp chuột vào một vị trí trên bản đồ để đặt ${mode === 'ADD_STATION' ? 'trạm dừng' : 'sự cố giao thông'}.`,
      });
    }
  };

  const handleMapClick = (latlng: { lat: number; lng: number }) => {
    setPendingCoords(latlng);
    if (clickMode === 'ADD_INCIDENT') {
      setIsIncidentsModalOpen(true);
      setClickMode(null);
    } else if (clickMode === 'ADD_STATION') {
      setIsStationsModalOpen(true);
      setClickMode(null);
    }
  };

  // Quản lý Sự cố
  const handleToggleIncident = async (id: number) => {
    await api.toggleIncident(id);
    const incs = await api.getIncidents();
    setIncidents(incs);
  };

  const handleDeleteIncident = async (id: number) => {
    await api.deleteIncident(id);
    const incs = await api.getIncidents();
    setIncidents(incs);
  };

  const handleCreateIncident = async (data: Partial<TrafficIncident>) => {
    await api.createIncident(data);
    const incs = await api.getIncidents();
    setIncidents(incs);
  };

  const refreshStations = async () => {
    try {
      const sts = await api.getStations();
      setStations(sts);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi không xác định';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Cảnh báo đồng bộ',
        message: 'Không thể làm mới danh sách trạm: ' + msg,
      });
    }
  };

  // Quản lý Trạm
  const handleCreateStation = async (data: Partial<Station>) => {
    try {
      const created = await api.createStation(data);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Tạo trạm thành công',
        message: `Đã tạo trạm ${created.name} (${created.code})`,
      });
      await refreshStations();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi tạo trạm';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi tạo trạm',
        message: msg,
      });
      throw err;
    }
  };

  const handleUpdateStation = async (id: number, data: Partial<Station>) => {
    try {
      const updated = await api.updateStation(id, data);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Cập nhật thành công',
        message: `Đã cập nhật trạm ${updated.name} (${updated.code})`,
      });
      await refreshStations();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi cập nhật trạm';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi cập nhật trạm',
        message: msg,
      });
      throw err;
    }
  };

  const handleDeleteStation = async (id: number) => {
    try {
      await api.deleteStation(id);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Xóa trạm thành công',
        message: 'Đã xóa trạm dừng khỏi hệ thống',
      });
      await refreshStations();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi xóa trạm';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi xóa trạm',
        message: msg,
      });
      throw err;
    }
  };

  // Quản lý Tuyến đường
  const refreshRoutes = async () => {
    try {
      const rts = await api.getRoutes();
      setRoutes(rts);
      if (selectedRoute) {
        const stillExists = rts.find((r) => r.id === selectedRoute.id);
        if (stillExists) {
          setSelectedRoute(stillExists);
        } else if (rts.length > 0) {
          setSelectedRoute(rts[0]);
        } else {
          setSelectedRoute(null);
        }
      } else if (rts.length > 0) {
        setSelectedRoute(rts[0]);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi không xác định';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Cảnh báo đồng bộ',
        message: 'Không thể làm mới danh sách tuyến đường: ' + msg,
      });
    }
  };

  const handleCreateRoute = async (data: RouteRequest) => {
    try {
      const created = await api.createRoute(data);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Tạo tuyến thành công',
        message: `Đã tạo tuyến ${created.name} (${created.code})`,
      });
      await refreshRoutes();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi tạo tuyến đường';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi tạo tuyến đường',
        message: msg,
      });
      throw err;
    }
  };

  const handleUpdateRoute = async (id: number, data: RouteRequest) => {
    try {
      const updated = await api.updateRoute(id, data);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Cập nhật thành công',
        message: `Đã cập nhật tuyến ${updated.name} (${updated.code})`,
      });
      await refreshRoutes();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi cập nhật tuyến đường';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi cập nhật tuyến đường',
        message: msg,
      });
      throw err;
    }
  };

  const handleDeleteRoute = async (id: number) => {
    try {
      await api.deleteRoute(id);
      addToast({
        type: 'INFO',
        level: 'INFO',
        title: 'Xóa tuyến thành công',
        message: 'Đã xóa tuyến đường khỏi hệ thống',
      });
      await refreshRoutes();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi xóa tuyến đường';
      addToast({
        type: 'INFO',
        level: 'WARNING',
        title: 'Lỗi xóa tuyến đường',
        message: msg,
      });
      throw err;
    }
  };

  return (
    <div style={{ position: 'relative', width: '100vw', height: '100vh', overflow: 'hidden' }}>
      {/* Bản đồ nền tương tác */}
      <MapComponent
        stations={stations}
        route={selectedRoute}
        vehicleTelemetry={telemetry}
        incidents={incidents}
        onMapClick={handleMapClick}
        clickMode={clickMode}
      />

      {/* Thanh điều khiển Simulator ở trên cùng */}
      <SimulatorPanel
        trip={currentTrip}
        route={selectedRoute}
        simStatus={simStatus}
        multiplier={multiplier}
        clickMode={clickMode}
        onStart={handleStart}
        onPause={handlePause}
        onResume={handleResume}
        onReset={handleReset}
        onSetMultiplier={handleSetMultiplier}
        onToggleClickMode={handleToggleClickMode}
        onOpenIncidentsModal={() => setIsIncidentsModalOpen(true)}
        onOpenStationsModal={() => setIsStationsModalOpen(true)}
        onOpenRoutesModal={() => setIsRoutesModalOpen(true)}
      />

      {/* Bảng tiến trình chuyến đi & ETA thời gian thực bên trái */}
      <TimelinePanel
        telemetry={telemetry}
        route={selectedRoute}
        trip={currentTrip}
      />

      {/* Toasts thông báo thời gian thực góc trên bên phải */}
      <ToastNotification toasts={toasts} onDismiss={dismissToast} />

      {/* Modal Quản lý Sự cố giao thông */}
      <IncidentModal
        isOpen={isIncidentsModalOpen}
        onClose={() => {
          setIsIncidentsModalOpen(false);
          setPendingCoords(null);
        }}
        incidents={incidents}
        onToggleIncident={handleToggleIncident}
        onDeleteIncident={handleDeleteIncident}
        onCreateIncident={handleCreateIncident}
        pendingCoords={pendingCoords}
      />

      {/* Modal Quản lý Trạm dừng */}
      <StationModal
        isOpen={isStationsModalOpen}
        onClose={() => {
          setIsStationsModalOpen(false);
          setPendingCoords(null);
        }}
        stations={stations}
        onCreateStation={handleCreateStation}
        onUpdateStation={handleUpdateStation}
        onDeleteStation={handleDeleteStation}
        pendingCoords={pendingCoords}
      />

      {/* Modal Quản lý Tuyến đường */}
      <RouteModal
        isOpen={isRoutesModalOpen}
        onClose={() => setIsRoutesModalOpen(false)}
        routes={routes}
        stations={stations}
        onCreateRoute={handleCreateRoute}
        onUpdateRoute={handleUpdateRoute}
        onDeleteRoute={handleDeleteRoute}
      />
    </div>
  );
}
