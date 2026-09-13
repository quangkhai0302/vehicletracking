import { useState, type FormEvent } from 'react';
import { ArrowLeft, Save } from 'lucide-react';
import type { FleetVehicle, VehicleInput } from '../../types/fleet';
import { FleetConfirmDialog } from './FleetConfirmDialog';

export function VehicleEditor({ vehicle, busy, error, onSave, onClose }: {
  vehicle: FleetVehicle | null; busy: boolean; error: string | null;
  onSave: (input: VehicleInput, id?: number) => Promise<boolean>; onClose: () => void;
}) {
  const [plateNumber, setPlateNumber] = useState(vehicle?.plateNumber ?? '');
  const [name, setName] = useState(vehicle?.name ?? '');
  const [description, setDescription] = useState(vehicle?.description ?? '');
  const [confirm, setConfirm] = useState(false);
  const dirty = plateNumber !== (vehicle?.plateNumber ?? '') || name !== (vehicle?.name ?? '') || description !== (vehicle?.description ?? '');
  const close = () => { if (dirty) setConfirm(true); else onClose(); };
  const normalized = plateNumber.toUpperCase().replace(/[\s.-]/g, '');
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (busy || !name.trim() || !/^[A-Z0-9]{1,20}$/.test(normalized)) return;
    void onSave({ plateNumber, name: name.trim(), description: description.trim() || null }, vehicle?.id);
  };
  return <section className="fleet-editor" aria-label={vehicle ? 'Chỉnh sửa xe' : 'Thêm xe mới'}>
    <div className="fleet-heading"><button className="fleet-icon-button" aria-label="Đóng biểu mẫu xe" onClick={close} disabled={busy}><ArrowLeft size={18} /></button>
      <div><span className="panel-eyebrow">DANH MỤC PHƯƠNG TIỆN</span><h2>{vehicle ? 'Chỉnh sửa xe' : 'Thêm xe mới'}</h2></div></div>
    <form id="vehicle-form" className="fleet-form-body" onSubmit={submit}><fieldset disabled={busy}>
      {error && <p role="alert" className="fleet-error">{error}</p>}
      <label>Biển số *<input name="plateNumber" value={plateNumber} maxLength={20} required pattern="[A-Za-z0-9 .\-]+" placeholder="Ví dụ: 51B-123.45" onChange={e => setPlateNumber(e.target.value)} /></label>
      <p className="fleet-help">Biển số được lưu viết hoa, bỏ khoảng trắng, dấu chấm và gạch nối.</p>
      <label>Tên xe *<input name="name" value={name} maxLength={100} required placeholder="Ví dụ: Xe buýt 01" onChange={e => setName(e.target.value)} /></label>
      <label>Mô tả<textarea name="description" value={description} maxLength={255} rows={3} placeholder="Thông tin nhận diện xe" onChange={e => setDescription(e.target.value)} /></label>
      <p className="availability-note">Vị trí và vận tốc sẽ hiển thị khi có nguồn dữ liệu xe.</p>
    </fieldset></form>
    <div className="fleet-footer"><button className="btn-secondary" disabled={busy} onClick={close}>Hủy</button>
      <button className="btn-primary" type="submit" form="vehicle-form" disabled={busy || !name.trim() || !/^[A-Z0-9]{1,20}$/.test(normalized)}><Save size={15} />{busy ? 'Đang lưu…' : vehicle ? 'Lưu xe' : 'Thêm xe'}</button></div>
    {confirm && <FleetConfirmDialog title="Bỏ thay đổi của xe?" message="Thông tin chưa lưu sẽ bị xóa." confirmLabel="Bỏ thay đổi" busy={false} onConfirm={onClose} onClose={() => setConfirm(false)} />}
  </section>;
}
