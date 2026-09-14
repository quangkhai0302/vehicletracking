import type { TrafficFlowSegment, TrafficIncident } from '../types/map';
import type { TrafficFlowResponse, TrafficIncidentsResponse } from '../types/traffic';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const TRAFFIC_TILE_URL = `${API_BASE_URL}/api/v1/traffic/tiles/{z}/{x}/{y}.png`;

// Mặc định Bounding Box khu vực trung tâm TP. Hồ Chí Minh
// west (minLng), south (minLat), east (maxLng), north (maxLat)
export const DEFAULT_HCMC_BBOX = '106.64,10.74,106.74,10.84';

export interface TrafficBoundsParams {
  west: number | string;
  south: number | string;
  east: number | string;
  north: number | string;
}

function parseBboxString(bbox: string): TrafficBoundsParams {
  const parts = bbox.split(',').map((p) => Number(p.trim()));
  if (parts.length !== 4) {
    throw new Error('Invalid bbox format. Expected "west,south,east,north"');
  }
  if (parts.some(value => !Number.isFinite(value))) throw new Error('Invalid bbox coordinates');
  const [west, south, east, north] = parts;
  if (west < -180 || east > 180 || south < -90 || north > 90 || west >= east || south >= north) {
    throw new Error('Invalid bbox coordinates');
  }
  return {
    west,
    south,
    east,
    north,
  };
}

export async function fetchHereTrafficFlow(
  bbox: string = DEFAULT_HCMC_BBOX,
  signal?: AbortSignal,
): Promise<TrafficFlowResponse> {
  const { west, south, east, north } = parseBboxString(bbox);
  const url = `${API_BASE_URL}/api/v1/traffic/flow?west=${encodeURIComponent(west)}&south=${encodeURIComponent(south)}&east=${encodeURIComponent(east)}&north=${encodeURIComponent(north)}`;

  try {
    const response = await fetch(url, { signal });
    if (!response.ok) {
      throw new Error(`Traffic Flow API error: HTTP ${response.status}`);
    }

    const data = await response.json() as TrafficFlowResponse;
    return { ...data, results: Array.isArray(data.results) ? data.results as TrafficFlowSegment[] : [] };
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      console.error('Lỗi khi tải dữ liệu Traffic Flow từ backend proxy:', error);
    }
    throw error;
  }
}

export async function fetchHereIncidents(
  bbox: string = DEFAULT_HCMC_BBOX,
  signal?: AbortSignal,
): Promise<TrafficIncidentsResponse> {
  const { west, south, east, north } = parseBboxString(bbox);
  const url = `${API_BASE_URL}/api/v1/traffic/incidents?west=${encodeURIComponent(west)}&south=${encodeURIComponent(south)}&east=${encodeURIComponent(east)}&north=${encodeURIComponent(north)}`;

  try {
    const response = await fetch(url, { signal });
    if (!response.ok) {
      throw new Error(`Traffic Incidents API error: HTTP ${response.status}`);
    }

    const data = await response.json() as TrafficIncidentsResponse;
    return { ...data, results: Array.isArray(data.results) ? data.results as TrafficIncident[] : [] };
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      console.error('Lỗi khi tải dữ liệu Traffic Incidents từ backend proxy:', error);
    }
    throw error;
  }
}
