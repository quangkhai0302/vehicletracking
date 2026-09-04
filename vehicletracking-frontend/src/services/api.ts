import { Station, Route, RouteRequest, Vehicle, Trip, TrafficIncident, SimulatorResponse } from '../types';

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
  if (res.status === 404) return 'Không tìm thấy tài nguyên (404)';
  if (res.status === 409) return 'Xung đột dữ liệu hoặc dữ liệu đang được sử dụng (409)';
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
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi lấy danh sách tuyến đường'));
    }
    return res.json();
  },
  getRouteById: async (id: number): Promise<Route> => {
    const res = await fetch(`${API_BASE}/routes/${id}`);
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Không tìm thấy tuyến đường'));
    }
    return res.json();
  },
  createRoute: async (data: RouteRequest): Promise<Route> => {
    const res = await fetch(`${API_BASE}/routes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi tạo tuyến đường'));
    }
    return res.json();
  },
  updateRoute: async (id: number, data: RouteRequest): Promise<Route> => {
    const res = await fetch(`${API_BASE}/routes/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi cập nhật tuyến đường'));
    }
    return res.json();
  },
  deleteRoute: async (id: number): Promise<void> => {
    const res = await fetch(`${API_BASE}/routes/${id}`, { method: 'DELETE' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi xóa tuyến đường'));
    }
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
  getTripById: async (id: number): Promise<Trip> => {
    const res = await fetch(`${API_BASE}/trips/${id}`);
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Không tìm thấy chuyến đi'));
    }
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
  startSimulator: async (tripId: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/start/${tripId}`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi khởi động mô phỏng'));
    }
    return res.json();
  },
  pauseSimulator: async (tripId: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/pause/${tripId}`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi tạm dừng mô phỏng'));
    }
    return res.json();
  },
  resumeSimulator: async (tripId: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/resume/${tripId}`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi tiếp tục mô phỏng'));
    }
    return res.json();
  },
  resetSimulator: async (tripId: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/reset/${tripId}`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi đặt lại mô phỏng'));
    }
    return res.json();
  },
  setMultiplier: async (tripId: number, multiplier: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/multiplier/${tripId}?multiplier=${multiplier}`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi cập nhật hệ số tốc độ'));
    }
    return res.json();
  },
  getSimulatorStatus: async (tripId: number): Promise<SimulatorResponse> => {
    const res = await fetch(`${API_BASE}/simulator/status/${tripId}`);
    if (!res.ok) {
      throw new Error(await parseErrorMessage(res, 'Lỗi tải trạng thái mô phỏng'));
    }
    return res.json();
  }
};
