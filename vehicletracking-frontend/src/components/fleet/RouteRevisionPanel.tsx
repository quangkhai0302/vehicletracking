import { useEffect, useState } from 'react';
import { GitBranch, RefreshCw } from 'lucide-react';
import { fetchTripRevisions, supersedeTripRevision } from '../../services/notifications';
import type { RouteRevision } from '../../types/notifications';
import { FleetConfirmDialog } from './FleetConfirmDialog';
import { displayTripTime } from '../../utils/tripTime';

export function RouteRevisionPanel({ tripId }: { tripId: number }) {
  const [revisions, setRevisions] = useState<RouteRevision[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<RouteRevision | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    fetchTripRevisions(tripId, controller.signal).then(setRevisions).catch((err: unknown) => {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không thể tải phiên bản tuyến.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tripId, attempt]);
  const supersede = async () => {
    if (!confirm || busy) return;
    setBusy(true); setError(null);
    try { const updated = await supersedeTripRevision(tripId, confirm.id); setRevisions(current => current.map(item => item.id === updated.id ? updated : item)); setConfirm(null); }
    catch (err) { setError(err instanceof Error ? err.message : 'Không thể ngừng hiệu lực phiên bản tuyến.'); }
    finally { setBusy(false); }
  };
  if (!error && revisions.length === 0) return null;
  return <section className="trip-revision-panel" aria-label="Thay đổi tuyến đường">
    <div className="trip-section-heading"><span><GitBranch size={15} /> Thay đổi tuyến đường</span><button className="fleet-icon-button" onClick={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} disabled={loading} aria-label="Tải lại thay đổi tuyến"><RefreshCw size={14} /></button></div>
    {error && <div className="fleet-error" role="alert">{error}</div>}{loading && <p className="fleet-loading">Đang tải phiên bản…</p>}
    {!loading && !error && revisions.map(item => <article className="trip-revision-row" key={item.id}><div><strong>Đổi tuyến lần {item.revisionNumber}</strong><span className={`revision-status ${item.status.toLowerCase()}`}>{item.status === 'ACTIVE' ? 'Đang áp dụng' : 'Đã ngừng áp dụng'}</span></div><p>{item.reasonDetail || (item.reasonCode === 'ROAD_CLOSURE' ? 'Đường bị đóng' : 'Chậm giao thông')}</p><small>{displayTripTime(item.createdAt)} · Còn lại {Math.ceil(item.revisedRemainingSeconds / 60)} phút</small>{item.status === 'ACTIVE' && <button className="fleet-text-button" disabled={busy} onClick={() => setConfirm(item)}>Ngừng áp dụng</button>}</article>)}
    {confirm && <FleetConfirmDialog title={`Ngừng áp dụng lần đổi tuyến ${confirm.revisionNumber}?`} message="Thao tác chỉ ngừng áp dụng lần đổi tuyến này, không tạo tuyến mới và không xóa lịch sử." confirmLabel="Xác nhận ngừng áp dụng" busy={busy} error={error} onClose={() => setConfirm(null)} onConfirm={() => { void supersede(); }} />}
  </section>;
}
