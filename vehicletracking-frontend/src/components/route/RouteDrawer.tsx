import { useEffect, useId, useRef, useState } from 'react';
import {
  AlertCircle,
  Car,
  CheckCircle2,
  Plus,
  RefreshCw,
  X,
} from 'lucide-react';
import type { RouteCreateInput, RouteDetail, RouteDraftStop } from '../../types/route';
import type { Station } from '../../types/station';
import { SortableStopList } from './SortableStopList';
import { formatDuration } from '../../utils/format';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';

interface RouteDrawerProps {
  onShape: () => void;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  mode: 'closed' | 'create' | 'edit' | 'view';
  routeDetail: RouteDetail | null;
  stations: Station[];
  loadingDetail: boolean;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
  onEdit: () => void;
  onDeactivate: () => Promise<boolean>;
  onDraftStopsChange: (stops: RouteDraftStop[]) => void;
  selectedDraftStopId: string | null;
  onFocusDraftStop: (id: string) => void;
}

function normalizeStops(stops: RouteDraftStop[]): RouteDraftStop[] {
  return stops.map((stop, idx) => {
    if (idx === 0 || idx === stops.length - 1) {
      return { ...stop, dwellDurationSeconds: 0 };
    }
    const clamped = Math.max(0, Math.min(3600, Number.isFinite(stop.dwellDurationSeconds) ? stop.dwellDurationSeconds : 0));
    return { ...stop, dwellDurationSeconds: clamped };
  });
}

interface RouteCreateContentProps {
  initialRoute?: RouteDetail;
  stations: Station[];
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSaveRoute: (input: RouteCreateInput) => void;
  onDraftStopsChange: (stops: RouteDraftStop[]) => void;
  selectedDraftStopId: string | null;
  onFocusDraftStop: (id: string) => void;
}

function RouteCreateContent({
  initialRoute,
  stations,
  saving,
  error,
  onClose,
  onSaveRoute,
  onDraftStopsChange, selectedDraftStopId, onFocusDraftStop,
}: RouteCreateContentProps) {
  const nameInputId = useId();
  const [name, setName] = useState(initialRoute?.name ?? '');
  const [localError, setLocalError] = useState<string | null>(null);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  const [formStops, setFormStops] = useState<RouteDraftStop[]>(() => {
    if (initialRoute) return initialRoute.stops.map(stop => ({ id: crypto.randomUUID(), stationId: stop.stationId, dwellDurationSeconds: stop.dwellDurationSeconds }));
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

  const initialStopsRef = useRef(formStops);
  useEffect(() => { onDraftStopsChange(formStops); }, [formStops, onDraftStopsChange]);
  useEffect(() => () => onDraftStopsChange([]), [onDraftStopsChange]);
  const safeClose = () => {
    if (saving) return;
    if (name !== (initialRoute?.name ?? '') || JSON.stringify(formStops) !== JSON.stringify(initialStopsRef.current)) setConfirmDiscard(true);
    else onClose();
  };
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
  const allStationsActive = formStops.every(stop => activeStations.some(station => station.id === stop.stationId));
  const isFormValid = isNameValid && isStopsCountValid && hasNoConsecutiveDuplicates && areDwellsValid && allStationsActive;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (saving || !isFormValid) return;
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
          <h3>{initialRoute ? 'Sửa tuyến đường' : 'Tạo tuyến đường mới'}</h3>
        </div>
        <button
          type="button"
          className="drawer-close-btn"
          onClick={safeClose}
          disabled={saving}
          title="Đóng bảng tạo tuyến"
          aria-label="Đóng"
        >
          <X size={18} />
        </button>
      </div>

      <div className="route-drawer-body">
        {!!initialRoute?.shapingPoints?.length && <p className="panel-help">
          Sửa thông tin hoặc trạm bằng biểu mẫu này sẽ tính lại đường đi và bỏ các điểm kéo chỉnh đã lưu.
          Để giữ trạm và chỉ đổi đường đi, hãy dùng “Kéo chỉnh đường đi”.
        </p>}
        {confirmDiscard && <div className="discard-confirm" role="alert">
          <strong>Bỏ bản nháp tuyến đường?</strong><p>Tên và thứ tự điểm dừng chưa lưu sẽ bị xóa.</p>
          <div><button type="button" className="btn-secondary" onClick={() => setConfirmDiscard(false)}>Tiếp tục chỉnh sửa</button><button type="button" className="danger-action" onClick={onClose}>Bỏ bản nháp</button></div>
        </div>}
        {(localError || error) && (
          <div className="route-error-banner" role="alert">
            <AlertCircle size={16} />
            <span>{localError || error}</span>
          </div>
        )}

        <form id="route-create-form" onSubmit={handleSubmit}><fieldset disabled={saving} className="route-form-fields">
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
                justifyContent: 'space-between',
                padding: '9px 12px',
                background: 'linear-gradient(145deg, rgba(20, 32, 48, 0.7) 0%, rgba(10, 18, 28, 0.85) 100%)',
                border: '1px solid rgba(56, 189, 248, 0.25)',
                borderRadius: '8px',
                fontSize: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Car size={16} className="text-cyan" />
                <span style={{ fontWeight: 500, color: '#f1f5f9' }}>Ô tô / Xe buýt</span>
              </div>
              <span style={{ fontSize: '10px', fontWeight: 700, padding: '2px 7px', borderRadius: '4px', background: 'rgba(34, 211, 238, 0.15)', color: '#38bdf8', border: '1px solid rgba(34, 211, 238, 0.35)' }}>HERE CAR</span>
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

            <SortableStopList stops={formStops} stations={activeStations} disabled={saving}
              onChange={stops => setFormStops(normalizeStops(stops))}
              selectedId={selectedDraftStopId} onFocusStop={onFocusDraftStop} />

            <button
              type="button"
              className="btn-add-stop"
              onClick={handleAddStop}
              disabled={activeStations.length === 0 || formStops.length >= 50}
            >
              <Plus size={14} /> Thêm điểm dừng đón/trả
            </button>
          </div>
          <p className="route-draft-note">{initialRoute ? 'Tính lại lộ trình HERE và cập nhật tuyến hiện tại. Chỉ sửa được tuyến chưa từng được dùng bởi chuyến đi.' : 'Bản nháp · Chưa tính lộ trình hoặc ETA. Tính & lưu sẽ tạo một tuyến mới theo thứ tự trên.'}</p>
          {!hasNoConsecutiveDuplicates && formStops.length >= 2 && <p role="alert" className="inline-error">Hai điểm liền nhau phải là hai trạm khác nhau.</p>}
          {!allStationsActive && <p role="alert" className="inline-error">Có trạm đã ngừng hoạt động. Chọn lại trạm trước khi lưu.</p>}
          {formStops.length < 2 && <p className="availability-note">Cần ít nhất hai điểm dừng. Bạn có thể thêm trạm ở tab Trạm dừng rồi quay lại.</p>}
        </fieldset></form>
      </div>

      <div className="route-drawer-footer">
        <button type="button" className="btn-secondary" onClick={safeClose} disabled={saving}>
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
              <span>{initialRoute ? 'Tính & cập nhật tuyến' : 'Tính & lưu tuyến mới'}</span>
            </>
          )}
        </button>
      </div>
    </>
  );
}

interface RouteViewContentProps {
  onShape: () => void;
  saving: boolean;
  onEdit: () => void;
  onDeactivate: () => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  routeDetail: RouteDetail | null;
  loadingDetail: boolean;
  error: string | null;
  onClose: () => void;
}

function RouteViewContent({
  onShape,
  saving, onEdit, onDeactivate,
  routeDetail,
  loadingDetail,
  error,
  onClose,
  onFocusStop,
}: RouteViewContentProps) {
  const [confirm, setConfirm] = useState(false);
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
          disabled={saving}
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
                <span className="metric-card-label">Thời gian dự kiến</span>
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
                <span className="metric-card-sub">Di chuyển bằng ô tô</span>
              </div>

              <div className="metric-card">
                <span className="metric-card-label">Thời gian đón/trả</span>
                <span className="metric-card-value tabular-numbers">
                  {formatDuration(routeDetail.totalDwellDurationSeconds)}
                </span>
                <span className="metric-card-sub">{routeDetail.stops.length} điểm dừng</span>
              </div>
            </div>

            <div className="route-snapshot-note">
              <strong>Ước tính khi tạo tuyến · {new Date(routeDetail.calculatedAt).toLocaleString('vi-VN')}</strong>
              Thời gian này chưa được cập nhật theo vị trí xe đang di chuyển.
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
                          <button className="timeline-station-name" onClick={() => onFocusStop([stop.latitude, stop.longitude], 16)} title="Xem trạm trên bản đồ">{stop.stationName}</button>
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
        <button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>
          Đóng
        </button>
        {!loadingDetail && routeDetail && <>
          <button type="button" className="btn-primary" disabled={saving} onClick={onShape}>Kéo chỉnh đường đi</button>
          <button type="button" className="btn-secondary" disabled={saving} onClick={onEdit}>Sửa tuyến</button>
          <button type="button" className="danger-action" disabled={saving} onClick={() => setConfirm(true)}>Ngừng sử dụng</button>
        </>}
      </div>
      {confirm && <FleetConfirmDialog title="Ngừng sử dụng tuyến đường?" message="Tuyến sẽ không còn trong danh sách tạo chuyến. Lịch sử được giữ lại; chuyến chưa kết thúc có thể ngăn thao tác này."
        confirmLabel="Xác nhận ngừng tuyến" busy={saving} error={error} onClose={() => setConfirm(false)} onConfirm={() => { void onDeactivate().then(success => { if (success) setConfirm(false); }); }} />}
    </>
  );
}

export function RouteDrawer({
  onShape,
  onEdit, onDeactivate,
  mode,
  routeDetail,
  stations,
  loadingDetail,
  saving,
  error,
  onClose,
  onSaveRoute,
  onDraftStopsChange, selectedDraftStopId, onFocusDraftStop,
  onFocusStop,
}: RouteDrawerProps) {
  if (mode === 'closed') {
    return null;
  }

  return (
    <aside
      className="route-drawer"
      aria-label={mode === 'create' ? 'Tạo tuyến đường' : mode === 'edit' ? 'Sửa tuyến đường' : 'Chi tiết tuyến đường'}
    >
      {(mode === 'create' || mode === 'edit') && (
        <RouteCreateContent
          key={mode === 'edit' ? `edit-${routeDetail?.id}` : 'create'}
          initialRoute={mode === 'edit' ? routeDetail ?? undefined : undefined}
          onDraftStopsChange={onDraftStopsChange} selectedDraftStopId={selectedDraftStopId} onFocusDraftStop={onFocusDraftStop}
          stations={stations}
          saving={saving}
          error={error}
          onClose={onClose}
          onSaveRoute={onSaveRoute}
        />
      )}
      {mode === 'view' && (
        <RouteViewContent
          onShape={onShape}
          saving={saving} onEdit={onEdit} onDeactivate={onDeactivate}
          key={routeDetail?.id ?? 'view'}
          onFocusStop={onFocusStop}
          routeDetail={routeDetail}
          loadingDetail={loadingDetail}
          error={error}
          onClose={onClose}
        />
      )}
    </aside>
  );
}
