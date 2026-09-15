import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import type { TrafficFlowSegment } from '../../types/map';
import type { TrafficEnvelope, TrafficIncidentsResponse } from '../../types/traffic';
import { nearbyIncidents, segmentMetrics, trafficAge, usableTraffic, type Coordinate } from '../../utils/routeInspection';
import { inspectionDistance, inspectionDuration, inspectionStamp } from '../../utils/inspectionFormat';
import '../route/route-inspection.css';

const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const severity: Record<string, string> = { critical: 'Nghiêm trọng', major: 'Đáng chú ý', minor: 'Nhẹ', low: 'Thấp' };

export function MapInspectionCard({ x, y, pinned, kind, onClose, children }: {
  x: number; y: number; pinned: boolean; kind: 'route' | 'road'; onClose: () => void; children: ReactNode;
}) {
  const card = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 304, height: 460 });
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
    <p className="route-inspection-hint">{pinned ? 'Đã ghim · Esc để đóng' : 'Bấm vào đường hoặc Enter để ghim · Esc để đóng'}</p>
  </div>, document.body);
}

export function TrafficSourceLine({ envelope, receivedAt, now, label }: { envelope: TrafficEnvelope<unknown>; receivedAt: number; now: number; label: string }) {
  const age = trafficAge(envelope, receivedAt, now);
  const stale = envelope.source === 'HERE_LAST_KNOWN' || envelope.status === 'STALE' || (age !== null && age > 90);
  return <p className="route-inspection-source" data-stale={stale}>
    {label}: HERE · {stale ? 'Dữ liệu gần nhất' : 'Dữ liệu đã lấy'}{age !== null ? ` · ${age < 60 ? `${age} giây` : `${Math.floor(age / 60)} phút`} trước` : ''}
    <span>Lấy lúc {inspectionStamp(envelope.fetchedAt)}{envelope.observedAt ? ` · Quan sát ${inspectionStamp(envelope.observedAt)}` : ''}</span>
  </p>;
}

export function TrafficFlowDetails({ flow, showName = true }: { flow: TrafficFlowSegment; showName?: boolean }) {
  const metrics = segmentMetrics(flow);
  const status = metrics.blocked ? 'Đóng đường' : flow.speedKmh === 0 ? 'Dòng xe đang dừng'
    : Number.isFinite(flow.jamFactor) ? flow.jamFactor >= 8 ? 'Ùn tắc' : flow.jamFactor >= 4 ? 'Di chuyển chậm' : 'Thông thoáng' : 'Chưa rõ tình trạng';
  const tone = metrics.blocked || flow.speedKmh === 0 || flow.jamFactor >= 8 ? 'danger' : flow.jamFactor >= 4 ? 'warning' : 'normal';
  return <>
    <p className="route-inspection-status" data-tone={tone}>{status}</p>
    {showName && flow.description && <p className="route-inspection-road">{flow.description}</p>}
    <dl className="route-inspection-metrics">
      <div><dt>Tốc độ dòng xe</dt><dd>{number.format(flow.speedKmh)} km/h</dd></div>
      <div><dt>Khi thông thoáng</dt><dd>{flow.freeFlowKmh > 0 && Number.isFinite(flow.freeFlowKmh) ? `${number.format(flow.freeFlowKmh)} km/h` : 'Chưa có dữ liệu'}</dd></div>
      <div><dt>Đoạn đường có dữ liệu</dt><dd>{metrics.length > 0 ? inspectionDistance(metrics.length) : 'Chưa rõ'}</dd></div>
      <div><dt>Ước tính đi qua đoạn</dt><dd>{metrics.blocked ? 'Đang bị chặn' : inspectionDuration(metrics.travel)}</dd></div>
      <div><dt>Chậm hơn khi thông thoáng</dt><dd>{metrics.delay != null ? `+${inspectionDuration(metrics.delay)}` : 'Chưa ước tính được'}</dd></div>
    </dl>
    <p className="route-inspection-note">Ước tính cho đoạn có dữ liệu tại đây. Tốc độ thông thoáng không phải giới hạn tốc độ.</p>
  </>;
}

export function NearbyTrafficIncidents({ point, envelope, receivedAt, now, failed = false }: {
  point: Coordinate; envelope: TrafficIncidentsResponse | null; receivedAt: number; now: number; failed?: boolean;
}) {
  const incidents = usableTraffic(envelope) ? nearbyIncidents(point, envelope!.results, now) : [];
  return <div className="route-inspection-incidents">
    <strong>Sự cố trong khoảng 50 m</strong>
    {incidents.slice(0, 2).map(incident => <p key={incident.id}>{incident.description || incident.type || 'Sự cố giao thông'}
      {incident.criticality && <span className="route-inspection-note"> · {severity[incident.criticality] ?? 'Chưa rõ mức độ'}</span>}</p>)}
    {incidents.length > 2 && <p>Và {incidents.length - 2} sự cố khác.</p>}
    {!incidents.length && <p>{failed ? 'Không tải được thông tin sự cố.' : usableTraffic(envelope) ? 'Chưa ghi nhận sự cố gần vị trí này.' : 'Chưa có dữ liệu sự cố.'}</p>}
    {incidents.length > 0 && <p className="route-inspection-note">Vị trí gần đường; chưa xác định chiều đường bị ảnh hưởng.</p>}
    {envelope && usableTraffic(envelope) && <TrafficSourceLine label="Sự cố" envelope={envelope} receivedAt={receivedAt} now={now} />}
  </div>;
}
