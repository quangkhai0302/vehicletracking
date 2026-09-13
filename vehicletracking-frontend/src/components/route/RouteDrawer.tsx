import { useId, useState } from 'react';
import {
  AlertCircle,
  ArrowDown,
  ArrowUp,
  Car,
  CheckCircle2,
  Plus,
  RefreshCw,
  Trash2,
  X,
} from 'lucide-react';
import type { RouteCreateInput, RouteDetail, RouteStopRole } from '../../types/route';
import type { Station } from '../../types/station';
import { formatDuration } from '../../utils/format';

interface RouteDrawerProps {
  mode: 'closed' | 'create' | 'view';
  routeDetail: RouteDetail | null;
  stations: Station[];
  loadingDetail: boolean;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
}

interface FormStopItem {
  id: string;
  stationId: number;
  dwellDurationSeconds: number;
}

function getStopRole(index: number, total: number): RouteStopRole {
  if (index === 0) return 'START';
  if (index === total - 1) return 'END';
  return 'STOP';
}

function normalizeStops(stops: FormStopItem[]): FormStopItem[] {
  return stops.map((stop, idx) => {
    if (idx === 0 || idx === stops.length - 1) {
      return { ...stop, dwellDurationSeconds: 0 };
    }
    const clamped = Math.max(0, Math.min(3600, Number.isFinite(stop.dwellDurationSeconds) ? stop.dwellDurationSeconds : 0));
    return { ...stop, dwellDurationSeconds: clamped };
  });
}

interface RouteCreateContentProps {
  stations: Station[];
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
}

function RouteCreateContent({
  stations,
  saving,
  error,
  onClose,
  onSaveRoute,
}: RouteCreateContentProps) {
  const nameInputId = useId();
  const [name, setName] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const [formStops, setFormStops] = useState<FormStopItem[]>(() => {
    const active = stations.filter((s) => s.active);
    if (active.length >= 2) {
      return [
        { id: crypto.randomUUID(), stationId: active[0].id, dwellDurationSeconds: 0 },
        { id: crypto.randomUUID(), stationId: active[1].id, dwellDurationSeconds: 0 },
      ];
    }
    if (active.length === 1) {
      return [{ id: crypto.randomUUID(), stationId: active[0].id, dwellDurationSeconds: 0 }];
    }
    return [];
  });

  const activeStations = stations.filter((s) => s.active);

  const handleAddStop = () => {
    if (activeStations.length === 0) return;
    const lastStop = formStops[formStops.length - 1];
    let nextStation = activeStations[0];
    if (lastStop) {
      const candidate = activeStations.find((s) => s.id !== lastStop.stationId);
      if (candidate) nextStation = candidate;
    }
    setFormStops((prev) =>
      normalizeStops([
        ...prev,
        { id: crypto.randomUUID(), stationId: nextStation.id, dwellDurationSeconds: 0 },
      ])
    );
  };

  const handleRemoveStop = (index: number) => {
    setFormStops((prev) => normalizeStops(prev.filter((_, i) => i !== index)));
  };

  const handleMoveUp = (index: number) => {
    if (index <= 0) return;
    setFormStops((prev) => {
      const next = [...prev];
      const temp = next[index - 1];
      next[index - 1] = next[index];
      next[index] = temp;
      return normalizeStops(next);
    });
  };

  const handleMoveDown = (index: number) => {
    if (index >= formStops.length - 1) return;
    setFormStops((prev) => {
      const next = [...prev];
      const temp = next[index + 1];
      next[index + 1] = next[index];
      next[index] = temp;
      return normalizeStops(next);
    });
  };

  const handleChangeStation = (index: number, newStationId: number) => {
    setFormStops((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], stationId: newStationId };
      return next;
    });
  };

  const handleChangeDwell = (index: number, seconds: number) => {
    if (index === 0 || index === formStops.length - 1) return;
    const clamped = Math.max(0, Math.min(3600, Number.isFinite(seconds) ? seconds : 0));
    setFormStops((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], dwellDurationSeconds: clamped };
      return next;
    });
  };

  const trimmedName = name.trim();
  const isNameValid = trimmedName.length > 0 && trimmedName.length <= 150;
  const isStopsCountValid = formStops.length >= 2 && formStops.length <= 50;
  const hasNoConsecutiveDuplicates =
    formStops.length >= 2 &&
    formStops.every((s, i) => i === 0 || s.stationId !== formStops[i - 1].stationId);
  const areDwellsValid = formStops.every((s, i) => {
    if (i === 0 || i === formStops.length - 1) return s.dwellDurationSeconds === 0;
    return s.dwellDurationSeconds >= 0 && s.dwellDurationSeconds <= 3600;
  });
  const isFormValid = isNameValid && isStopsCountValid && hasNoConsecutiveDuplicates && areDwellsValid;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);

    if (!isNameValid) {
      setLocalError('Vui lòng nhập tên tuyến đường (tối đa 150 ký tự).');
      return;
    }

    if (formStops.length < 2) {
      setLocalError('Tuyến đường cần tối thiểu 2 điểm dừng.');
      return;
    }

    if (formStops.length > 50) {
      setLocalError('Số lượng điểm dừng không được vượt quá 50.');
      return;
    }

    for (let i = 0; i < formStops.length - 1; i++) {
      if (formStops[i].stationId === formStops[i + 1].stationId) {
        setLocalError(`Hai điểm dừng liên tiếp (#${i + 1} và #${i + 2}) không được cùng một trạm.`);
        return;
      }
    }

    const normalized = normalizeStops(formStops);

    onSaveRoute({
      name: trimmedName,
      stops: normalized.map((s) => ({
        stationId: s.stationId,
        dwellDurationSeconds: s.dwellDurationSeconds,
      })),
    });
  };

  return (
    <>
      <div className="route-drawer-header">
        <div>
          <div className="panel-eyebrow">Kế hoạch vận hành</div>
          <h3>Tạo tuyến đường mới</h3>
        </div>
        <button
          type="button"
          className="drawer-close-btn"
          onClick={onClose}
          disabled={saving}
          title="Đóng bảng tạo tuyến"
          aria-label="Đóng"
        >
          <X size={18} />
        </button>
      </div>

      <div className="route-drawer-body">
        {(localError || error) && (
          <div className="route-error-banner" role="alert">
            <AlertCircle size={16} />
            <span>{localError || error}</span>
          </div>
        )}

        <form id="route-create-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label htmlFor={nameInputId} className="form-label">
              Tên tuyến đường *
            </label>
            <input
              id={nameInputId}
              type="text"
              className="form-input"
              placeholder="Ví dụ: Tuyến 01: Bến Thành - Suối Tiên"
              maxLength={150}
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              required
            />
          </div>

          <div className="form-field" style={{ marginTop: '14px' }}>
            <span className="form-label">Phương tiện vận chuyển</span>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '8px 12px',
                background: 'rgba(255, 255, 255, 0.04)',
                borderRadius: '8px',
                fontSize: '12px',
              }}
            >
              <Car size={16} className="text-cyan" />
              <span>Ô tô / Xe buýt (HERE CAR Mode)</span>
            </div>
          </div>

          <div style={{ marginTop: '18px' }}>
            <div className="stops-builder-header">
              <span className="form-label">
                Danh sách điểm dừng ({formStops.length} trạm)
              </span>
              <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                Tối thiểu 2 trạm
              </span>
            </div>

            <div className="stops-list">
              {formStops.map((stop, idx) => {
                const role = getStopRole(idx, formStops.length);
                return (
                  <div key={stop.id} className="stop-builder-item">
                    <span className={`stop-badge ${role.toLowerCase()}`}>
                      {role === 'START' ? 'Đầu' : role === 'END' ? 'Cuối' : `#${idx + 1}`}
                    </span>

                    <select
                      className="stop-select"
                      value={stop.stationId}
                      onChange={(e) => handleChangeStation(idx, Number(e.target.value))}
                      aria-label={`Chọn trạm cho điểm dừng ${idx + 1}`}
                    >
                      {activeStations.map((station) => (
                        <option key={station.id} value={station.id}>
                          {station.name}
                        </option>
                      ))}
                    </select>

                    {role === 'STOP' ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <input
                          type="number"
                          className="stop-dwell-input"
                          min={0}
                          max={3600}
                          value={stop.dwellDurationSeconds}
                          onChange={(e) => handleChangeDwell(idx, parseInt(e.target.value, 10))}
                          title="Thời gian đón/trả khách (0-3600 giây)"
                          aria-label={`Thời gian dừng cho điểm ${idx + 1} (giây)`}
                        />
                        <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>s</span>
                      </div>
                    ) : (
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '11px',
                          color: 'var(--color-text-muted)',
                          minWidth: '52px',
                        }}
                        title={role === 'START' ? 'Điểm xuất phát (không dừng)' : 'Điểm kết thúc (không dừng)'}
                      >
                        0s
                      </div>
                    )}

                    <div className="stop-actions">
                      <button
                        type="button"
                        className="btn-icon-small"
                        onClick={() => handleMoveUp(idx)}
                        disabled={idx === 0}
                        title="Di chuyển lên"
                        aria-label="Di chuyển lên"
                      >
                        <ArrowUp size={13} />
                      </button>
                      <button
                        type="button"
                        className="btn-icon-small"
                        onClick={() => handleMoveDown(idx)}
                        disabled={idx === formStops.length - 1}
                        title="Di chuyển xuống"
                        aria-label="Di chuyển xuống"
                      >
                        <ArrowDown size={13} />
                      </button>
                      <button
                        type="button"
                        className="btn-icon-small danger"
                        onClick={() => handleRemoveStop(idx)}
                        disabled={formStops.length <= 2}
                        title="Xóa điểm dừng"
                        aria-label="Xóa điểm dừng"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>

            <button
              type="button"
              className="btn-add-stop"
              onClick={handleAddStop}
              disabled={activeStations.length === 0 || formStops.length >= 50}
            >
              <Plus size={14} /> Thêm điểm dừng đón/trả
            </button>
          </div>
        </form>
      </div>

      <div className="route-drawer-footer">
        <button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>
          Hủy
        </button>
        <button
          type="submit"
          form="route-create-form"
          className="btn-primary"
          disabled={saving || !isFormValid}
        >
          {saving ? (
            <>
              <RefreshCw size={14} className="animate-spin" />
              <span>Đang tính toán lộ trình HERE...</span>
            </>
          ) : (
            <>
              <CheckCircle2 size={14} />
              <span>Lưu tuyến đường</span>
            </>
          )}
        </button>
      </div>
    </>
  );
}

interface RouteViewContentProps {
  routeDetail: RouteDetail | null;
  loadingDetail: boolean;
  error: string | null;
  onClose: () => void;
}

function RouteViewContent({
  routeDetail,
  loadingDetail,
  error,
  onClose,
}: RouteViewContentProps) {
  return (
    <>
      <div className="route-drawer-header">
        <div>
          <div className="panel-eyebrow">Lộ trình chi tiết</div>
          <h3>{routeDetail?.name || 'Chi tiết tuyến'}</h3>
        </div>
        <button
          type="button"
          className="drawer-close-btn"
          onClick={onClose}
          title="Đóng bảng chi tiết"
          aria-label="Đóng"
        >
          <X size={18} />
        </button>
      </div>

      <div className="route-drawer-body">
        {error && (
          <div className="route-error-banner" role="alert">
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        {loadingDetail && (
          <div className="panel-loading" role="status">
            <RefreshCw size={18} className="animate-spin" />
            <span>Đang tải thông tin chi tiết lộ trình...</span>
          </div>
        )}

        {!loadingDetail && routeDetail && (
          <>
            <div className="route-detail-summary-grid">
              <div className="metric-card">
                <span className="metric-card-label">Tổng cự ly</span>
                <span className="metric-card-value tabular-numbers">
                  {(routeDetail.totalDistanceMeters / 1000).toFixed(2)} km
                </span>
                <span className="metric-card-sub">Theo mạng lưới giao thông</span>
              </div>

              <div className="metric-card">
                <span className="metric-card-label">Tổng thời gian</span>
                <span className="metric-card-value tabular-numbers">
                  {formatDuration(routeDetail.estimatedTripDurationSeconds)}
                </span>
                <span className="metric-card-sub">Bao gồm thời gian dừng</span>
              </div>

              <div className="metric-card">
                <span className="metric-card-label">Thời gian lăn bánh</span>
                <span className="metric-card-value tabular-numbers">
                  {formatDuration(routeDetail.estimatedTravelDurationSeconds)}
                </span>
                <span className="metric-card-sub">HERE Router CAR</span>
              </div>

              <div className="metric-card">
                <span className="metric-card-label">Thời gian đón/trả</span>
                <span className="metric-card-value tabular-numbers">
                  {formatDuration(routeDetail.totalDwellDurationSeconds)}
                </span>
                <span className="metric-card-sub">{routeDetail.stops.length} điểm dừng</span>
              </div>
            </div>

            <div style={{ marginTop: '10px' }}>
              <span className="form-label">Hành trình chi tiết qua các trạm</span>
              <div className="route-timeline">
                {routeDetail.stops.map((stop, idx) => {
                  const role = stop.role;
                  return (
                    <div key={stop.sequenceNumber} className="timeline-item">
                      <div className="timeline-track">
                        <div className={`timeline-node ${role.toLowerCase()}`}>
                          {stop.sequenceNumber}
                        </div>
                        {idx < routeDetail.stops.length - 1 && <div className="timeline-line" />}
                      </div>

                      <div className="timeline-content">
                        <div className="timeline-title-row">
                          <span className="timeline-station-name">{stop.stationName}</span>
                          <span className={`stop-badge ${role.toLowerCase()}`}>
                            {role === 'START' ? 'Khởi hành' : role === 'END' ? 'Về đích' : 'Đón trả'}
                          </span>
                        </div>

                        {idx > 0 && (
                          <div className="timeline-leg-meta tabular-numbers">
                            <span>
                              +{(stop.distanceFromPreviousMeters / 1000).toFixed(2)} km
                            </span>
                            <span>
                              +{formatDuration(stop.travelDurationFromPreviousSeconds)}
                            </span>
                          </div>
                        )}

                        <div className="timeline-offsets tabular-numbers">
                          <span>
                            Đến: +{formatDuration(stop.arrivalOffsetSeconds)}
                          </span>
                          <span style={{ marginLeft: '10px' }}>
                            Rời: +{formatDuration(stop.departureOffsetSeconds)}
                          </span>
                          {stop.dwellDurationSeconds > 0 && (
                            <span style={{ marginLeft: '10px', color: 'var(--color-text-muted)' }}>
                              (dừng {stop.dwellDurationSeconds}s)
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </>
        )}
      </div>

      <div className="route-drawer-footer">
        <button type="button" className="btn-secondary" onClick={onClose}>
          Đóng
        </button>
      </div>
    </>
  );
}

export function RouteDrawer({
  mode,
  routeDetail,
  stations,
  loadingDetail,
  saving,
  error,
  onClose,
  onSaveRoute,
}: RouteDrawerProps) {
  if (mode === 'closed') {
    return null;
  }

  return (
    <aside
      className="route-drawer"
      aria-label={mode === 'create' ? 'Tạo tuyến đường' : 'Chi tiết tuyến đường'}
    >
      {mode === 'create' && (
        <RouteCreateContent
          key="create"
          stations={stations}
          saving={saving}
          error={error}
          onClose={onClose}
          onSaveRoute={onSaveRoute}
        />
      )}
      {mode === 'view' && (
        <RouteViewContent
          key={routeDetail?.id ?? 'view'}
          routeDetail={routeDetail}
          loadingDetail={loadingDetail}
          error={error}
          onClose={onClose}
        />
      )}
    </aside>
  );
}
