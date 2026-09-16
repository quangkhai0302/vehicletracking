import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import type { TrafficFlowSegment } from '../../types/map';
import type { TrafficEnvelope, TrafficIncidentsResponse } from '../../types/traffic';
import { nearbyIncidents, segmentMetrics, trafficAge, usableTraffic, type Coordinate } from '../../utils/routeInspection';
import { inspectionDuration } from '../../utils/inspectionFormat';
import '../route/route-inspection.css';

const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const severity: Record<string, string> = { critical: 'Nghiêm trọng', major: 'Đáng chú ý', minor: 'Nhẹ', low: 'Thấp' };

export function MapInspectionCard({ x, y, pinned, kind, onClose, children }: {
  x: number; y: number; pinned: boolean; kind: 'route' | 'road'; onClose: () => void; children: ReactNode;
}) {
  const card = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 304, height: 360 });
  const [viewport, setViewport] = useState({ width: window.innerWidth, height: window.innerHeight });
  useLayoutEffect(() => {
    if (!card.current) return;
    const observer = new ResizeObserver(entries => {
      const rect = entries[0].target.getBoundingClientRect();
      setSize(previous => previous.width === rect.width && previous.height === rect.height ? previous : { width: rect.width, height: rect.height });
    });
    const resize = () => setViewport({ width: window.innerWidth, height: window.innerHeight });
    observer.observe(card.current); window.addEventListener('resize', resize);
    return () => { observer.disconnect(); window.removeEventListener('resize', resize); };
  }, []);
  const left = Math.max(8, Math.min(x + 18 + size.width > viewport.width - 8 ? x - size.width - 18 : x + 18, viewport.width - size.width - 8));
  const top = Math.max(8, Math.min(y + 18, viewport.height - size.height - 8));
  return createPortal(<div ref={card} className="route-inspection-card" data-pinned={pinned} data-kind={kind}
    role={pinned ? 'dialog' : 'tooltip'} aria-label={kind === 'route' ? 'Thông tin đoạn đường' : 'Thông tin đường trên bản đồ'} style={{ left, top }}>
    <div className="route-inspection-heading"><span>{kind === 'route' ? 'THÔNG TIN TUYẾN' : 'GIAO THÔNG TRÊN ĐƯỜNG'}</span>
      {pinned && <button aria-label="Đóng thông tin đoạn đường" onClick={onClose}>×</button>}</div>
    {children}
  </div>, document.body);
}

export function TrafficSourceLine({ envelope, receivedAt, now, label }: { envelope: TrafficEnvelope<unknown>; receivedAt: number; now: number; label: string }) {
  const age = trafficAge(envelope, receivedAt, now);
  const stale = envelope.source === 'HERE_LAST_KNOWN' || envelope.status === 'STALE' || (age !== null && age > 90);
  const ageLabel = age === null ? 'Thời gian cập nhật không rõ' : age < 60 ? `Cập nhật ${age} giây trước` : `Cập nhật ${Math.floor(age / 60)} phút trước`;
  return <p className="route-inspection-source" data-stale={stale}>
    {label}: HERE · {stale ? 'Dữ liệu gần nhất' : ageLabel}
  </p>;
}

export function TrafficFlowDetails({ flow, showName = true }: { flow: TrafficFlowSegment; showName?: boolean }) {
  const metrics = segmentMetrics(flow);
  const status = metrics.blocked ? 'Đóng đường' : flow.speedKmh === 0 ? 'Dòng xe đang dừng'
    : Number.isFinite(flow.jamFactor) ? flow.jamFactor >= 8 ? 'Ùn tắc' : flow.jamFactor >= 4 ? 'Di chuyển chậm' : 'Thông thoáng' : 'Chưa rõ tình trạng';
  const tone = metrics.blocked || flow.speedKmh === 0 || flow.jamFactor >= 8 ? 'danger' : flow.jamFactor >= 4 ? 'warning' : 'normal';
  return <>
    <div className="route-inspection-status-row">
      <span>Tình trạng giao thông</span>
      <strong className="route-inspection-status" data-tone={tone}>{status}</strong>
    </div>
    {showName && flow.description && <div className="route-inspection-road">
      <span>Tên đường trên đoạn này</span>
      <strong>{flow.description}</strong>
    </div>}
    <dl className="route-inspection-metrics">
      <div><dt>Tốc độ hiện tại</dt><dd>{number.format(flow.speedKmh)} km/h</dd></div>
      <div><dt>Thời gian qua đoạn</dt><dd>{metrics.blocked ? 'Đang bị chặn' : inspectionDuration(metrics.travel)}</dd></div>
      {metrics.delay != null && metrics.delay > 0 && <div><dt>Chậm hơn bình thường</dt><dd>+{inspectionDuration(metrics.delay)}</dd></div>}
    </dl>
  </>;
}

export function NearbyTrafficIncidents({ point, envelope, now, failed = false }: {
  point: Coordinate; envelope: TrafficIncidentsResponse | null; now: number; failed?: boolean;
}) {
  const incidents = usableTraffic(envelope) ? nearbyIncidents(point, envelope!.results, now) : [];
  return <div className="route-inspection-incidents">
    <strong>Sự cố gần đoạn đang xem (50 m)</strong>
    {incidents.slice(0, 2).map(incident => <p key={incident.id}>{incident.description || incident.type || 'Sự cố giao thông'}
      {incident.criticality && <span className="route-inspection-note"> · {severity[incident.criticality] ?? 'Chưa rõ mức độ'}</span>}</p>)}
    {incidents.length > 2 && <p>Và {incidents.length - 2} sự cố khác.</p>}
    {!incidents.length && <p>{failed ? 'Không tải được thông tin sự cố.' : usableTraffic(envelope) ? 'Chưa ghi nhận sự cố gần vị trí này.' : 'Chưa có dữ liệu sự cố.'}</p>}
  </div>;
}
