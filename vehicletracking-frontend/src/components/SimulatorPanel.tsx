import React from 'react';
import { Play, Pause, RotateCcw, Zap, AlertOctagon, PlusCircle, Layers, Radio } from 'lucide-react';
import { Trip, Route } from '../types';

interface SimulatorPanelProps {
  trip: Trip | null;
  route: Route | null;
  simStatus: 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED';
  multiplier: number;
  clickMode: string | null;
  onStart: () => void;
  onPause: () => void;
  onResume: () => void;
  onReset: () => void;
  onSetMultiplier: (m: number) => void;
  onToggleClickMode: (mode: 'ADD_STATION' | 'ADD_INCIDENT') => void;
  onOpenIncidentsModal: () => void;
  onOpenStationsModal: () => void;
}

export default function SimulatorPanel({
  trip,
  route,
  simStatus,
  multiplier,
  clickMode,
  onStart,
  onPause,
  onResume,
  onReset,
  onSetMultiplier,
  onToggleClickMode,
  onOpenIncidentsModal,
  onOpenStationsModal,
}: SimulatorPanelProps) {
  return (
    <header
      className="glass-panel"
      style={{
        position: 'absolute',
        top: '16px',
        left: '24px',
        right: '24px',
        height: '56px',
        zIndex: 20,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 20px',
      }}
    >
      {/* Brand & Logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div
          style={{
            background: 'linear-gradient(135deg, #00f0ff, #3b82f6)',
            width: '36px',
            height: '36px',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#000',
            fontWeight: 800,
            boxShadow: '0 0 16px rgba(0, 240, 255, 0.4)',
          }}
        >
          <Radio size={20} />
        </div>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <h1 style={{ fontSize: '1.1rem', fontWeight: 800, letterSpacing: '-0.5px', color: '#fff' }}>
              LiveFleet AI
            </h1>
            <span className="glass-badge emerald" style={{ fontSize: '0.65rem', padding: '2px 8px' }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }}></span>
              REALTIME
            </span>
          </div>
          <div style={{ fontSize: '0.72rem', color: '#94a3b8' }}>
            {route?.name || 'Tuyến buýt giám sát'}
          </div>
        </div>
      </div>

      {/* Simulator Playback Controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        {simStatus === 'IDLE' && (
          <button className="glass-btn primary" onClick={onStart}>
            <Play size={16} fill="currentColor" />
            <span>Bắt Đầu Giả Lập</span>
          </button>
        )}

        {simStatus === 'RUNNING' && (
          <button className="glass-btn primary" onClick={onPause}>
            <Pause size={16} fill="currentColor" />
            <span>Tạm Dừng</span>
          </button>
        )}

        {simStatus === 'PAUSED' && (
          <button className="glass-btn primary" onClick={onResume}>
            <Play size={16} fill="currentColor" />
            <span>Tiếp Tục</span>
          </button>
        )}

        <button className="glass-btn" onClick={onReset} title="Đặt lại về trạm xuất phát">
          <RotateCcw size={15} />
          <span>Reset</span>
        </button>

        {/* Multiplier selector */}
        <div
          style={{
            display: 'flex',
            background: 'rgba(0,0,0,0.3)',
            borderRadius: '8px',
            padding: '2px',
            border: '1px solid rgba(255,255,255,0.06)',
          }}
        >
          {[1, 2, 5, 10].map((m) => (
            <button
              key={m}
              onClick={() => onSetMultiplier(m)}
              style={{
                background: multiplier === m ? 'rgba(0, 240, 255, 0.25)' : 'transparent',
                color: multiplier === m ? '#00f0ff' : '#94a3b8',
                border: 'none',
                padding: '4px 10px',
                borderRadius: '6px',
                fontSize: '0.75rem',
                fontWeight: 700,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              {m}x
            </button>
          ))}
        </div>
      </div>

      {/* Feature Action Buttons */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        {/* Nút Tạo sự cố giao thông (Click trên bản đồ) */}
        <button
          className={`glass-btn ${clickMode === 'ADD_INCIDENT' ? 'danger' : ''}`}
          onClick={() => onToggleClickMode('ADD_INCIDENT')}
          style={{ fontSize: '0.8rem' }}
        >
          <AlertOctagon size={15} color={clickMode === 'ADD_INCIDENT' ? '#fff' : '#ef4444'} />
          <span>{clickMode === 'ADD_INCIDENT' ? 'Nhấp bản đồ để đặt sự cố' : '+ Thêm Điểm Kẹt Xe'}</span>
        </button>

        {/* Nút Thêm trạm dừng */}
        <button
          className={`glass-btn ${clickMode === 'ADD_STATION' ? 'primary' : ''}`}
          onClick={() => onToggleClickMode('ADD_STATION')}
          style={{ fontSize: '0.8rem' }}
        >
          <PlusCircle size={15} color={clickMode === 'ADD_STATION' ? '#fff' : '#00f0ff'} />
          <span>{clickMode === 'ADD_STATION' ? 'Nhấp bản đồ để đặt trạm' : '+ Thêm Trạm'}</span>
        </button>

        {/* Danh sách quản lý sự cố */}
        <button className="glass-btn" onClick={onOpenIncidentsModal} title="Danh sách các điểm nghẽn giao thông">
          <Layers size={15} color="#cbd5e1" />
          <span>Sự Cố</span>
        </button>
      </div>
    </header>
  );
}
