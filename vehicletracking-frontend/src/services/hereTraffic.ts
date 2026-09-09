import { TrafficFlowSegment, TrafficIncident } from '../types/map';

const DEFAULT_HERE_API_KEY =
  import.meta.env.VITE_HERE_API_KEY || '-D75IYnLLUODWlZqB8Ugkyo-i_ypcaSlzxBoD6LZo6A';

// Mặc định Bounding Box khu vực trung tâm TP. Hồ Chí Minh
// minLng, minLat, maxLng, maxLat
export const DEFAULT_HCMC_BBOX = '106.64,10.74,106.74,10.84';

interface HereFlowPoint {
  lat: number;
  lng: number;
}

interface HereFlowLink {
  points?: HereFlowPoint[];
}

interface HereFlowItem {
  location?: {
    description?: string;
    length?: number;
    shape?: {
      links?: HereFlowLink[];
    };
  };
  currentFlow?: {
    speed?: number; // m/s
    freeFlow?: number; // m/s
    jamFactor?: number; // 0..10
    traversability?: string;
    confidence?: number;
  };
}

interface HereIncidentItem {
  id?: string;
  location?: {
    description?: string;
    shape?: {
      links?: HereFlowLink[];
    };
  };
  incidentDetails?: {
    type?: string;
    description?: {
      value?: string;
    };
    criticality?: string;
    startTime?: string;
    endTime?: string;
  };
}

export async function fetchHereTrafficFlow(
  bbox: string = DEFAULT_HCMC_BBOX,
  apiKey: string = DEFAULT_HERE_API_KEY
): Promise<TrafficFlowSegment[]> {
  const url = `https://data.traffic.hereapi.com/v7/flow?in=bbox:${bbox}&locationReferencing=shape&apiKey=${apiKey}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`HERE Traffic Flow API error: HTTP ${response.status}`);
    }

    const data = await response.json();
    const items: HereFlowItem[] = data.results || [];
    const segments: TrafficFlowSegment[] = [];

    items.forEach((item, index) => {
      const location = item.location;
      const currentFlow = item.currentFlow;
      if (!location?.shape?.links || !currentFlow) return;

      const description = location.description || 'Tuyến đường TP.HCM';
      const speedKmh = Math.round((currentFlow.speed ?? 0) * 3.6);
      const freeFlowKmh = Math.round((currentFlow.freeFlow ?? 0) * 3.6);
      const jamFactor = Math.round((currentFlow.jamFactor ?? 0) * 10) / 10;
      const traversability = currentFlow.traversability || 'open';
      const confidence = currentFlow.confidence ?? 1;

      location.shape.links.forEach((link, linkIndex) => {
        if (!link.points || link.points.length < 2) return;

        const points: [number, number][] = link.points.map((pt) => [pt.lat, pt.lng]);
        segments.push({
          id: `flow-${index}-${linkIndex}`,
          description,
          points,
          speedKmh,
          freeFlowKmh,
          jamFactor,
          traversability,
          confidence,
        });
      });
    });

    return segments;
  } catch (error) {
    console.error('Lỗi khi tải dữ liệu HERE Traffic Flow:', error);
    throw error;
  }
}

export async function fetchHereIncidents(
  bbox: string = DEFAULT_HCMC_BBOX,
  apiKey: string = DEFAULT_HERE_API_KEY
): Promise<TrafficIncident[]> {
  const url = `https://data.traffic.hereapi.com/v7/incidents?in=bbox:${bbox}&locationReferencing=shape&apiKey=${apiKey}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`HERE Traffic Incidents API error: HTTP ${response.status}`);
    }

    const data = await response.json();
    const items: HereIncidentItem[] = data.results || [];
    const incidents: TrafficIncident[] = [];

    items.forEach((item, index) => {
      const location = item.location;
      const details = item.incidentDetails;
      if (!location || !details) return;

      const points: [number, number][] = [];
      if (location.shape?.links) {
        location.shape.links.forEach((link) => {
          link.points?.forEach((pt) => {
            points.push([pt.lat, pt.lng]);
          });
        });
      }

      if (points.length === 0) return;

      // Tính điểm trung tâm của sự cố
      const centerLat = points.reduce((acc, p) => acc + p[0], 0) / points.length;
      const centerLng = points.reduce((acc, p) => acc + p[1], 0) / points.length;

      const crit = (details.criticality || 'minor').toLowerCase();
      const criticality: 'minor' | 'major' | 'critical' | 'low' =
        crit === 'critical' || crit === 'major' || crit === 'minor' || crit === 'low'
          ? crit
          : 'minor';

      incidents.push({
        id: item.id || `incident-${index}`,
        description: details.description?.value || location.description || 'Sự cố giao thông',
        type: details.type || 'CONGESTION',
        criticality,
        startTime: details.startTime,
        endTime: details.endTime,
        points,
        center: [centerLat, centerLng],
      });
    });

    return incidents;
  } catch (error) {
    console.error('Lỗi khi tải dữ liệu HERE Traffic Incidents:', error);
    return [];
  }
}
