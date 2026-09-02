import React from 'react';
import { Clock, Navigation, CheckCircle2, ShieldAlert, Compass, Gauge } from 'lucide-react';
import { VehicleTelemetry, Route, Trip } from '../types';

interface TimelinePanelProps {
  telemetry: VehicleTelemetry | null;
  route: Route | null;
  trip: Trip | null;
}

export default function TimelinePanel({ telemetry, route, trip }: TimelinePanelProps) {
  const stationsEta = telemetry?.stationsEta || [];
  const inIncident = telemetry?.inIncidentZone;
  const incidentNotice = telemetry?.currentIncidentNotice;

  const formatEta = (seconds: number | undefined | null) => {
    if (seconds === undefined || seconds === null || seconds <= 0) return 'Đã đến trạm';
    const m = Math.floor(seconds / 60);
    const s = Math.round(seconds % 60);
    if (m === 0) return `${s} giây`;
    return `${m}p ${s}s`;
  };

  const formatTime = (isoString?: string) => {
    if (!isoString) return '--:--';
    const date = new Date(isoString);
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div
      className="glass-panel"
      style={{
        position: 'absolute',
        top: '84px',
        left: '24px',
        width: '380px',
        maxHeight: 'calc(100vh - 110px)',
        zIndex: 10,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      {/* Header trạng thái xe */}
      <div style={{ padding: '18px 20px', borderBottom: '1px solid var(--border-glass)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#10b981', boxShadow: '0 0 10px #10b981' }}></div>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Trạng thái xe trực tuyến
            </span>
          </div>
          <span className="glass-badge cyan">
            {telemetry?.plateNumber || trip?.vehiclePlateNumber || '51B-299.88'}
          </span>
        </div>

        {/* Đồng hồ số hiển thị Vận tốc & Góc xoay */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginTop: '12px' }}>
          <div style={{ background: 'rgba(0,0,0,0.25)', padding: '10px 14px', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>
              <Gauge size={14} color="#00f0ff" />
              <span>VẬN TỐC</span>
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, color: inIncident ? '#ef4444' : '#00f0ff', fontFamily: 'var(--font-display)' }}>
              {telemetry ? telemetry.speed : 0} <span style={{ fontSize: '0.75rem', fontWeight: 500, color: '#94a3b8' }}>km/h</span>
            </div>
          </div>

          <div style={{ background: 'rgba(0,0,0,0.25)', padding: '10px 14px', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>
              <Compass size={14} color="#8b5cf6" />
              <span>GÓC QUAY</span>
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#f8fafc', fontFamily: 'var(--font-display)' }}>
              {telemetry ? Math.round(telemetry.heading) : 0}°
            </div>
          </div>
        </div>

        {/* Banner cảnh báo sự cố giao thông nếu xe đang đi vào khu vực kẹt xe */}
        {inIncident && (
          <div
            style={{
              marginTop: '12px',
              padding: '10px 12px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(185, 28, 28, 0.35))',
              border: '1px solid #ef4444',
              display: 'flex',
              gap: '10px',
              alignItems: 'center',
            }}
          >
            <ShieldAlert size={22} color="#ef4444" style={{ flexShrink: 0 }} />
            <div>
              <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#fca5a5' }}>
                ĐANG GẶP SỰ CỐ GIAO THÔNG
              </div>
              <div style={{ fontSize: '0.72rem', color: '#cbd5e1' }}>
                {incidentNotice || 'Khu vực ùn tắc, tốc độ giảm mạnh và tăng thời gian tới trạm!'}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Danh sách các trạm và tiến trình Check-in thời gian thực */}
      <div style={{ padding: '16px 20px', overflowY: 'auto', flex: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
          <h3 style={{ fontSize: '0.9rem', fontWeight: 700, letterSpacing: '0.5px', textTransform: 'uppercase', color: '#cbd5e1' }}>
            Lịch Trình & Thời Gian Đến (ETA)
          </h3>
          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
            {route ? `${route.totalDistanceKm} km • ~${route.estimatedDurationMinutes}p` : ''}
          </span>
        </div>

        <div style={{ position: 'relative', paddingLeft: '28px' }}>
          <div
            style={{
              position: 'absolute',
              left: '11px',
              top: '12px',
              bottom: '16px',
              width: '2px',
              background: 'linear-gradient(to bottom, #10b981, #00f0ff, #8b5cf6)',
              opacity: 0.4,
            }}
          />

          {stationsEta.map((stationEta, idx) => {
            const isCheckedIn = stationEta.status === 'CHECKED_IN';
            const isTarget = telemetry?.targetStationId === stationEta.stationId;
            const isStart = idx === 0;
            const isEnd = idx === stationsEta.length - 1;

            return (
              <div
                key={stationEta.stationId}
                style={{
                  position: 'relative',
                  marginBottom: '16px',
                  opacity: isCheckedIn ? 0.65 : 1,
                  transition: 'all 0.3s ease',
                }}
              >
                <div
                  style={{
                    position: 'absolute',
                    left: '-28px',
                    top: '2px',
                    width: '24px',
                    height: '24px',
                    borderRadius: '50%',
                    background: isCheckedIn
                      ? '#10b981'
                      : isTarget
                      ? '#00f0ff'
                      : '#1e293b',
                    border: isTarget ? '2px solid #fff' : '2px solid var(--border-glass)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '11px',
                    fontWeight: 700,
                    color: isCheckedIn || isTarget ? '#000' : '#94a3b8',
                    boxShadow: isTarget ? '0 0 12px #00f0ff' : 'none',
                    zIndex: 2,
                  }}
                >
                  {isCheckedIn ? '✓' : stationEta.stopOrder}
                </div>

                <div
                  style={{
                    background: isTarget ? 'rgba(0, 240, 255, 0.08)' : 'rgba(255,255,255,0.02)',
                    border: isTarget ? '1px solid rgba(0, 240, 255, 0.3)' : '1px solid rgba(255,255,255,0.05)',
                    borderRadius: '10px',
                    padding: '10px 12px',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                    <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: isTarget ? '#00f0ff' : '#f8fafc' }}>
                      {stationEta.stationName}
                    </h4>
                    {isStart && <span className="glass-badge emerald" style={{ fontSize: '0.65rem' }}>XUẤT PHÁT</span>}
                    {isEnd && <span className="glass-badge crimson" style={{ fontSize: '0.65rem' }}>ĐÍCH ĐẾN</span>}
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', marginTop: '6px' }}>
                    {isCheckedIn ? (
                      <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <CheckCircle2 size={13} />
                        Đã check-in ({formatTime(stationEta.estimatedArrivalTime)})
                      </span>
                    ) : isTarget ? (
                      <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                        <span style={{ color: '#00f0ff', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Navigation size={13} />
                          Cách {Math.round(stationEta.distanceRemainingMeters)}m
                        </span>
                        <span style={{ color: '#f59e0b', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Clock size={13} />
                          ETA: {formatEta(stationEta.etaSeconds)}
                        </span>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', color: '#64748b' }}>
                        <span>Cách {(stationEta.distanceRemainingMeters / 1000).toFixed(1)} km</span>
                        <span style={{ color: '#94a3b8' }}>
                          Dự kiến: {formatTime(stationEta.estimatedArrivalTime)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
