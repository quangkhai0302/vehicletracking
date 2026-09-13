import { useEffect, useRef } from 'react';
import type { Station } from '../../types/station';

export function ConfirmStationDelete({ station, saving, onCancel, onConfirm }: {
  station: Station; saving: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = dialogRef.current; dialog?.showModal(); return () => dialog?.close(); }, []);
  return <dialog ref={dialogRef} className="confirmation-dialog" aria-labelledby="deactivate-title" onCancel={event => { event.preventDefault(); if (!saving) onCancel(); }}>
    <div className="dialog-icon" aria-hidden="true">!</div>
    <h2 id="deactivate-title">Ngừng sử dụng trạm?</h2>
    <div className="dialog-station-info"><strong>{station.name}</strong><p>{station.address}</p></div>
    <p className="dialog-explanation">Trạm sẽ không được dùng cho tuyến mới. Dữ liệu lịch sử vẫn được lưu trữ.</p>
    <div className="dialog-actions"><button className="secondary-action" onClick={onCancel} disabled={saving} autoFocus>Hủy bỏ</button><button className="danger-action" onClick={onConfirm} disabled={saving}>{saving ? 'Đang xử lý…' : 'Xác nhận ngừng sử dụng'}</button></div>
  </dialog>;
}
