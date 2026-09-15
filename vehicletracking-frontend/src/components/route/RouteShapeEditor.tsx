import { useEffect, useRef, useState, type RefObject } from 'react';
import L from 'leaflet';
import type { RouteDetail, RouteShapePoint, RoutingProviderName } from '../../types/route';
import { decodeRoutePolyline } from '../../services/polyline';
import { shapeRoute } from '../../services/routes';
import { formatDuration } from '../../utils/format';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';

export function RouteShapeEditor({ route, mapRef, onClose, onSaved }: {
  route: RouteDetail; mapRef: RefObject<L.Map | null>; onClose: () => void; onSaved: (route: RouteDetail) => void;
}) {
  const [points, setPoints] = useState<RouteShapePoint[]>(route.shapingPoints ?? []);
  const [preview, setPreview] = useState(route);
  const [previewKey, setPreviewKey] = useState(JSON.stringify({points, provider: route.routingProvider}));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmClose, setConfirmClose] = useState(false);
  const [copy, setCopy] = useState(false);
  const [targetProvider, setTargetProvider] = useState<RoutingProviderName>(route.routingProvider);
  const requestRef = useRef<AbortController | null>(null);
  const pendingRef = useRef(false);
  const mounted = useRef(true);
  const dirty = JSON.stringify(points) !== JSON.stringify(route.shapingPoints ?? []);
  const previewRequestKey = (provider: RoutingProviderName) => JSON.stringify({points, provider});
  const activeTargetProvider = copy ? targetProvider : route.routingProvider;
  const previewCurrent = previewKey === previewRequestKey(activeTargetProvider);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; requestRef.current?.abort(); }; }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const pane = map.getPane('routeShapeEditor') ?? map.createPane('routeShapeEditor');
    pane.style.zIndex = '620';
    const layer = L.layerGroup().addTo(map);
    const renderer = L.svg({pane: 'routeShapeEditor'});
    let finishDrag: (() => void) | null = null;
    let suppressClick = false;
    const addPoint = (destinationStopSequence: number, position: L.LatLng) => {
      if (busy || points.length >= 20) return;
      setPoints(current => {
        const next = [...current];
        let at = next.findIndex(p => p.destinationStopSequence > destinationStopSequence);
        if (at < 0) at = next.length;
        next.splice(at, 0, {destinationStopSequence, latitude: position.lat, longitude: position.lng});
        return next;
      });
    };
    for (const section of preview.sections) {
      let line: [number, number][];
      try { line = decodeRoutePolyline(section.encodedPolyline, section.polylineEncoding); } catch { continue; }
      L.polyline(line, {renderer, color: '#174ea6', weight: 9, interactive: false}).addTo(layer);
      const path = L.polyline(line, {renderer, color: '#4285f4', weight: 5, bubblingMouseEvents: false}).addTo(layer);
      path.bindTooltip('Bấm để thêm điểm dẫn đường, rồi kéo điểm tới đường muốn đi', {sticky: true});
      path.on('click', (event: L.LeafletMouseEvent) => {
        if (!suppressClick) addPoint(section.destinationStopSequence, event.latlng);
        suppressClick = false;
      });
      path.on('mousedown', (event: L.LeafletMouseEvent) => {
        if (busy || points.length >= 20 || event.originalEvent.button !== 0) return;
        const enabled = map.dragging.enabled();
        map.dragging.disable();
        const start = event.containerPoint;
        const ghost = L.circleMarker(event.latlng, {pane:'routeShapeEditor', radius:8, color:'#1967d2', fillColor:'#fff', fillOpacity:1}).addTo(layer);
        let moved = false;
        const move = (e: MouseEvent) => {
          moved ||= map.mouseEventToContainerPoint(e).distanceTo(start) > 4;
          ghost.setLatLng(map.mouseEventToLatLng(e));
        };
        const cleanup = () => {
          document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up);
          if (enabled) map.dragging.enable(); ghost.remove(); finishDrag = null;
        };
        const up = (e: MouseEvent) => {
          cleanup();
          if (moved) { suppressClick = true; addPoint(section.destinationStopSequence, map.mouseEventToLatLng(e)); }
        };
        finishDrag?.(); finishDrag = cleanup;
        document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
      });
    }
    points.forEach((point, index) => {
      const marker = L.marker([point.latitude, point.longitude], {
        draggable: !busy, keyboard: true, pane: 'routeShapeEditor', zIndexOffset: 1000,
        icon: L.divIcon({className: 'route-shape-handle', html: `<span>${index + 1}</span>`, iconSize: [28, 28], iconAnchor: [14, 14]}),
        title: `Kéo điểm dẫn đường ${index + 1}`,
      }).addTo(layer);
      marker.bindTooltip(`Điểm ${index + 1} · Kéo để đổi đường đi`);
      marker.on('dragend', () => {
        const position = marker.getLatLng();
        setPoints(current => current.map((p, i) => i === index ? {...p, latitude: position.lat, longitude: position.lng} : p));
      });
    });
    for (const stop of route.stops) {
      L.circleMarker([stop.latitude, stop.longitude], {pane: 'routeShapeEditor', radius: 7, color: '#fff', fillColor: '#00875a', fillOpacity: 1})
        .bindTooltip(`Trạm ${stop.sequenceNumber}: ${stop.stationName}`).addTo(layer);
    }
    return () => { finishDrag?.(); layer.eachLayer(item => item.off()); layer.clearLayers(); layer.remove(); renderer.remove(); };
  }, [mapRef, preview, route.stops, points, busy]);

  const execute = async (action: 'preview' | 'save' | 'copy') => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    const controller = new AbortController(); requestRef.current = controller;
    const provider = action === 'copy' ? targetProvider : activeTargetProvider;
    const key = previewRequestKey(provider); setBusy(true); setError(null);
    try {
      const result = await shapeRoute(route.id, points, action, controller.signal, provider);
      if (!mounted.current) return;
      if (action === 'preview') { setPreview(result); setPreviewKey(key); }
      else onSaved(result);
    } catch (err) {
      if (mounted.current && !controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không thể tính lại tuyến.');
    } finally { pendingRef.current = false; if (mounted.current) setBusy(false); }
  };

  return <aside className="route-drawer" aria-label="Chỉnh đường đi trên bản đồ">
    <div className="route-drawer-header"><h3>Chỉnh đường đi</h3><button type="button" className="drawer-close-btn" disabled={busy}
      onClick={() => dirty ? setConfirmClose(true) : onClose()} aria-label="Đóng">×</button></div>
    <div className="route-drawer-body">
      <p>Kéo đoạn tuyến màu xanh đến đường muốn đi, hoặc bấm tuyến để thêm điểm trắng rồi kéo điểm. Sau đó bấm <strong>Tính lại tuyến</strong>.</p>
      <p>Trạm dừng được giữ nguyên. Các điểm dẫn đường chỉ chọn đường xe đi qua.</p>
      <p>{(preview.totalDistanceMeters / 1000).toFixed(2)} km · {formatDuration(preview.estimatedTripDurationSeconds)}
        {!previewCurrent && ' · Cần tính lại sau khi kéo điểm'}</p>
      {error && <div className="route-error-banner" role="alert">{error}</div>}
      <ol className="route-shape-points">{points.map((p, index) => <li key={index}>
        <span>Điểm {index + 1} · trước trạm {p.destinationStopSequence}</span>
        <button type="button" className="btn-secondary" disabled={busy || index === 0 || points[index - 1].destinationStopSequence !== p.destinationStopSequence}
          aria-label={`Đưa điểm ${index + 1} lên trước`} onClick={() => setPoints(current => { const next = [...current]; [next[index - 1], next[index]] = [next[index], next[index - 1]]; return next; })}>↑</button>
        <button type="button" className="btn-secondary" disabled={busy} aria-label={`Xóa điểm dẫn đường ${index + 1}`}
          onClick={() => setPoints(current => current.filter((_, i) => i !== index))}>Xóa</button>
      </li>)}</ol>
      {!points.length && <p>Chưa có điểm dẫn đường. Bạn có thể kéo bản đồ để tìm vị trí.</p>}
      <button type="button" className="btn-primary" disabled={busy || previewCurrent} onClick={() => void execute('preview')}>
        {busy ? 'Đang tính tuyến…' : 'Tính lại tuyến'}</button>
      <label className="route-shape-copy"><input type="checkbox" checked={copy} disabled={busy} onChange={e => setCopy(e.target.checked)} />Lưu thành tuyến mới</label>
      {copy && <label className="form-field"><span className="form-label">Nhà cung cấp cho tuyến mới</span>
        <select className="form-input" value={targetProvider} disabled={busy}
          onChange={event => { setTargetProvider(event.target.value as RoutingProviderName); setPreviewKey(''); }}>
          <option value="GOOGLE">Google Routes (hình học và ETA Google)</option>
          <option value="HERE">HERE Routing</option>
        </select>
      </label>}
      <p className="panel-help">Nếu tuyến đã có chuyến đi, hãy lưu thành tuyến mới rồi chọn tuyến đó khi tạo chuyến.</p>
    </div>
    <div className="route-drawer-footer">
      <button type="button" className="btn-secondary" disabled={busy} onClick={() => dirty ? setConfirmClose(true) : onClose()}>Hủy</button>
      <button type="button" className="btn-primary" disabled={busy || !previewCurrent || (!dirty && !copy)} onClick={() => void execute(copy ? 'copy' : 'save')}>Lưu tuyến</button>
    </div>
    {confirmClose && <FleetConfirmDialog title="Bỏ thay đổi đường đi?" message="Các điểm bạn vừa kéo chưa được lưu." confirmLabel="Bỏ thay đổi"
      busy={false} error={null} onClose={() => setConfirmClose(false)} onConfirm={onClose} />}
  </aside>;
}
