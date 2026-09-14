import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, History, RefreshCw } from 'lucide-react';
import { fetchTelemetryHistory, type TelemetryHistoryQuery } from '../../services/telemetry';
import type { TelemetryPage } from '../../types/telemetry';
import { displayTripTime } from '../../utils/tripTime';

export function TelemetryHistoryPanel({ tripId, onFocusPosition }: { tripId: number; onFocusPosition: (position: [number, number]) => void }) {
  const [page, setPage] = useState<TelemetryPage | null>(null);
  const [source, setSource] = useState<TelemetryHistoryQuery['source']>();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    const query: TelemetryHistoryQuery = { tripId, source, page: pageNumber, size: 20 };
    if (from) query.from = new Date(from).toISOString();
    if (to) query.to = new Date(to).toISOString();
    fetchTelemetryHistory(query, controller.signal).then(setPage).catch((err: unknown) => {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không thể tải lịch sử vị trí.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tripId, source, from, to, pageNumber, attempt]);
  const applyFilter = (setter: (value: string) => void, value: string) => { setLoading(true); setError(null); setter(value); setPageNumber(0); };
  return <section className="trip-history-panel" aria-label="Lịch sử vị trí">
    <div className="trip-section-heading"><span><History size={15} /> Lịch sử vị trí</span><button className="fleet-icon-button" onClick={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} disabled={loading} aria-label="Tải lại lịch sử"><RefreshCw size={14} /></button></div>
    <div className="trip-history-filters">
      <select aria-label="Nguồn telemetry" value={source ?? ''} onChange={event => { setLoading(true); setError(null); setSource(event.target.value ? event.target.value as TelemetryHistoryQuery['source'] : undefined); setPageNumber(0); }}>
        <option value="">Tất cả nguồn</option><option value="GPS">GPS</option><option value="SIMULATOR">Mô phỏng</option>
      </select>
      <label>Từ <input type="datetime-local" value={from} onChange={event => applyFilter(setFrom, event.target.value)} /></label>
      <label>Đến <input type="datetime-local" value={to} onChange={event => applyFilter(setTo, event.target.value)} /></label>
    </div>
    {error && <div className="fleet-error" role="alert">{error}<button className="fleet-text-button" onClick={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }}>Thử lại</button></div>}
    {loading && <p className="fleet-loading" role="status">Đang tải lịch sử…</p>}
    {!loading && !error && page?.items.length === 0 && <p className="fleet-help">Không có mẫu telemetry phù hợp.</p>}
    {!loading && !error && page && page.items.length > 0 && <>
      <div className="telemetry-history-list">{page.items.map(item => <button className="telemetry-history-row" key={item.id} onClick={() => onFocusPosition([item.latitude, item.longitude])}>
        <span><strong>{item.source === 'SIMULATOR' ? 'Mô phỏng' : 'GPS'}</strong> · {displayTripTime(item.recordedAt)}</span><span>{item.speedKmh.toFixed(1)} km/h · {item.latitude.toFixed(5)}, {item.longitude.toFixed(5)}</span>
      </button>)}</div>
      <div className="trip-history-pagination"><span>Trang {(page.page ?? pageNumber) + 1} / {Math.max(page.totalPages, 1)} · {page.totalElements} mẫu</span><span><button className="fleet-icon-button" disabled={pageNumber <= 0 || loading} onClick={() => { setLoading(true); setPageNumber(value => value - 1); }} aria-label="Trang trước"><ChevronLeft size={14} /></button><button className="fleet-icon-button" disabled={pageNumber + 1 >= page.totalPages || loading} onClick={() => { setLoading(true); setPageNumber(value => value + 1); }} aria-label="Trang sau"><ChevronRight size={14} /></button></span></div>
    </>}
  </section>;
}
