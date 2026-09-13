import { useEffect, useRef } from 'react';

export function FleetConfirmDialog({ title, message, confirmLabel, busy, error, onConfirm, onClose }: {
  title: string; message: string; confirmLabel: string; busy: boolean; error?: string | null;
  onConfirm: () => void; onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = ref.current; dialog?.showModal(); return () => dialog?.close(); }, []);
  return <dialog ref={ref} className="confirmation-dialog fleet-confirm" aria-labelledby="fleet-confirm-title"
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <h2 id="fleet-confirm-title">{title}</h2><p>{message}</p>
    {error && <p className="inline-error" role="alert">{error}</p>}
    <div className="dialog-actions">
      <button className="btn-secondary" disabled={busy} onClick={onClose} autoFocus>Quay lại</button>
      <button className="danger-action" disabled={busy} onClick={onConfirm}>{busy ? 'Đang xử lý…' : confirmLabel}</button>
    </div>
  </dialog>;
}
