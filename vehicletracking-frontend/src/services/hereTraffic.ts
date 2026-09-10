import { TrafficFlowSegment, TrafficIncident } from '../types/map';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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
  const parts = bbox.split(',').map((p) => p.trim());
  if (parts.length !== 4) {
    throw new Error('Invalid bbox format. Expected "west,south,east,north"');
  }
  return {
    west: parts[0],
    south: parts[1],
    east: parts[2],
    north: parts[3],
  };
}

export async function fetchHereTrafficFlow(
  bbox: string = DEFAULT_HCMC_BBOX
): Promise<TrafficFlowSegment[]> {
  const { west, south, east, north } = parseBboxString(bbox);
  const url = `${API_BASE_URL}/api/v1/traffic/flow?west=${encodeURIComponent(west)}&south=${encodeURIComponent(south)}&east=${encodeURIComponent(east)}&north=${encodeURIComponent(north)}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Traffic Flow API error: HTTP ${response.status}`);
    }

    const data = await response.json();
    return (data.results || []) as TrafficFlowSegment[];
  } catch (error) {
    console.error('Lỗi khi tải dữ liệu Traffic Flow từ backend proxy:', error);
    throw error;
  }
}

export async function fetchHereIncidents(
  bbox: string = DEFAULT_HCMC_BBOX
): Promise<TrafficIncident[]> {
  const { west, south, east, north } = parseBboxString(bbox);
  const url = `${API_BASE_URL}/api/v1/traffic/incidents?west=${encodeURIComponent(west)}&south=${encodeURIComponent(south)}&east=${encodeURIComponent(east)}&north=${encodeURIComponent(north)}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Traffic Incidents API error: HTTP ${response.status}`);
    }

    const data = await response.json();
    return (data.results || []) as TrafficIncident[];
  } catch (error) {
    console.error('Lỗi khi tải dữ liệu Traffic Incidents từ backend proxy:', error);
    throw error;
  }
}
