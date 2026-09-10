import type { FormEvent } from 'react';
import { Edit3, MapPin, Plus, Save, Trash2, X } from 'lucide-react';
import type { Station, StationFormMode, StationFormState, StationInput } from '../types/station';

interface StationPanelProps {
  stations: Station[];
  loading: boolean;
  saving: boolean;
  error: string | null;
  mode: StationFormMode;
  form: StationFormState;
  onBeginCreate: () => void;
  onBeginEdit: (station: Station) => void;
  onCancel: () => void;
  onFieldChange: (field: keyof StationFormState, value: string) => void;
  onSave: (input: StationInput) => Promise<void>;
  onDelete: (station: Station) => void;
  onFocus: (station: Station) => void;
}

export function StationPanel({
  stations,
  loading,
  saving,
  error,
  mode,
  form,
  onBeginCreate,
  onBeginEdit,
  onCancel,
  onFieldChange,
  onSave,
  onDelete,
  onFocus,
}: StationPanelProps) {
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSave({
      name: form.name.trim(),
      address: form.address.trim() || null,
      latitude: Number(form.latitude),
      longitude: Number(form.longitude),
      checkinRadiusMeters: Number(form.checkinRadiusMeters),
    });
  };

  return (
    <aside className="station-panel" aria-label="Quản lý trạm">
      <div className="station-panel-header">
        <div>
          <div className="station-panel-eyebrow">Điểm trên bản đồ</div>
          <h2>Quản lý trạm</h2>
        </div>
        {mode === 'closed' && (
          <button className="station-primary-btn" onClick={onBeginCreate}>
            <Plus size={16} /> Thêm trạm
          </button>
        )}
      </div>

      {error && <div className="station-alert">{error}</div>}

      {mode !== 'closed' && (
        <form className="station-form" onSubmit={handleSubmit}>
          <div className="station-form-title">
            <span>{mode === 'create' ? 'Trạm mới' : 'Chỉnh sửa trạm'}</span>
            <button type="button" className="station-icon-btn" onClick={onCancel} title="Đóng biểu mẫu">
              <X size={16} />
            </button>
          </div>

          <p className="station-form-hint">
            Nhấp lên bản đồ để chọn hoặc thay đổi tọa độ.
          </p>

          <label className="station-field">
            <span>Tên trạm *</span>
            <input
              value={form.name}
              onChange={(event) => onFieldChange('name', event.target.value)}
              maxLength={150}
              required
              placeholder="Ví dụ: Bến xe Miền Đông"
            />
          </label>

          <label className="station-field">
            <span>Địa chỉ</span>
            <input
              value={form.address}
              onChange={(event) => onFieldChange('address', event.target.value)}
              maxLength={255}
              placeholder="Địa chỉ mô tả của trạm"
            />
          </label>

          <div className="station-field-row">
            <label className="station-field">
              <span>Vĩ độ *</span>
              <input
                type="number"
                value={form.latitude}
                onChange={(event) => onFieldChange('latitude', event.target.value)}
                min={-90}
                max={90}
                step="0.000001"
                required
              />
            </label>
            <label className="station-field">
              <span>Kinh độ *</span>
              <input
                type="number"
                value={form.longitude}
                onChange={(event) => onFieldChange('longitude', event.target.value)}
                min={-180}
                max={180}
                step="0.000001"
                required
              />
            </label>
          </div>

          <label className="station-field">
            <span>Bán kính check-in (m) *</span>
            <input
              type="number"
              value={form.checkinRadiusMeters}
              onChange={(event) => onFieldChange('checkinRadiusMeters', event.target.value)}
              min={10}
              max={1000}
              required
            />
          </label>

          <div className="station-form-actions">
            <button type="button" className="station-secondary-btn" onClick={onCancel} disabled={saving}>
              Hủy
            </button>
            <button type="submit" className="station-primary-btn" disabled={saving}>
              <Save size={15} /> {saving ? 'Đang lưu…' : 'Lưu trạm'}
            </button>
          </div>
        </form>
      )}

      <div className="station-list" aria-live="polite">
        {loading && <div className="station-empty">Đang tải danh sách trạm…</div>}
        {!loading && stations.length === 0 && (
          <div className="station-empty">
            <MapPin size={24} />
            <span>Chưa có trạm nào.</span>
            <small>Chọn “Thêm trạm”, sau đó nhấp lên bản đồ.</small>
          </div>
        )}
        {!loading && stations.map((station) => (
          <article className="station-list-item" key={station.id}>
            <button className="station-summary" onClick={() => onFocus(station)}>
              <span className="station-marker-dot" />
              <span>
                <strong>{station.name}</strong>
                <small>{station.address || `${station.latitude}, ${station.longitude}`}</small>
                <small>Bán kính: {station.checkinRadiusMeters} m</small>
              </span>
            </button>
            <div className="station-item-actions">
              <button className="station-icon-btn" onClick={() => onBeginEdit(station)} title="Sửa trạm">
                <Edit3 size={15} />
              </button>
              <button className="station-icon-btn danger" onClick={() => onDelete(station)} title="Xóa trạm">
                <Trash2 size={15} />
              </button>
            </div>
          </article>
        ))}
      </div>
    </aside>
  );
}
