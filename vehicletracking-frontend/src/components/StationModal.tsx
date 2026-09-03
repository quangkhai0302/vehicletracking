import React, { useState } from 'react';
import { X, MapPin, Check, Trash2, Edit2, AlertCircle, RotateCcw } from 'lucide-react';
import { Station, StationType } from '../types';

interface StationModalProps {
  isOpen: boolean;
  onClose: () => void;
  stations: Station[];
  onCreateStation: (data: Partial<Station>) => Promise<void>;
  onUpdateStation: (id: number, data: Partial<Station>) => Promise<void>;
  onDeleteStation: (id: number) => Promise<void>;
  pendingCoords: { lat: number; lng: number } | null;
}

interface StationModalContentProps {
  onClose: () => void;
  stations: Station[];
  onCreateStation: (data: Partial<Station>) => Promise<void>;
  onUpdateStation: (id: number, data: Partial<Station>) => Promise<void>;
  onDeleteStation: (id: number) => Promise<void>;
  pendingCoords: { lat: number; lng: number } | null;
}

function StationModalContent({
  onClose,
  stations,
  onCreateStation,
  onUpdateStation,
  onDeleteStation,
  pendingCoords,
}: StationModalContentProps) {
  const [selectedStation, setSelectedStation] = useState<Station | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [latitude, setLatitude] = useState(pendingCoords ? pendingCoords.lat.toFixed(6) : '');
  const [longitude, setLongitude] = useState(pendingCoords ? pendingCoords.lng.toFixed(6) : '');
  const [address, setAddress] = useState('');
  const [type, setType] = useState<StationType>('STOP');
  const [radius, setRadius] = useState(60);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Reset form to clean create mode
  const resetForm = () => {
    setSelectedStation(null);
    setCode('');
    setName('');
    setAddress('');
    setType('STOP');
    setRadius(60);
    setLatitude(pendingCoords ? pendingCoords.lat.toFixed(6) : '');
    setLongitude(pendingCoords ? pendingCoords.lng.toFixed(6) : '');
    setFormError(null);
    setFieldErrors({});
    setConfirmDeleteId(null);
  };

  const handleClose = () => {
    // REV-001: Không cho phép đóng modal khi mutation đang chạy
    if (isSubmitting) return;
    onClose();
  };

  const handleStartEdit = (st: Station) => {
    if (isSubmitting) return;
    setSelectedStation(st);
    setCode(st.code);
    setName(st.name);
    setLatitude(st.latitude.toString());
    setLongitude(st.longitude.toString());
    setAddress(st.address || '');
    setType(st.stationType);
    setRadius(st.radiusMeters);
    setFormError(null);
    setFieldErrors({});
    setConfirmDeleteId(null);
  };

  const handleCancelEdit = () => {
    if (isSubmitting) return;
    resetForm();
  };

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    const trimmedCode = code.trim();
    if (!trimmedCode) {
      errors.code = 'Mã trạm không được để trống';
    } else if (trimmedCode.length > 50) {
      errors.code = 'Mã trạm không được vượt quá 50 ký tự';
    }

    const trimmedName = name.trim();
    if (!trimmedName) {
      errors.name = 'Tên trạm không được để trống';
    } else if (trimmedName.length > 150) {
      errors.name = 'Tên trạm không được vượt quá 150 ký tự';
    }

    const latNum = parseFloat(latitude);
    if (isNaN(latNum) || !isFinite(latNum) || latNum < -90 || latNum > 90) {
      errors.latitude = 'Vĩ độ phải từ -90 đến 90';
    }

    const lngNum = parseFloat(longitude);
    if (isNaN(lngNum) || !isFinite(lngNum) || lngNum < -180 || lngNum > 180) {
      errors.longitude = 'Kinh độ phải từ -180 đến 180';
    }

    if (address && address.length > 255) {
      errors.address = 'Địa chỉ không được vượt quá 255 ký tự';
    }

    if (isNaN(radius) || radius < 30 || radius > 150) {
      errors.radius = 'Bán kính phải từ 30 đến 150 mét';
    }

    if (!type || !['START', 'STOP', 'END'].includes(type)) {
      errors.type = 'Loại trạm không hợp lệ';
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFormError('Vui lòng kiểm tra lại thông tin nhập.');
      return false;
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    const payload: Partial<Station> = {
      code: code.trim(),
      name: name.trim(),
      address: address.trim() || undefined,
      stationType: type,
      latitude: parseFloat(latitude),
      longitude: parseFloat(longitude),
      radiusMeters: radius,
    };

    setIsSubmitting(true);
    setFormError(null);

    try {
      if (selectedStation) {
        await onUpdateStation(selectedStation.id, payload);
        resetForm();
      } else {
        await onCreateStation(payload);
        // Thành công: đóng modal thông qua parent (sẽ xóa pendingCoords)
        onClose();
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Đã xảy ra lỗi';
      // Giữ nguyên form và hiển thị lỗi inline khi mutation thất bại (BR-005)
      setFormError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleConfirmDelete = async (id: number) => {
    setIsSubmitting(true);
    setDeletingId(id);
    setFormError(null);
    try {
      await onDeleteStation(id);
      setConfirmDeleteId(null);
      if (selectedStation && selectedStation.id === id) {
        resetForm();
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể xóa trạm';
      setFormError(msg);
    } finally {
      setIsSubmitting(false);
      setDeletingId(null);
    }
  };

  const getTypeBadge = (stType: StationType) => {
    switch (stType) {
      case 'START':
        return <span className="glass-badge emerald" style={{ fontSize: '0.65rem' }}>Trạm Đầu</span>;
      case 'END':
        return <span className="glass-badge crimson" style={{ fontSize: '0.65rem' }}>Trạm Cuối</span>;
      case 'STOP':
      default:
        return <span className="glass-badge cyan" style={{ fontSize: '0.65rem' }}>Trạm Dừng</span>;
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.65)',
        backdropFilter: 'blur(8px)',
        zIndex: 999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
      onClick={handleClose}
    >
      <div
        className="glass-panel"
        style={{
          width: '600px',
          maxHeight: '88vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          animation: 'slideInRight 0.2s ease',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-glass)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <MapPin size={20} color="#00f0ff" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>
              Quản Lý Trạm Dừng Tuyến Đường
            </h3>
          </div>
          <button
            onClick={handleClose}
            disabled={isSubmitting}
            style={{
              background: 'none',
              border: 'none',
              color: isSubmitting ? '#475569' : '#94a3b8',
              cursor: isSubmitting ? 'not-allowed' : 'pointer',
            }}
            aria-label="Đóng"
          >
            <X size={18} />
          </button>
        </div>

        <div style={{ padding: '20px', overflowY: 'auto' }}>
          {/* Thông báo lỗi inline */}
          {formError && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 14px',
                background: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid rgba(239, 68, 68, 0.35)',
                borderRadius: '8px',
                color: '#f87171',
                fontSize: '0.85rem',
                marginBottom: '16px',
              }}
            >
              <AlertCircle size={16} />
              <span>{formError}</span>
            </div>
          )}

          {/* Form thêm / sửa trạm */}
          <form onSubmit={handleSubmit} style={{ background: 'rgba(0,0,0,0.25)', padding: '16px', borderRadius: '12px', marginBottom: '20px', border: '1px solid rgba(255,255,255,0.06)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: selectedStation ? '#f59e0b' : '#00f0ff' }}>
                {selectedStation
                  ? `✎ Chỉnh Sửa Trạm: ${selectedStation.name} (${selectedStation.code})`
                  : `+ Thêm Trạm Mới ${pendingCoords ? `(${pendingCoords.lat.toFixed(4)}, ${pendingCoords.lng.toFixed(4)})` : ''}`}
              </h4>
              {selectedStation && (
                <button
                  type="button"
                  onClick={handleCancelEdit}
                  disabled={isSubmitting}
                  className="glass-btn secondary"
                  style={{ fontSize: '0.75rem', padding: '4px 8px' }}
                >
                  <RotateCcw size={12} />
                  <span>Hủy sửa</span>
                </button>
              )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Mã trạm *</label>
                <input
                  type="text"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="VD: ST-01"
                  required
                  disabled={isSubmitting}
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: fieldErrors.code ? '1px solid #ef4444' : '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
                {fieldErrors.code && <span style={{ color: '#ef4444', fontSize: '0.7rem' }}>{fieldErrors.code}</span>}
              </div>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Tên trạm dừng *</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="VD: Bến xe Miền Đông"
                  required
                  disabled={isSubmitting}
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: fieldErrors.name ? '1px solid #ef4444' : '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
                {fieldErrors.name && <span style={{ color: '#ef4444', fontSize: '0.7rem' }}>{fieldErrors.name}</span>}
              </div>
            </div>

            {/* Tọa độ rõ ràng và cho phép chỉnh sửa */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Vĩ độ (Latitude) *</label>
                <input
                  type="number"
                  step="any"
                  value={latitude}
                  onChange={(e) => setLatitude(e.target.value)}
                  placeholder="VD: 10.814387"
                  required
                  disabled={isSubmitting}
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: fieldErrors.latitude ? '1px solid #ef4444' : '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
                {fieldErrors.latitude && <span style={{ color: '#ef4444', fontSize: '0.7rem' }}>{fieldErrors.latitude}</span>}
              </div>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Kinh độ (Longitude) *</label>
                <input
                  type="number"
                  step="any"
                  value={longitude}
                  onChange={(e) => setLongitude(e.target.value)}
                  placeholder="VD: 106.711822"
                  required
                  disabled={isSubmitting}
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: fieldErrors.longitude ? '1px solid #ef4444' : '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
                {fieldErrors.longitude && <span style={{ color: '#ef4444', fontSize: '0.7rem' }}>{fieldErrors.longitude}</span>}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Loại trạm *</label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as StationType)}
                  disabled={isSubmitting}
                  style={{ width: '100%', padding: '8px 12px', background: '#0f172a', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                >
                  <option value="START">Trạm Đầu (START)</option>
                  <option value="STOP">Trạm Dừng (STOP)</option>
                  <option value="END">Trạm Cuối (END)</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Bán kính check-in: {radius}m *</label>
                <input
                  type="range"
                  min="30"
                  max="150"
                  step="5"
                  value={radius}
                  onChange={(e) => setRadius(Number(e.target.value))}
                  disabled={isSubmitting}
                  style={{ width: '100%', marginTop: '6px' }}
                />
              </div>
            </div>

            <div style={{ marginBottom: '10px' }}>
              <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Địa chỉ (tùy chọn)</label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="VD: 292 Đinh Bộ Lĩnh, Bình Thạnh"
                disabled={isSubmitting}
                style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: fieldErrors.address ? '1px solid #ef4444' : '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
              />
              {fieldErrors.address && <span style={{ color: '#ef4444', fontSize: '0.7rem' }}>{fieldErrors.address}</span>}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '14px' }}>
              {selectedStation && (
                <button
                  type="button"
                  onClick={handleCancelEdit}
                  disabled={isSubmitting}
                  className="glass-btn secondary"
                  style={{ fontSize: '0.8rem' }}
                >
                  Hủy
                </button>
              )}
              <button
                type="submit"
                disabled={isSubmitting}
                className="glass-btn primary"
                style={{ fontSize: '0.8rem', opacity: isSubmitting ? 0.7 : 1 }}
              >
                <Check size={14} />
                <span>{isSubmitting ? 'Đang lưu...' : selectedStation ? 'Lưu Thay Đổi' : 'Lưu Trạm Mới'}</span>
              </button>
            </div>
          </form>

          {/* Danh sách trạm */}
          <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '10px' }}>
            Danh Sách Trạm Hiện Tại ({stations.length})
          </h4>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {stations.map((st, i) => (
              <div
                key={st.id}
                style={{
                  background: selectedStation?.id === st.id ? 'rgba(0, 240, 255, 0.08)' : 'rgba(255,255,255,0.03)',
                  border: selectedStation?.id === st.id ? '1px solid rgba(0, 240, 255, 0.3)' : '1px solid rgba(255,255,255,0.05)',
                  borderRadius: '10px',
                  padding: '10px 14px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div style={{ flex: 1, marginRight: '10px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#00f0ff' }}>#{i + 1}</span>
                    <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#fff' }}>{st.name}</span>
                    <span className="glass-badge cyan" style={{ fontSize: '0.65rem' }}>{st.code}</span>
                    {getTypeBadge(st.stationType)}
                  </div>
                  <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '3px' }}>
                    {st.address ? `${st.address} • ` : ''}Tọa độ: {st.latitude.toFixed(4)}, {st.longitude.toFixed(4)} • Bán kính: {st.radiusMeters}m
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {confirmDeleteId === st.id ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <span style={{ fontSize: '0.7rem', color: '#f87171', marginRight: '4px' }}>Xác nhận xóa?</span>
                      <button
                        onClick={() => handleConfirmDelete(st.id)}
                        disabled={isSubmitting}
                        className="glass-btn danger"
                        style={{ padding: '4px 8px', fontSize: '0.7rem' }}
                      >
                        {deletingId === st.id ? '...' : 'Xóa'}
                      </button>
                      <button
                        onClick={() => setConfirmDeleteId(null)}
                        disabled={isSubmitting}
                        className="glass-btn secondary"
                        style={{ padding: '4px 8px', fontSize: '0.7rem' }}
                      >
                        Hủy
                      </button>
                    </div>
                  ) : (
                    <>
                      <button
                        onClick={() => handleStartEdit(st)}
                        disabled={isSubmitting}
                        className="glass-btn secondary"
                        style={{ padding: '6px 10px' }}
                        title="Chỉnh sửa trạm"
                      >
                        <Edit2 size={13} />
                      </button>
                      <button
                        onClick={() => setConfirmDeleteId(st.id)}
                        disabled={isSubmitting}
                        className="glass-btn danger"
                        style={{ padding: '6px 10px' }}
                        title="Xóa trạm"
                      >
                        <Trash2 size={13} />
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function StationModal(props: StationModalProps) {
  if (!props.isOpen) return null;

  const modalKey = props.pendingCoords
    ? `modal_${props.pendingCoords.lat.toFixed(6)}_${props.pendingCoords.lng.toFixed(6)}`
    : 'modal_no_coords';

  return <StationModalContent key={modalKey} {...props} />;
}
