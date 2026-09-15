import { useState } from 'react';
import { BellOff, CircleCheck, Route, Check, Trash2, CheckCheck } from 'lucide-react';
import type { NotificationItem } from '../../types/notifications';
import { deleteNotification, markAllNotificationsRead, markNotificationRead } from '../../services/notifications';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';

interface Props { notifications: NotificationItem[]; }

export function AlertStream({ notifications }: Props) {
  const [readIds, setReadIds] = useState<Set<number>>(() => new Set());
  const [deletedIds, setDeletedIds] = useState<Set<number>>(() => new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<NotificationItem | null>(null);
  const items = notifications.filter(item => !deletedIds.has(item.id)).map(item => readIds.has(item.id) && !item.readAt ? { ...item, readAt: new Date().toISOString() } : item);
  const unread = items.filter(item => !item.readAt).length;
  const read = async (id: number) => {
    if (busy) return; setBusy(true); setError(null);
    try {
      const updated = await markNotificationRead(id);
      setReadIds(current => new Set(current).add(updated.id));
    } catch (err) { setError(err instanceof Error ? err.message : 'Không thể cập nhật thông báo.'); }
    finally { setBusy(false); }
  };
  const readAll = async () => { if (busy || unread === 0) return; setBusy(true); setError(null); try { await markAllNotificationsRead(); setReadIds(current => new Set([...current, ...items.map(item => item.id)])); } catch (err) { setError(err instanceof Error ? err.message : 'Không thể đánh dấu thông báo.'); } finally { setBusy(false); } };
  const remove = async () => { if (!confirmDelete || busy) return; setBusy(true); setError(null); try { await deleteNotification(confirmDelete.id); setDeletedIds(current => new Set(current).add(confirmDelete.id)); setConfirmDelete(null); } catch (err) { setError(err instanceof Error ? err.message : 'Không thể xóa thông báo.'); } finally { setBusy(false); } };
  return <section className="alert-stream-content" aria-label="Luồng cảnh báo">
    <div className="source-label"><span className="status-dot" /> {unread} chưa đọc <span className="alert-actions"><button className="fleet-text-button" disabled={busy || unread === 0} onClick={() => void readAll()}><CheckCheck size={13} />Đọc tất cả</button></span></div>
    {error && <div className="fleet-error" role="alert">{error}</div>}
    {items.length === 0 ? <div className="alerts-empty"><BellOff size={24} /><strong>Chưa có cảnh báo</strong><p>Hệ thống sẽ thông báo khi phát hiện đường đóng hoặc trễ giao thông cần đổi tuyến.</p></div> :
      <div className="alert-list">{items.slice(0, 20).map(item => <article key={item.id} className={`alert-item ${item.readAt ? 'read' : 'unread'}`}>
        <div className="alert-item-icon">{item.type === 'REROUTE_CREATED' ? <Route size={15} /> : <BellOff size={15} />}</div>
        <div className="alert-item-body"><strong>{item.title}</strong><span>Chuyến #{item.tripId} · {item.vehiclePlateNumber} · {item.severity === 'CRITICAL' ? 'Nghiêm trọng' : 'Cao'}</span><p>{item.reason}</p></div>
        {!item.readAt && <button className="alert-read-button" disabled={busy} onClick={() => void read(item.id)} aria-label="Đánh dấu đã đọc"><Check size={14} /></button>}
        <button className="alert-read-button" disabled={busy} onClick={() => setConfirmDelete(item)} aria-label="Xóa thông báo"><Trash2 size={14} /></button>
      </article>)}</div>}
    <div className="alert-legend"><span><CircleCheck size={13} /> Check-in · Chi tiết chuyến</span><span><Route size={13} /> Đổi tuyến tự động</span></div>
    {confirmDelete && <FleetConfirmDialog title="Xóa thông báo?" message="Thông báo sẽ bị xóa khỏi danh sách. Dữ liệu chuyến và revision không bị ảnh hưởng." confirmLabel="Xác nhận xóa" busy={busy} error={error} onClose={() => setConfirmDelete(null)} onConfirm={() => { void remove(); }} />}
  </section>;
}
