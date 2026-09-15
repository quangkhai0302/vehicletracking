import { useCallback, useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import L from 'leaflet';
import type { RouteDetail, RouteSection } from '../../types/route';
import { decodeFlexiblePolyline } from '../../services/polyline';
import { useRouteTraffic } from '../../hooks/useRouteTraffic';
import { matchFlow, project, trafficCell, usableTraffic, validLine, type Coordinate } from '../../utils/routeInspection';
import { MapInspectionCard, NearbyTrafficIncidents, TrafficFlowDetails, TrafficSourceLine } from '../traffic/TrafficInspectionCard';
import { inspectionDistance as distance, inspectionDuration as duration, inspectionStamp as stamp } from '../../utils/inspectionFormat';

interface InspectedSection { section: RouteSection; points: Coordinate[] }
interface Selection { route: RouteDetail; item: InspectedSection; point: Coordinate; x: number; y: number; pinned: boolean }

export function RouteInspectionLayer({ mapRef, mapReady, route, visible, trafficEnabled }: {
  mapRef: RefObject<L.Map | null>; mapReady: boolean; route: RouteDetail | null; visible: boolean; trafficEnabled: boolean;
}) {
  const [selection, setSelection] = useState<Selection | null>(null);
  const [now, setNow] = useState(Date.now);
  const selectedRef = useRef<Selection | null>(null);
  const select = useCallback((value: Selection | null) => {
    selectedRef.current = value; setSelection(value); if (value) setNow(Date.now());
  }, []);
  const prepared = useMemo(() => {
    try {
      const sections = route?.sections.map(section => ({ section, points: decodeFlexiblePolyline(section.encodedPolyline) })) ?? [];
      return sections.every(item => validLine(item.points)) ? sections : [];
    } catch { return []; }
  }, [route]);
  const current = visible && selection?.route === route ? selection : null;
  const key = trafficEnabled && current ? trafficCell(current.point) : null;
  const traffic = useRouteTraffic(key);
  const open = !!current;
  useEffect(() => {
    if (!open) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [open]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || !visible || !route || !prepared.length) return;
    // A dedicated pane keeps hit targets above the route's Canvas renderer regardless of effect order.
    // Marker panes remain above this pane, so stations/vehicles/incidents retain their own interactions.
    const pane = map.getPane('routeInspectionPane') ?? map.createPane('routeInspectionPane');
    pane.style.zIndex = '460';
    pane.style.pointerEvents = 'none';
    const renderer = L.svg({ pane: 'routeInspectionPane' });
    const layer = L.layerGroup().addTo(map);
    let raf: number | null = null;
    const cleanups: (() => void)[] = [];
    const close = () => { if (raf !== null) cancelAnimationFrame(raf); raf = null; select(null); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') close(); };
    for (const item of prepared) {
      const path = L.polyline(item.points, { pane: 'routeInspectionPane', renderer, weight: 18, opacity: 0,
        className: 'route-inspection-hit', interactive: true, bubblingMouseEvents: false }).addTo(layer);
      const show = (point: Coordinate, pinned: boolean, client?: { x: number; y: number }) => {
        const position = project(point, item.points)?.point;
        if (!position) return;
        const pixel = map.latLngToContainerPoint(position), rect = map.getContainer().getBoundingClientRect();
        select({ route, item, point: position, pinned, x: client?.x ?? rect.left + pixel.x, y: client?.y ?? rect.top + pixel.y });
      };
      const hover = (event: L.LeafletMouseEvent) => {
        if (selectedRef.current?.pinned) return;
        if (raf !== null) cancelAnimationFrame(raf);
        raf = requestAnimationFrame(() => {
          raf = null;
          show([event.latlng.lat, event.latlng.lng], false,
            { x: event.originalEvent.clientX, y: event.originalEvent.clientY });
        });
      };
      path.on('mouseover', hover);
      path.on('mousemove', hover);
      path.on('mouseout', () => { if (!selectedRef.current?.pinned) close(); });
      path.on('click', (event: L.LeafletMouseEvent) => {
        if (raf !== null) cancelAnimationFrame(raf);
        raf = null;
        show([event.latlng.lat, event.latlng.lng], true,
          { x: event.originalEvent.clientX, y: event.originalEvent.clientY });
      });
      const element = path.getElement();
      if (element) {
        element.setAttribute('tabindex', '0');
        element.setAttribute('role', 'button');
        element.setAttribute('aria-label', `Thông tin tuyến ${route.name}, đoạn ${item.section.sectionSequence}`);
        element.setAttribute('data-route-section', String(item.section.sectionSequence));
        const focus = () => { if (!selectedRef.current?.pinned) show(item.points[Math.floor(item.points.length / 2)], false); };
        const blur = () => { if (!selectedRef.current?.pinned) close(); };
        const keydown = (event: Event) => {
          const keyEvent = event as KeyboardEvent;
          if (keyEvent.key === 'Enter' || keyEvent.key === ' ') {
            keyEvent.preventDefault(); keyEvent.stopPropagation();
            show(item.points[Math.floor(item.points.length / 2)], true);
          }
        };
        element.addEventListener('focus', focus); element.addEventListener('blur', blur); element.addEventListener('keydown', keydown);
        cleanups.push(() => { element.removeEventListener('focus', focus); element.removeEventListener('blur', blur); element.removeEventListener('keydown', keydown); });
      }
      cleanups.push(() => path.off());
    }
    map.on('movestart zoomstart click', close);
    window.addEventListener('keydown', escape);
    return () => {
      if (raf !== null) cancelAnimationFrame(raf);
      cleanups.forEach(cleanup => cleanup());
      map.off('movestart zoomstart click', close);
      window.removeEventListener('keydown', escape);
      layer.remove(); renderer.remove(); select(null);
    };
  }, [mapReady, mapRef, prepared, route, select, visible]);

  if (!current) return null;
  const data = traffic.data;
  const flow = trafficEnabled && data && usableTraffic(data.flow) ? matchFlow(current.point, current.item.points, data.flow!.results) : null;
  const section = current.item.section;
  const destination = current.route.stops.findIndex(stop => stop.sequenceNumber === section.destinationStopSequence);
  const from = current.route.stops[destination - 1]?.stationName ?? 'Đầu chặng';
  const to = current.route.stops[destination]?.stationName ?? 'Cuối chặng';
  return <MapInspectionCard x={current.x} y={current.y} pinned={current.pinned} kind="route" onClose={() => select(null)}>
    <strong className="route-inspection-name">{current.route.name}</strong>
    <p className="route-inspection-leg">{from} → {to} · Đoạn {section.sectionSequence}</p>
    <div className="route-inspection-baseline">Đoạn tuyến đã lưu: {distance(section.distanceMeters)} · {duration(section.travelDurationSeconds)}
      <span>Tính lúc {stamp(current.route.calculatedAt)} · Chưa gồm dừng trạm</span></div>
    {!trafficEnabled ? <p className="route-inspection-empty">Bật lớp Giao thông để xem dữ liệu tại vị trí này.</p>
      : !flow ? <p className="route-inspection-empty" role="status">{traffic.loading ? 'Đang tải giao thông tại vị trí này…'
        : data?.flowError ? 'Không tải được dữ liệu giao thông.'
          : !usableTraffic(data?.flow ?? null) ? 'Chưa có dữ liệu giao thông.' : 'Chưa có dữ liệu khớp vị trí và hướng tuyến.'}</p>
        : <TrafficFlowDetails flow={flow} />}
    {trafficEnabled && data?.flow && usableTraffic(data.flow) && <TrafficSourceLine label="Tốc độ" envelope={data.flow} receivedAt={data.receivedAt} now={now} />}
    {trafficEnabled && data && <NearbyTrafficIncidents point={current.point} envelope={data.incidents} receivedAt={data.receivedAt} now={now} failed={data.incidentError} />}
    {trafficEnabled && current.pinned && (data?.flowError || data?.incidentError) && <button className="route-inspection-retry" disabled={traffic.loading} onClick={traffic.retry}>Thử tải lại</button>}
  </MapInspectionCard>;
}
