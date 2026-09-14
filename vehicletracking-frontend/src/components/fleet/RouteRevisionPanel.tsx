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
  return <section className="trip-revision-panel" aria-label="Phiên bản tuyến">
    <div className="trip-section-heading"><span><GitBranch size={15} /> Phiên bản tuyến</span><button className="fleet-icon-button" onClick={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} disabled={loading} aria-label="Tải lại phiên bản"><RefreshCw size={14} /></button></div>
    {error && <div className="fleet-error" role="alert">{error}</div>}{loading && <p className="fleet-loading">Đang tải phiên bản…</p>}
    {!loading && !error && revisions.length === 0 && <p className="fleet-help">Chuyến này chưa có phiên bản đổi tuyến.</p>}
    {!loading && !error && revisions.map(item => <article className="trip-revision-row" key={item.id}><div><strong>Revision {item.revisionNumber}</strong><span className={`revision-status ${item.status.toLowerCase()}`}>{item.status === 'ACTIVE' ? 'Đang hiệu lực' : 'Đã supersede'}</span></div><p>{item.reasonDetail || (item.reasonCode === 'ROAD_CLOSURE' ? 'Đường bị đóng' : 'Chậm giao thông')}</p><small>{displayTripTime(item.createdAt)} · Còn lại {Math.ceil(item.revisedRemainingSeconds / 60)} phút</small>{item.status === 'ACTIVE' && <button className="fleet-text-button" disabled={busy} onClick={() => setConfirm(item)}>Ngừng hiệu lực</button>}</article>)}
    {confirm && <FleetConfirmDialog title={`Ngừng hiệu lực revision ${confirm.revisionNumber}?`} message="Thao tác chỉ đóng phiên bản hiện tại, không tạo tuyến mới và không xóa lịch sử." confirmLabel="Xác nhận supersede" busy={busy} error={error} onClose={() => setConfirm(null)} onConfirm={() => { void supersede(); }} />}
  </section>;
}
