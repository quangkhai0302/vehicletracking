import React, { useState } from 'react';
import { X, MapPin, Check, Trash2 } from 'lucide-react';
import { Station, StationType } from '../types';

interface StationModalProps {
  isOpen: boolean;
  onClose: () => void;
  stations: Station[];
  onCreateStation: (data: Partial<Station>) => void;
  onDeleteStation: (id: number) => void;
  pendingCoords: { lat: number; lng: number } | null;
}

export default function StationModal({
  isOpen,
  onClose,
  stations,
  onCreateStation,
  onDeleteStation,
  pendingCoords,
}: StationModalProps) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [type, setType] = useState<StationType>('STOP');
  const [radius, setRadius] = useState(60);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !code) return;

    onCreateStation({
      code,
      name,
      address,
      stationType: type,
      latitude: pendingCoords ? pendingCoords.lat : 10.795,
      longitude: pendingCoords ? pendingCoords.lng : 106.705,
      radiusMeters: radius,
    });

    setCode('');
    setName('');
    setAddress('');
    onClose();
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
      onClick={onClose}
    >
      <div
        className="glass-panel"
        style={{
          width: '560px',
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          animation: 'slideInRight 0.2s ease',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-glass)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <MapPin size={20} color="#00f0ff" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>
              Quản Lý Trạm Dừng Tuyến Đường
            </h3>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        <div style={{ padding: '20px', overflowY: 'auto' }}>
          {/* Form thêm trạm */}
          <form onSubmit={handleSubmit} style={{ background: 'rgba(0,0,0,0.25)', padding: '16px', borderRadius: '12px', marginBottom: '20px', border: '1px solid rgba(255,255,255,0.06)' }}>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: '#00f0ff', marginBottom: '12px' }}>
              + Thêm Trạm Mới {pendingCoords && `(${pendingCoords.lat.toFixed(4)}, ${pendingCoords.lng.toFixed(4)})`}
            </h4>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Mã trạm</label>
                <input
                  type="text"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="VD: ST-06"
                  required
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Tên trạm dừng</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="VD: Cổng trường ĐH Khoa học Tự nhiên"
                  required
                  style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Loại trạm</label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as StationType)}
                  style={{ width: '100%', padding: '8px 12px', background: '#0f172a', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                >
                  <option value="START">Trạm Đầu (Xuất phát)</option>
                  <option value="STOP">Trạm Dừng (Trung gian)</option>
                  <option value="END">Trạm Cuối (Đích đến)</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Bán kính check-in: {radius} mét</label>
                <input
                  type="range"
                  min="30"
                  max="150"
                  step="5"
                  value={radius}
                  onChange={(e) => setRadius(Number(e.target.value))}
                  style={{ width: '100%', marginTop: '6px' }}
                />
              </div>
            </div>

            <div style={{ marginBottom: '10px' }}>
              <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Địa chỉ</label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="VD: 227 Nguyễn Văn Cừ, Quận 5"
                style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '14px' }}>
              <button type="submit" className="glass-btn primary" style={{ fontSize: '0.8rem' }}>
                <Check size={14} />
                <span>Lưu Trạm Mới</span>
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
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(255,255,255,0.05)',
                  borderRadius: '10px',
                  padding: '10px 14px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#00f0ff' }}>#{i + 1}</span>
                    <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#fff' }}>{st.name}</span>
                    <span className="glass-badge cyan" style={{ fontSize: '0.65rem' }}>{st.code}</span>
                  </div>
                  <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '2px' }}>
                    {st.address || `${st.latitude.toFixed(4)}, ${st.longitude.toFixed(4)}`} • Bán kính geofence: {st.radiusMeters}m
                  </div>
                </div>

                <button
                  onClick={() => onDeleteStation(st.id)}
                  className="glass-btn danger"
                  style={{ padding: '6px 10px' }}
                  title="Xóa trạm"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
