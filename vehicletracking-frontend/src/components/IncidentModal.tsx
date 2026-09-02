import React, { useState } from 'react';
import { X, AlertTriangle, Check, Trash2, Power } from 'lucide-react';
import { TrafficIncident, IncidentType } from '../types';

interface IncidentModalProps {
  isOpen: boolean;
  onClose: () => void;
  incidents: TrafficIncident[];
  onToggleIncident: (id: number) => void;
  onDeleteIncident: (id: number) => void;
  onCreateIncident: (data: Partial<TrafficIncident>) => void;
  pendingCoords: { lat: number; lng: number } | null;
}

export default function IncidentModal({
  isOpen,
  onClose,
  incidents,
  onToggleIncident,
  onDeleteIncident,
  onCreateIncident,
  pendingCoords,
}: IncidentModalProps) {
  const [title, setTitle] = useState('');
  const [type, setType] = useState<IncidentType>('CONGESTION');
  const [radius, setRadius] = useState(250);
  const [speedReduction, setSpeedReduction] = useState(70);
  const [description, setDescription] = useState('');

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title) return;

    onCreateIncident({
      title,
      type,
      latitude: pendingCoords ? pendingCoords.lat : 10.795,
      longitude: pendingCoords ? pendingCoords.lng : 106.708,
      radiusMeters: radius,
      speedReductionPercent: speedReduction,
      description,
      active: true,
    });

    setTitle('');
    setDescription('');
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
        {/* Header */}
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-glass)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={20} color="#ef4444" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>
              Quản Lý Sự Cố Giao Thông
            </h3>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: '20px', overflowY: 'auto' }}>
          {/* Form thêm mới */}
          <form onSubmit={handleSubmit} style={{ background: 'rgba(0,0,0,0.25)', padding: '16px', borderRadius: '12px', marginBottom: '20px', border: '1px solid rgba(255,255,255,0.06)' }}>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: '#00f0ff', marginBottom: '12px' }}>
              + Thêm Điểm Nghẽn Mới {pendingCoords && `(Tọa độ: ${pendingCoords.lat.toFixed(4)}, ${pendingCoords.lng.toFixed(4)})`}
            </h4>

            <div style={{ marginBottom: '10px' }}>
              <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Tiêu đề sự cố</label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="VD: Ùn tắc nghiêm trọng tại Ngã tư Hàng Xanh"
                required
                style={{ width: '100%', padding: '8px 12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '10px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Loại sự cố</label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as IncidentType)}
                  style={{ width: '100%', padding: '8px 12px', background: '#0f172a', border: '1px solid var(--border-glass)', borderRadius: '8px', color: '#fff', fontSize: '0.85rem' }}
                >
                  <option value="CONGESTION">Ùn tắc / Kẹt xe</option>
                  <option value="ACCIDENT">Tai nạn giao thông</option>
                  <option value="CONSTRUCTION">Công trường thi công</option>
                  <option value="BAD_WEATHER">Mưa ngập / Thời tiết xấu</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', color: '#94a3b8', display: 'block', marginBottom: '4px' }}>Giảm tốc độ: {speedReduction}%</label>
                <input
                  type="range"
                  min="20"
                  max="90"
                  step="5"
                  value={speedReduction}
                  onChange={(e) => setSpeedReduction(Number(e.target.value))}
                  style={{ width: '100%', marginTop: '6px' }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '14px' }}>
              <button type="submit" className="glass-btn primary" style={{ fontSize: '0.8rem' }}>
                <Check size={14} />
                <span>Kích Hoạt Điểm Kẹt Xe</span>
              </button>
            </div>
          </form>

          {/* Danh sách các sự cố hiện có */}
          <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '10px' }}>
            Danh Sách Điểm Sự Cố ({incidents.length})
          </h4>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {incidents.map((incident) => (
              <div
                key={incident.id}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: incident.active ? '1px solid rgba(239, 68, 68, 0.4)' : '1px solid rgba(255,255,255,0.05)',
                  borderRadius: '10px',
                  padding: '12px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '0.875rem', fontWeight: 600, color: incident.active ? '#f87171' : '#64748b' }}>
                      {incident.title}
                    </span>
                    <span className={`glass-badge ${incident.active ? 'crimson' : 'cyan'}`} style={{ fontSize: '0.65rem' }}>
                      {incident.active ? 'HIỆU LỰC' : 'ĐÃ TẮT'}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '3px' }}>
                    Loại: {incident.type} • Giảm tốc: {incident.speedReductionPercent}% • Bán kính: {incident.radiusMeters}m
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '6px' }}>
                  <button
                    onClick={() => onToggleIncident(incident.id)}
                    className="glass-btn"
                    style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                    title={incident.active ? 'Tắt sự cố' : 'Bật sự cố'}
                  >
                    <Power size={13} color={incident.active ? '#10b981' : '#64748b'} />
                  </button>
                  <button
                    onClick={() => onDeleteIncident(incident.id)}
                    className="glass-btn danger"
                    style={{ padding: '6px 10px' }}
                    title="Xóa sự cố"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
