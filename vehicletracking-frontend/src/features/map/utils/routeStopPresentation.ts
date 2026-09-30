import L from 'leaflet';
import type { RouteStopRole } from '@/features/routes/types/route';
import '@/features/map/styles/operational-markers.css';

export type RouteStopProgress = 'checked-in' | 'next' | 'pending';
export const stopProgressLabel = (state: RouteStopProgress) =>
  state === 'checked-in' ? 'Đã check-in' : state === 'next' ? 'Trạm kế tiếp' : 'Chưa check-in';
export const stopRoleLabel = (role: RouteStopRole) =>
  role === 'START' ? 'Khởi hành' : role === 'END' ? 'Về đích' : 'Đón/trả';

export function createRouteStopIcon(
  sequenceNumber: number,
  role: RouteStopRole,
  progress?: RouteStopProgress,
): L.DivIcon {
  return L.divIcon({
    className: 'route-stop-div-icon',
    html: `<div class="route-stop-map-marker ${role.toLowerCase()}${progress ? ` simulation-${progress}` : ''}" aria-hidden="true">
      <svg class="route-stop-map-marker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round" focusable="false">
        <path d="M4 6 2 7" /><path d="M10 6h4" /><path d="m22 7-2-1" />
        <rect width="16" height="16" x="4" y="3" rx="2" /><path d="M4 11h16" />
        <path d="M8 15h.01" /><path d="M16 15h.01" /><path d="M6 19v2" /><path d="M18 21v-2" />
      </svg><span class="route-stop-map-marker-sequence">${Number(sequenceNumber)}</span>
    </div>`,
    iconSize: [42, 48],
    iconAnchor: [21, 46],
    popupAnchor: [0, -42],
    tooltipAnchor: [0, -40],
  });
}

export interface MapStop {
  sequenceNumber: number;
  stationName: string;
  role: RouteStopRole;
  latitude: number;
  longitude: number;
  dwellDurationSeconds: number;
}
export function createRouteStopPopup(
  stop: MapStop,
  progress: RouteStopProgress,
  station?: { address?: string | null; checkinRadiusMeters: number } | null,
) {
  const popup = document.createElement('section');
  popup.className = 'simulation-stop-popup';
  const heading = document.createElement('div');
  heading.className = 'simulation-stop-popup-heading';
  const copy = document.createElement('span'),
    eyebrow = document.createElement('small'),
    title = document.createElement('strong');
  eyebrow.textContent = `Trạm ${stop.sequenceNumber} · ${stopRoleLabel(stop.role)}`;
  title.textContent = stop.stationName;
  copy.append(eyebrow, title);
  const status = document.createElement('b');
  status.dataset.state = progress;
  status.textContent = stopProgressLabel(progress);
  heading.append(copy, status);
  popup.append(heading);
  const address = document.createElement('p');
  address.className = 'simulation-stop-popup-address';
  address.textContent = station?.address || 'Chưa có địa chỉ mô tả';
  popup.append(address);
  const metrics = document.createElement('dl');
  for (const [label, value] of [
    ['Vùng check-in', station ? `${station.checkinRadiusMeters} m` : 'Đang cập nhật'],
    ['Dừng tại trạm', `${stop.dwellDurationSeconds} giây`],
    ['Tọa độ', `${stop.latitude.toFixed(6)}, ${stop.longitude.toFixed(6)}`],
  ]) {
    const row = document.createElement('div'),
      term = document.createElement('dt'),
      description = document.createElement('dd');
    term.textContent = label;
    description.textContent = value;
    row.append(term, description);
    metrics.append(row);
  }
  popup.append(metrics);
  return popup;
}
export function updateRouteStopPopup(content: unknown, progress: RouteStopProgress) {
  if (!(content instanceof HTMLElement)) return;
  const status = content.querySelector<HTMLElement>('.simulation-stop-popup-heading b');
  if (status) {
    status.dataset.state = progress;
    status.textContent = stopProgressLabel(progress);
  }
}
