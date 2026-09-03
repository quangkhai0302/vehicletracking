import { Station, Route, Vehicle, Trip, TrafficIncident } from '../types';

const API_BASE = 'http://localhost:8080/api';

async function parseErrorMessage(res: Response, defaultMessage: string): Promise<string> {
  try {
    const data = await res.json();
    if (data.detail) {
      if (Array.isArray(data.errors) && data.errors.length > 0) {
        const fieldDetails = data.errors
          .map((e: { field?: string; message?: string }) => e.message || e.field)
          .join(', ');
        return `${data.detail}: ${fieldDetails}`;
      }
      return data.detail;
    }
    if (data.message) {
      return data.message;
    }
  } catch {
    // Non-JSON response
  }
  if (res.status === 400) return 'Dữ liệu không hợp lệ (400)';
  if (res.status === 404) return 'Không tìm thấy trạm dừng (404)';
  if (res.status === 409) return 'Mã trạm hoặc quan hệ bị xung đột (409)';
  if (res.status >= 500) return 'Lỗi máy chủ nội bộ (500)';
  return defaultMessage;
}

export const api = {
  // Trạm dừng
  getStations: async (): Promise<Station[]> => {
    const res = await fetch(`${API_BASE}/stations`);
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi lấy danh sách trạm'));
    }
    return res.json();
  },
  getStationById: async (id: number): Promise<Station> => {
    const res = await fetch(`${API_BASE}/stations/${id}`);
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Không tìm thấy trạm dừng'));
    }
    return res.json();
  },
  createStation: async (data: Partial<Station>): Promise<Station> => {
    const res = await fetch(`${API_BASE}/stations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi tạo trạm'));
    }
    return res.json();
  },
  updateStation: async (id: number, data: Partial<Station>): Promise<Station> => {
    const res = await fetch(`${API_BASE}/stations/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi cập nhật trạm'));
    }
    return res.json();
  },
  deleteStation: async (id: number): Promise<void> => {
    const res = await fetch(`${API_BASE}/stations/${id}`, { method: 'DELETE' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi xóa trạm'));
    }
  },

  // Tuyến đường
  getRoutes: async (): Promise<Route[]> => {
    const res = await fetch(`${API_BASE}/routes`);
    return res.json();
  },
  getRouteById: async (id: number): Promise<Route> => {
    const res = await fetch(`${API_BASE}/routes/${id}`);
    return res.json();
  },
  createRoute: async (data: { code?: string; name: string; description?: string; stationIds: number[] }): Promise<Route> => {
    const res = await fetch(`${API_BASE}/routes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error((await res.json()).message || 'Lỗi tạo tuyến');
    return res.json();
  },
  deleteRoute: async (id: number): Promise<void> => {
    const res = await fetch(`${API_BASE}/routes/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('Lỗi xóa tuyến');
  },

  // Phương tiện & Chuyến đi
  getVehicles: async (): Promise<Vehicle[]> => {
    const res = await fetch(`${API_BASE}/vehicles`);
    return res.json();
  },
  getTrips: async (): Promise<Trip[]> => {
    const res = await fetch(`${API_BASE}/trips`);
    return res.json();
  },
  createTrip: async (routeId: number, vehicleId: number): Promise<Trip> => {
    const res = await fetch(`${API_BASE}/trips?routeId=${routeId}&vehicleId=${vehicleId}`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Lỗi tạo chuyến đi');
    return res.json();
  },

  // Sự cố giao thông
  getIncidents: async (): Promise<TrafficIncident[]> => {
    const res = await fetch(`${API_BASE}/incidents`);
    return res.json();
  },
  createIncident: async (data: Partial<TrafficIncident>): Promise<TrafficIncident> => {
    const res = await fetch(`${API_BASE}/incidents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error('Lỗi tạo sự cố');
    return res.json();
  },
  toggleIncident: async (id: number): Promise<TrafficIncident> => {
    const res = await fetch(`${API_BASE}/incidents/${id}/toggle`, { method: 'PATCH' });
    return res.json();
  },
  deleteIncident: async (id: number): Promise<void> => {
    const res = await fetch(`${API_BASE}/incidents/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('Lỗi xóa sự cố');
  },

  // Simulator
  startSimulator: async (tripId: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/start/${tripId}`, { method: 'POST' });
    return res.json();
  },
  pauseSimulator: async (tripId: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/pause/${tripId}`, { method: 'POST' });
    return res.json();
  },
  resumeSimulator: async (tripId: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/resume/${tripId}`, { method: 'POST' });
    return res.json();
  },
  resetSimulator: async (tripId: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/reset/${tripId}`, { method: 'POST' });
    return res.json();
  },
  setMultiplier: async (tripId: number, multiplier: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/multiplier/${tripId}?multiplier=${multiplier}`, { method: 'POST' });
    return res.json();
  },
  getSimulatorStatus: async (tripId: number): Promise<any> => {
    const res = await fetch(`${API_BASE}/simulator/status/${tripId}`);
    return res.json();
  }
};
