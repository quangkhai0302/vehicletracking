import { useState, type FormEvent } from 'react';
import {
  AlertCircle,
  Crosshair,
  Edit3,
  Save,
  Sliders,
  Trash2,
  X,
} from 'lucide-react';
import type { Station, StationFormMode, StationFormState, StationInput } from '../types/station';

const RADIUS_PRESETS = [30, 50, 100, 200, 500];

interface StationDrawerProps {
  station: Station | null;
  mode: StationFormMode;
  form: StationFormState;
  saving: boolean;
  error: string | null;
  pickingLocation: boolean;
  onClose: () => void;
  onBeginEdit: () => void;
  onPickLocation: () => void;
  onFieldChange: (field: keyof StationFormState, value: string) => void;
  onSave: (input: StationInput) => Promise<void>;
  onRequestDeactivate: () => void;
}

export function StationDrawer({
  station,
  mode,
  form,
  saving,
  error,
  pickingLocation,
  onClose,
  onBeginEdit,
  onPickLocation,
  onFieldChange,
  onSave,
  onRequestDeactivate,
}: StationDrawerProps) {
  const isFormOpen = mode !== 'closed';
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

  // Radius parsing and validation
  const parsedRadius = Number(form.checkinRadiusMeters);
  const isRadiusValid =
    Number.isInteger(parsedRadius) && parsedRadius >= 10 && parsedRadius <= 1000;

  // Check if form is dirty by comparing with original snapshot (F-01)
  const isDirty =
    mode === 'create'
      ? Boolean(form.name.trim()) ||
        Boolean(form.address.trim()) ||
        Boolean(form.latitude.trim()) ||
        Boolean(form.longitude.trim()) ||
        (form.checkinRadiusMeters.trim() !== '' && form.checkinRadiusMeters.trim() !== '50')
      : mode === 'edit' && station
        ? form.name.trim() !== station.name.trim() ||
          (form.address.trim() || '') !== (station.address?.trim() || '') ||
          form.latitude.trim() !== String(station.latitude) ||
          form.longitude.trim() !== String(station.longitude) ||
          form.checkinRadiusMeters.trim() !== String(station.checkinRadiusMeters)
        : false;

  const handleSafeClose = () => {
    if (isFormOpen && isDirty) {
      setShowDiscardConfirm(true);
    } else {
      onClose();
    }
  };

  const handleConfirmDiscard = () => {
    setShowDiscardConfirm(false);
    onClose();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isRadiusValid) return;

    await onSave({
      name: form.name.trim(),
      address: form.address.trim() || null,
      latitude: Number(form.latitude),
      longitude: Number(form.longitude),
      checkinRadiusMeters: parsedRadius,
    });
  };

  const sliderValue = Number.isFinite(parsedRadius)
    ? Math.min(1000, Math.max(10, parsedRadius))
    : 50;

  if (!isFormOpen && !station) return null;

  return (
    <aside className="station-drawer" aria-label={isFormOpen ? 'Biểu mẫu trạm' : 'Chi tiết trạm'}>
      {/* Header */}
      <div className="drawer-header">
        <div>
          <div className="drawer-eyebrow-row">
            <span className="panel-eyebrow">
              {mode === 'create' ? 'Thiết lập trạm mới' : mode === 'edit' ? 'Cập nhật trạm' : 'Thông tin trạm'}
            </span>
            <span className={`mode-badge ${mode === 'create' ? 'create' : mode === 'edit' ? 'edit' : 'browse'}`}>
              {mode === 'create' ? 'CREATE' : mode === 'edit' ? 'EDIT' : 'BROWSE'}
            </span>
          </div>
          <h2>{mode === 'create' ? 'Tạo trạm mới' : mode === 'edit' ? 'Chỉnh sửa trạm' : station?.name}</h2>
        </div>
        <button
          type="button"
          className="icon-action"
          onClick={handleSafeClose}
          disabled={saving}
          aria-label="Đóng panel"
          title="Đóng"
        >
          <X size={18} />
        </button>
      </div>

      {error && <div className="station-alert drawer-alert" role="alert">{error}</div>}

      {/* Confirmation khi hủy form dở dang */}
      {showDiscardConfirm && (
        <div className="inline-discard-alert" role="alert">
          <AlertCircle size={16} />
          <div>
            <strong>Hủy các thay đổi chưa lưu?</strong>
            <p>Nội dung bạn đang nhập sẽ bị mất nếu đóng lúc này.</p>
          </div>
          <div className="discard-actions">
            <button
              type="button"
              className="discard-stay-btn"
              onClick={() => setShowDiscardConfirm(false)}
            >
              Ở lại
            </button>
            <button
              type="button"
              className="discard-confirm-btn"
              onClick={handleConfirmDiscard}
            >
              Hủy thay đổi
            </button>
          </div>
        </div>
      )}

      {/* Mode BROWSE: Xem chi tiết trạm */}
      {!isFormOpen && station && (
        <div className="station-details">
          <div className="station-detail-header">
            <div>
              <h3 className="station-detail-name">{station.name}</h3>
              <div className="station-detail-sub">
                <span className="active-status"><span /> Đang hoạt động</span>
                <span className="station-detail-id">Mã hệ thống #{station.id}</span>
              </div>
            </div>
          </div>

          <div className="station-property-list">
            <div className="station-property-group">
              <span className="station-property-label">ĐỊA CHỈ HOẠT ĐỘNG</span>
              <p className="station-property-value">{station.address || 'Chưa có địa chỉ mô tả cụ thể'}</p>
            </div>

            <div className="station-property-group">
              <span className="station-property-label">TỌA ĐỘ VỊ TRÍ</span>
              <p className="station-property-value tabular-numbers">
                {station.latitude.toFixed(6)}, {station.longitude.toFixed(6)}
              </p>
            </div>

            <div className="station-property-group">
              <span className="station-property-label">BÁN KÍNH CHECK-IN (GEOFENCE)</span>
              <p className="station-property-value tabular-numbers">{station.checkinRadiusMeters} mét</p>
            </div>

            <div className="station-property-group">
              <span className="station-property-label">LẦN CẬP NHẬT GẦN NHẤT</span>
              <p className="station-property-value tabular-numbers">
                {new Date(station.updatedAt).toLocaleString('vi-VN')}
              </p>
            </div>
          </div>

          <div className="drawer-actions">
            <button
              type="button"
              className="secondary-action danger"
              onClick={onRequestDeactivate}
            >
              <Trash2 size={15} /> Ngừng sử dụng
            </button>
            <button
              type="button"
              className="primary-action"
              onClick={onBeginEdit}
            >
              <Edit3 size={15} /> Chỉnh sửa
            </button>
          </div>
        </div>
      )}

      {/* Mode CREATE / EDIT: Biểu mẫu form */}
      {isFormOpen && (
        <form className="station-form" onSubmit={handleSubmit}>
          {/* Vùng hướng dẫn chọn vị trí trên map */}
          <div className={`location-picker-status ${pickingLocation ? 'picking' : ''}`}>
            <Crosshair size={18} />
            <div>
              <strong>{pickingLocation ? 'Đang chờ bạn nhấp bản đồ' : 'Vị trí tọa độ trạm'}</strong>
              <span>
                {pickingLocation
                  ? 'Nhấp chuột lên bản đồ hoặc dùng nút "Lấy tâm bản đồ" ở góc dưới.'
                  : form.latitude && form.longitude
                    ? 'Có thể kéo thả trực tiếp marker trên bản đồ để tinh chỉnh.'
                    : 'Chưa xác định tọa độ cho trạm.'}
              </span>
            </div>
            <button
              type="button"
              onClick={onPickLocation}
              disabled={saving}
              aria-label="Chọn lại vị trí trên bản đồ"
            >
              {form.latitude ? 'Chọn lại' : 'Chọn vị trí'}
            </button>
          </div>

          {/* Trường Tên trạm */}
          <label className="station-field">
            <span>Tên trạm đón trả khách *</span>
            <input
              value={form.name}
              onChange={(e) => onFieldChange('name', e.target.value)}
              maxLength={150}
              required
              placeholder="Ví dụ: Bến xe Miền Đông, Ngã tư Hàng Xanh"
            />
          </label>

          {/* Trường Địa chỉ */}
          <label className="station-field">
            <span>Địa chỉ mô tả</span>
            <input
              value={form.address}
              onChange={(e) => onFieldChange('address', e.target.value)}
              maxLength={255}
              placeholder="Ví dụ: 292 Đinh Bộ Lĩnh, Phường 26, Bình Thạnh"
            />
          </label>

          {/* Trường Tọa độ */}
          <div className="station-field-row">
            <label className="station-field">
              <span>Vĩ độ (Latitude) *</span>
              <input
                type="number"
                className="tabular-numbers"
                value={form.latitude}
                onChange={(e) => onFieldChange('latitude', e.target.value)}
                min={-90}
                max={90}
                step="0.000001"
                required
                placeholder="10.814387"
              />
            </label>
            <label className="station-field">
              <span>Kinh độ (Longitude) *</span>
              <input
                type="number"
                className="tabular-numbers"
                value={form.longitude}
                onChange={(e) => onFieldChange('longitude', e.target.value)}
                min={-180}
                max={180}
                step="0.000001"
                required
                placeholder="106.711822"
              />
            </label>
          </div>

          {/* Bộ chọn bán kính check-in thông minh (Presets, Ô nhập số 10..1000m & Slider) */}
          <div className="radius-selector-card">
            <div className="radius-selector-header">
              <div className="radius-label-row">
                <Sliders size={14} className="text-cyan" />
                <span>BÁN KÍNH CHECK-IN (GEOFENCE) *</span>
              </div>
              <div className="radius-input-wrapper">
                <input
                  type="number"
                  className="radius-number-input tabular-numbers"
                  min={10}
                  max={1000}
                  step={1}
                  value={form.checkinRadiusMeters}
                  onChange={(e) => onFieldChange('checkinRadiusMeters', e.target.value)}
                  aria-label="Bán kính check-in tính bằng mét"
                  required
                />
                <span className="radius-unit">m</span>
              </div>
            </div>

            {/* Nút Preset chọn nhanh */}
            <div className="radius-presets-row" role="group" aria-label="Các mốc bán kính nhanh">
              {RADIUS_PRESETS.map((p) => (
                <button
                  key={p}
                  type="button"
                  className={`preset-btn ${parsedRadius === p ? 'active' : ''}`}
                  onClick={() => onFieldChange('checkinRadiusMeters', String(p))}
                  aria-pressed={parsedRadius === p}
                >
                  {p}m
                </button>
              ))}
            </div>

            {/* Slider tương tác 10..1000m */}
            <div className="radius-slider-box">
              <input
                type="range"
                className="radius-range-slider"
                min={10}
                max={1000}
                step={5}
                value={sliderValue}
                onChange={(e) => onFieldChange('checkinRadiusMeters', e.target.value)}
                aria-label="Thanh trượt điều chỉnh bán kính"
              />
              <div className="slider-ticks">
                <span>10m</span>
                <span>100m</span>
                <span>300m</span>
                <span>500m</span>
                <span>1000m</span>
              </div>
            </div>

            {!isRadiusValid && form.checkinRadiusMeters.trim() !== '' && (
              <span className="radius-error-text" role="alert">
                Bán kính check-in hợp lệ từ 10 đến 1.000 mét.
              </span>
            )}

            <span className="radius-helper-text">
              Vòng tròn geofence trên bản đồ sẽ hiển thị vùng nhận diện phương tiện tự động (10 – 1.000m).
            </span>
          </div>

          {/* Nút hành động */}
          <div className="drawer-actions">
            <button
              type="button"
              className="secondary-action"
              onClick={handleSafeClose}
              disabled={saving}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="primary-action"
              disabled={
                saving ||
                !form.name.trim() ||
                !form.latitude ||
                !form.longitude ||
                !isRadiusValid
              }
            >
              <Save size={15} /> {saving ? 'Đang lưu…' : mode === 'create' ? 'Tạo trạm' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      )}
    </aside>
  );
}
