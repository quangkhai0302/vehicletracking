import React from 'react';
import { Clock, Navigation, CheckCircle2, ShieldAlert, Compass, Gauge, CalendarClock, Flag } from 'lucide-react';
import { VehicleTelemetry, Route, Trip, StationEta } from '../types';

interface TimelinePanelProps {
  telemetry: VehicleTelemetry | null;
  route: Route | null;
  trip: Trip | null;
}

export default function TimelinePanel({ telemetry, route, trip }: TimelinePanelProps) {
  // BR-006 / REV-002: Chỉ chấp nhận telemetry nếu khớp với trip hiện tại
  const matchingTelemetry = (telemetry && trip && telemetry.tripId === trip.id) ? telemetry : null;

  const hasDynamicTelemetry = Boolean(
    matchingTelemetry &&
    matchingTelemetry.stationsEta &&
    matchingTelemetry.stationsEta.length > 0
  );

  const inIncident = matchingTelemetry?.inIncidentZone;
  const incidentNotice = matchingTelemetry?.currentIncidentNotice;

  const formatEta = (seconds: number | undefined | null) => {
    if (seconds === undefined || seconds === null) return '--:--';
    if (seconds <= 0) return 'Đã đến trạm';
    const m = Math.floor(seconds / 60);
    const s = Math.round(seconds % 60);
    if (m === 0) return `${s} giây`;
    return `${m}p ${s}s`;
  };

  const formatTime = (isoString?: string | null) => {
    if (!isoString) return '--:--';
    try {
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return '--:--';
      return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return '--:--';
    }
  };

  // Chuẩn bị danh sách trạm: dynamic từ telemetry hoặc fallback từ trip.checkIns
  interface TimelineItem {
    stationId: number;
    stationName: string;
    stopOrder: number;
    status: 'CHECKED_IN' | 'PENDING' | 'SKIPPED';
    isTarget: boolean;
    distanceRemainingMeters?: number;
    etaSeconds?: number;
    timeDisplay: string;
  }

  let timelineItems: TimelineItem[] = [];
  let completionEstimatedTime: string | null = null;
  let completionEtaSeconds: number | null = null;
  const isTripCompleted =
    matchingTelemetry?.tripStatus === 'COMPLETED' ||
    (matchingTelemetry?.status === 'IDLE' && hasDynamicTelemetry && (matchingTelemetry?.targetStationId == null)) ||
    trip?.status === 'COMPLETED';

  if (hasDynamicTelemetry && matchingTelemetry) {
    const rawStations: StationEta[] = [...matchingTelemetry.stationsEta].sort((a, b) => a.stopOrder - b.stopOrder);
    timelineItems = rawStations.map((eta) => ({
      stationId: eta.stationId,
      stationName: eta.stationName,
      stopOrder: eta.stopOrder,
      status: eta.status,
      isTarget: matchingTelemetry.targetStationId === eta.stationId,
      distanceRemainingMeters: eta.distanceRemainingMeters,
      etaSeconds: eta.etaSeconds,
      timeDisplay: formatTime(eta.estimatedArrivalTime),
    }));

    const lastEta = rawStations[rawStations.length - 1];
    completionEstimatedTime = matchingTelemetry.estimatedCompletionTime || lastEta?.estimatedArrivalTime || trip?.endTime || null;
    completionEtaSeconds = matchingTelemetry.etaSecondsToCompletion !== undefined ? matchingTelemetry.etaSecondsToCompletion : (lastEta?.etaSeconds ?? null);
  } else if (trip && trip.checkIns && trip.checkIns.length > 0) {
    // BR-006: Fallback schedule khi chưa có telemetry hoặc chưa chạy simulator
    const sortedCheckIns = [...trip.checkIns].sort((a, b) => a.stopOrder - b.stopOrder);
    timelineItems = sortedCheckIns.map((ci, idx) => ({
      stationId: ci.stationId,
      stationName: ci.stationName,
      stopOrder: ci.stopOrder,
      status: ci.status,
      isTarget: idx === 0 && ci.status === 'PENDING',
      timeDisplay: ci.status === 'CHECKED_IN'
        ? formatTime(ci.actualArrivalTime)
        : formatTime(ci.scheduledArrivalTime),
    }));

    const lastCi = sortedCheckIns[sortedCheckIns.length - 1];
    completionEstimatedTime = trip.endTime || lastCi?.actualArrivalTime || lastCi?.scheduledArrivalTime || null;
    completionEtaSeconds = null;
  }

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
            <div
              style={{
                width: '10px',
                height: '10px',
                borderRadius: '50%',
                background: isTripCompleted ? '#3b82f6' : hasDynamicTelemetry ? '#10b981' : '#f59e0b',
                boxShadow: isTripCompleted
                  ? '0 0 10px #3b82f6'
                  : hasDynamicTelemetry
                  ? '0 0 10px #10b981'
                  : '0 0 10px #f59e0b',
              }}
            ></div>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              {isTripCompleted
                ? 'Chuyến đi hoàn thành'
                : hasDynamicTelemetry
                ? 'Trạng thái xe trực tuyến'
                : 'Lịch trình chuyến đi'}
            </span>
          </div>
          <span className="glass-badge cyan">
            {matchingTelemetry?.plateNumber || trip?.vehiclePlateNumber || '51B-299.88'}
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
              {matchingTelemetry ? matchingTelemetry.speed : 0} <span style={{ fontSize: '0.75rem', fontWeight: 500, color: '#94a3b8' }}>km/h</span>
            </div>
          </div>

          <div style={{ background: 'rgba(0,0,0,0.25)', padding: '10px 14px', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>
              <Compass size={14} color="#8b5cf6" />
              <span>GÓC QUAY</span>
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#f8fafc', fontFamily: 'var(--font-display)' }}>
              {matchingTelemetry ? Math.round(matchingTelemetry.heading) : 0}°
            </div>
          </div>
        </div>

        {/* Banner tổng thời gian về đích (Completion ETA) */}
        {completionEstimatedTime && (
          <div
            style={{
              marginTop: '12px',
              padding: '10px 12px',
              borderRadius: '10px',
              background: isTripCompleted
                ? 'linear-gradient(135deg, rgba(16, 185, 129, 0.2), rgba(6, 78, 59, 0.3))'
                : 'linear-gradient(135deg, rgba(14, 165, 233, 0.15), rgba(99, 102, 241, 0.2))',
              border: isTripCompleted ? '1px solid rgba(16, 185, 129, 0.4)' : '1px solid rgba(14, 165, 233, 0.3)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Flag size={18} color={isTripCompleted ? '#10b981' : '#38bdf8'} />
              <div>
                <div style={{ fontSize: '0.72rem', fontWeight: 700, color: isTripCompleted ? '#6ee7b7' : '#7dd3fc', textTransform: 'uppercase' }}>
                  {isTripCompleted ? 'ĐÃ VỀ ĐÍCH' : hasDynamicTelemetry ? 'DỰ KIẾN VỀ ĐÍCH' : 'LỊCH VỀ ĐÍCH'}
                </div>
                <div style={{ fontSize: '0.9rem', fontWeight: 800, color: '#f8fafc' }}>
                  {formatTime(completionEstimatedTime)}
                </div>
              </div>
            </div>

            {hasDynamicTelemetry && !isTripCompleted && completionEtaSeconds !== null && (
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>ETA HOÀN THÀNH</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 800, color: '#f59e0b' }}>
                  {formatEta(completionEtaSeconds)}
                </div>
              </div>
            )}
            {isTripCompleted && (
              <span className="glass-badge emerald" style={{ fontSize: '0.7rem' }}>
                HOÀN THÀNH
              </span>
            )}
          </div>
        )}

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
            {hasDynamicTelemetry ? 'Lịch Trình & Thời Gian Đến (ETA)' : 'Lịch Trình Kế Hoạch'}
          </h3>
          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
            {route ? `${route.totalDistanceKm} km • ~${route.estimatedDurationMinutes}p` : ''}
          </span>
        </div>

        {timelineItems.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '30px 10px', color: '#64748b', fontSize: '0.85rem' }}>
            Chưa có thông tin lịch trình chuyến đi
          </div>
        ) : (
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

            {timelineItems.map((item, idx) => {
              const isCheckedIn = item.status === 'CHECKED_IN';
              const isTarget = item.isTarget;
              const isStart = idx === 0;
              const isEnd = idx === timelineItems.length - 1;

              return (
                <div
                  key={item.stationId}
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
                    {isCheckedIn ? '✓' : item.stopOrder}
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
                        {item.stationName}
                      </h4>
                      {isStart && <span className="glass-badge emerald" style={{ fontSize: '0.65rem' }}>XUẤT PHÁT</span>}
                      {isEnd && <span className="glass-badge crimson" style={{ fontSize: '0.65rem' }}>ĐÍCH ĐẾN</span>}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', marginTop: '6px' }}>
                      {isCheckedIn ? (
                        <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <CheckCircle2 size={13} />
                          Đã check-in ({item.timeDisplay})
                        </span>
                      ) : hasDynamicTelemetry && isTarget ? (
                        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                          <span style={{ color: '#00f0ff', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Navigation size={13} />
                            Cách {Math.round(item.distanceRemainingMeters ?? 0)}m
                          </span>
                          <span style={{ color: '#f59e0b', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Clock size={13} />
                            ETA: {formatEta(item.etaSeconds)}
                          </span>
                        </div>
                      ) : hasDynamicTelemetry ? (
                        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', color: '#64748b' }}>
                          <span>Cách {((item.distanceRemainingMeters ?? 0) / 1000).toFixed(1)} km</span>
                          <span style={{ color: '#94a3b8' }}>
                            Dự kiến: {item.timeDisplay}
                          </span>
                        </div>
                      ) : (
                        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', color: '#94a3b8' }}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#64748b' }}>
                            <CalendarClock size={13} />
                            Lịch trình dự kiến
                          </span>
                          <span style={{ color: '#38bdf8', fontWeight: 600 }}>
                            {item.timeDisplay}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
