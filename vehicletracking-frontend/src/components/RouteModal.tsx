import React, { useState, useMemo } from 'react';
import { X, Route as RouteIcon, Check, Trash2, Edit2, AlertCircle, ArrowUp, ArrowDown, Plus, RotateCcw } from 'lucide-react';
import { Route, Station, StationType, RouteRequest } from '../types';

interface RouteModalProps {
  isOpen: boolean;
  onClose: () => void;
  routes: Route[];
  stations: Station[];
  onCreateRoute: (data: RouteRequest) => Promise<void>;
  onUpdateRoute: (id: number, data: RouteRequest) => Promise<void>;
  onDeleteRoute: (id: number) => Promise<void>;
}

interface RouteModalContentProps {
  onClose: () => void;
  routes: Route[];
  stations: Station[];
  onCreateRoute: (data: RouteRequest) => Promise<void>;
  onUpdateRoute: (id: number, data: RouteRequest) => Promise<void>;
  onDeleteRoute: (id: number) => Promise<void>;
}

function RouteModalContent({
  onClose,
  routes,
  stations,
  onCreateRoute,
  onUpdateRoute,
  onDeleteRoute,
}: RouteModalContentProps) {
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [orderedStationIds, setOrderedStationIds] = useState<number[]>([]);
  const [stationToAdd, setStationToAdd] = useState<number | ''>('');

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const stationMap = useMemo(() => {
    const map = new Map<number, Station>();
    stations.forEach((st) => map.set(st.id, st));
    return map;
  }, [stations]);

  const resetForm = () => {
    setSelectedRoute(null);
    setCode('');
    setName('');
    setDescription('');
    setOrderedStationIds([]);
    setStationToAdd('');
    setFormError(null);
    setFieldErrors({});
    setConfirmDeleteId(null);
  };

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleStartEdit = (rt: Route) => {
    if (isSubmitting) return;
    setSelectedRoute(rt);
    setCode(rt.code);
    setName(rt.name);
    setDescription(rt.description || '');
    setOrderedStationIds(rt.stations.map((s) => s.station.id));
    setStationToAdd('');
    setFormError(null);
    setFieldErrors({});
    setConfirmDeleteId(null);
  };

  const handleCancelEdit = () => {
    if (isSubmitting) return;
    resetForm();
  };

  const handleAddStation = () => {
    if (stationToAdd === '' || isSubmitting) return;
    setOrderedStationIds((prev) => [...prev, stationToAdd]);
    setStationToAdd('');
    setFormError(null);
  };

  const handleRemoveStation = (index: number) => {
    if (isSubmitting) return;
    setOrderedStationIds((prev) => prev.filter((_, i) => i !== index));
    setFormError(null);
  };

  const handleMoveUp = (index: number) => {
    if (index === 0 || isSubmitting) return;
    setOrderedStationIds((prev) => {
      const next = [...prev];
      const temp = next[index - 1];
      next[index - 1] = next[index];
      next[index] = temp;
      return next;
    });
    setFormError(null);
  };

  const handleMoveDown = (index: number) => {
    if (index === orderedStationIds.length - 1 || isSubmitting) return;
    setOrderedStationIds((prev) => {
      const next = [...prev];
      const temp = next[index + 1];
      next[index + 1] = next[index];
      next[index] = temp;
      return next;
    });
    setFormError(null);
  };

  // Kiểm tra quy tắc BR-001 và BR-002
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    const trimmedName = name.trim();
    if (!trimmedName) {
      errors.name = 'Tên tuyến không được để trống';
    } else if (trimmedName.length > 150) {
      errors.name = 'Tên tuyến không được vượt quá 150 ký tự';
    }

    if (code.trim().length > 50) {
      errors.code = 'Mã tuyến không được vượt quá 50 ký tự';
    }

    if (description.trim().length > 500) {
      errors.description = 'Mô tả không được vượt quá 500 ký tự';
    }

    if (orderedStationIds.length < 2) {
      errors.stations = 'Tuyến phải có ít nhất 2 trạm dừng (trạm đầu và trạm cuối)';
    } else {
      const firstSt = stationMap.get(orderedStationIds[0]);
      const lastSt = stationMap.get(orderedStationIds[orderedStationIds.length - 1]);

      if (!firstSt || firstSt.stationType !== 'START') {
        errors.stations = 'Tuyến phải bắt đầu bằng trạm START';
      } else if (!lastSt || lastSt.stationType !== 'END') {
        errors.stations = 'Tuyến phải kết thúc bằng trạm END';
      } else {
        for (let i = 1; i < orderedStationIds.length - 1; i++) {
          const middleSt = stationMap.get(orderedStationIds[i]);
          if (!middleSt || middleSt.stationType !== 'STOP') {
            errors.stations = 'Tất cả các trạm giữa trạm đầu và trạm cuối phải có loại STOP';
            break;
          }
        }
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    if (!validateForm()) {
      return;
    }

    const payload: RouteRequest = {
      name: name.trim(),
      stationIds: orderedStationIds,
    };

    if (code.trim()) {
      payload.code = code.trim();
    }
    if (description.trim()) {
      payload.description = description.trim();
    }

    setIsSubmitting(true);
    setFormError(null);

    try {
      if (selectedRoute) {
        await onUpdateRoute(selectedRoute.id, payload);
        resetForm();
      } else {
        await onCreateRoute(payload);
        resetForm();
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Đã xảy ra lỗi';
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
      await onDeleteRoute(id);
      setConfirmDeleteId(null);
      if (selectedRoute && selectedRoute.id === id) {
        resetForm();
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể xóa tuyến';
      setFormError(msg);
    } finally {
      setIsSubmitting(false);
      setDeletingId(null);
    }
  };

  const getTypeBadge = (stType: StationType) => {
    switch (stType) {
      case 'START':
        return <span className="glass-badge emerald" style={{ fontSize: '0.65rem' }}>START</span>;
      case 'END':
        return <span className="glass-badge crimson" style={{ fontSize: '0.65rem' }}>END</span>;
      case 'STOP':
      default:
        return <span className="glass-badge cyan" style={{ fontSize: '0.65rem' }}>STOP</span>;
    }
  };

  // Đánh giá trạng thái quy tắc trạm theo thời gian thực
  const getRuleValidationNotice = () => {
    if (orderedStationIds.length === 0) {
      return { status: 'neutral', text: 'Chưa có trạm nào. Cần ít nhất 2 trạm (START → END).' };
    }
    if (orderedStationIds.length === 1) {
      return { status: 'warning', text: 'Cần thêm trạm. Tuyến phải kết thúc bằng trạm END.' };
    }
    const first = stationMap.get(orderedStationIds[0]);
    const last = stationMap.get(orderedStationIds[orderedStationIds.length - 1]);

    if (!first || first.stationType !== 'START') {
      return { status: 'error', text: 'Lỗi: Trạm đầu tiên (#1) phải là trạm START.' };
    }
    if (!last || last.stationType !== 'END') {
      return { status: 'error', text: 'Lỗi: Trạm cuối cùng phải là trạm END.' };
    }
    for (let i = 1; i < orderedStationIds.length - 1; i++) {
      const mid = stationMap.get(orderedStationIds[i]);
      if (!mid || mid.stationType !== 'STOP') {
        return { status: 'error', text: `Lỗi: Trạm #${i + 1} (${mid?.name || 'Trạm'}) phải là STOP.` };
      }
    }
    return { status: 'success', text: 'Cấu hình trạm hợp lệ (START → STOP* → END).' };
  };

  const ruleNotice = getRuleValidationNotice();

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
          width: '780px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          animation: 'slideInRight 0.2s ease',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid var(--border-glass)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <RouteIcon size={20} color="#00f0ff" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>
              Quản Lý Tuyến Đường Xe Buýt
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

        {/* Body scrollable */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '20px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Lỗi chung từ API hoặc server */}
          {formError && (
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: '10px',
                padding: '12px 16px',
                background: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid rgba(239, 68, 68, 0.4)',
                borderRadius: '8px',
                color: '#ef4444',
                fontSize: '0.85rem',
              }}
            >
              <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <span style={{ fontWeight: 600 }}>Thao tác thất bại: </span>
                <span>{formError}</span>
              </div>
            </div>
          )}

          {/* Form Create / Edit */}
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: '0.9rem', fontWeight: 700, color: '#00f0ff' }}>
                {selectedRoute ? (
                  <span>
                    Chỉnh sửa tuyến #{selectedRoute.id}: {selectedRoute.name}
                  </span>
                ) : (
                  <span>+ Tạo tuyến đường mới</span>
                )}
              </div>
              {selectedRoute && (
                <button
                  type="button"
                  onClick={handleCancelEdit}
                  disabled={isSubmitting}
                  className="glass-btn"
                  style={{ fontSize: '0.75rem', padding: '4px 10px' }}
                >
                  <RotateCcw size={12} />
                  <span>Hủy sửa / Tạo mới</span>
                </button>
              )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#94a3b8', marginBottom: '4px' }}>
                  Mã tuyến (Tự sinh nếu trống)
                </label>
                <input
                  type="text"
                  className="input-field"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="VD: ROUTE-01"
                  disabled={isSubmitting}
                  maxLength={50}
                  style={{ width: '100%' }}
                />
                {fieldErrors.code && (
                  <span style={{ fontSize: '0.7rem', color: '#ef4444', marginTop: '2px', display: 'block' }}>
                    {fieldErrors.code}
                  </span>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#94a3b8', marginBottom: '4px' }}>
                  Tên tuyến đường <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="text"
                  className="input-field"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="VD: Tuyến 01: Bến Thành - Chợ Lớn"
                  disabled={isSubmitting}
                  maxLength={150}
                  style={{ width: '100%' }}
                />
                {fieldErrors.name && (
                  <span style={{ fontSize: '0.7rem', color: '#ef4444', marginTop: '2px', display: 'block' }}>
                    {fieldErrors.name}
                  </span>
                )}
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', color: '#94a3b8', marginBottom: '4px' }}>
                Mô tả chi tiết (Tùy chọn)
              </label>
              <textarea
                className="input-field"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Thông tin lộ trình, thời gian hoạt động, lưu ý..."
                disabled={isSubmitting}
                maxLength={500}
                rows={2}
                style={{ width: '100%', resize: 'none' }}
              />
            </div>

            {/* Station sequence builder */}
            <div style={{ border: '1px solid var(--border-glass)', borderRadius: '8px', padding: '12px', background: 'rgba(0,0,0,0.2)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#e2e8f0' }}>
                  Danh sách trạm dừng theo thứ tự di chuyển ({orderedStationIds.length} trạm)
                </span>
                <div
                  style={{
                    fontSize: '0.72rem',
                    color:
                      ruleNotice.status === 'success'
                        ? '#10b981'
                        : ruleNotice.status === 'error'
                        ? '#ef4444'
                        : '#94a3b8',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  {ruleNotice.text}
                </div>
              </div>

              {/* Station selector to add */}
              <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
                <select
                  className="input-field"
                  value={stationToAdd}
                  onChange={(e) => setStationToAdd(e.target.value ? Number(e.target.value) : '')}
                  disabled={isSubmitting}
                  style={{ flex: 1, padding: '6px 10px', fontSize: '0.8rem' }}
                >
                  <option value="">-- Chọn trạm dừng để thêm vào tuyến --</option>
                  {stations.map((st) => (
                    <option key={st.id} value={st.id}>
                      [{st.stationType}] {st.name} ({st.code})
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="glass-btn primary"
                  onClick={handleAddStation}
                  disabled={stationToAdd === '' || isSubmitting}
                  style={{ fontSize: '0.75rem', padding: '6px 12px' }}
                >
                  <Plus size={14} />
                  <span>Thêm trạm</span>
                </button>
              </div>

              {fieldErrors.stations && (
                <div style={{ fontSize: '0.75rem', color: '#ef4444', marginBottom: '8px' }}>
                  {fieldErrors.stations}
                </div>
              )}

              {/* Station list in order */}
              <div style={{ maxHeight: '180px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {orderedStationIds.length === 0 ? (
                  <div style={{ padding: '16px', textAlign: 'center', color: '#64748b', fontSize: '0.78rem' }}>
                    Chưa có trạm nào trong tuyến. Hãy chọn trạm ở trên và nhấn "Thêm trạm".
                  </div>
                ) : (
                  orderedStationIds.map((stId, index) => {
                    const st = stationMap.get(stId);
                    return (
                      <div
                        key={`${stId}-${index}`}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '6px 10px',
                          background: 'rgba(255, 255, 255, 0.04)',
                          borderRadius: '6px',
                          border: '1px solid rgba(255, 255, 255, 0.05)',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <span
                            style={{
                              width: '22px',
                              height: '22px',
                              borderRadius: '50%',
                              background: 'rgba(0, 240, 255, 0.15)',
                              color: '#00f0ff',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '0.75rem',
                              fontWeight: 700,
                            }}
                          >
                            {index + 1}
                          </span>
                          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#f1f5f9' }}>
                            {st ? st.name : `Trạm ID #${stId}`}
                          </span>
                          {st && getTypeBadge(st.stationType)}
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <button
                            type="button"
                            onClick={() => handleMoveUp(index)}
                            disabled={index === 0 || isSubmitting}
                            className="glass-btn"
                            style={{
                              padding: '3px 6px',
                              opacity: index === 0 ? 0.3 : 1,
                              cursor: index === 0 ? 'default' : 'pointer',
                            }}
                            title="Di chuyển lên"
                          >
                            <ArrowUp size={12} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleMoveDown(index)}
                            disabled={index === orderedStationIds.length - 1 || isSubmitting}
                            className="glass-btn"
                            style={{
                              padding: '3px 6px',
                              opacity: index === orderedStationIds.length - 1 ? 0.3 : 1,
                              cursor: index === orderedStationIds.length - 1 ? 'default' : 'pointer',
                            }}
                            title="Di chuyển xuống"
                          >
                            <ArrowDown size={12} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleRemoveStation(index)}
                            disabled={isSubmitting}
                            className="glass-btn danger"
                            style={{ padding: '3px 6px' }}
                            title="Xóa khỏi tuyến"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '4px' }}>
              <button
                type="submit"
                className="glass-btn primary"
                disabled={isSubmitting}
                style={{ minWidth: '130px', justifyContent: 'center' }}
              >
                <Check size={16} />
                <span>{isSubmitting ? 'Đang lưu...' : selectedRoute ? 'Cập Nhật Tuyến' : 'Lưu Tuyến Mới'}</span>
              </button>
            </div>
          </form>

          {/* Danh sách các tuyến hiện có */}
          <div style={{ borderTop: '1px solid var(--border-glass)', paddingTop: '16px' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '12px' }}>
              Các tuyến đường hiện có ({routes.length})
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {routes.map((rt) => (
                <div
                  key={rt.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    borderRadius: '8px',
                    background: selectedRoute?.id === rt.id ? 'rgba(0, 240, 255, 0.1)' : 'rgba(255, 255, 255, 0.03)',
                    border: selectedRoute?.id === rt.id ? '1px solid #00f0ff' : '1px solid rgba(255, 255, 255, 0.06)',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontWeight: 700, color: '#fff', fontSize: '0.85rem' }}>{rt.name}</span>
                      <span className="glass-badge" style={{ fontSize: '0.65rem' }}>{rt.code}</span>
                    </div>
                    <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '2px' }}>
                      {rt.stations.length} trạm dừng • {rt.totalDistanceKm} km • ~{rt.estimatedDurationMinutes} phút
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <button
                      type="button"
                      className="glass-btn"
                      onClick={() => handleStartEdit(rt)}
                      disabled={isSubmitting}
                      style={{ fontSize: '0.75rem', padding: '4px 8px' }}
                    >
                      <Edit2 size={12} />
                      <span>Sửa</span>
                    </button>

                    {confirmDeleteId === rt.id ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <button
                          type="button"
                          className="glass-btn danger"
                          onClick={() => handleConfirmDelete(rt.id)}
                          disabled={isSubmitting}
                          style={{ fontSize: '0.72rem', padding: '4px 8px' }}
                        >
                          {deletingId === rt.id ? 'Đang xóa...' : 'Xác nhận xóa'}
                        </button>
                        <button
                          type="button"
                          className="glass-btn"
                          onClick={() => setConfirmDeleteId(null)}
                          disabled={isSubmitting}
                          style={{ fontSize: '0.72rem', padding: '4px 6px' }}
                        >
                          Hủy
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="glass-btn danger"
                        onClick={() => setConfirmDeleteId(rt.id)}
                        disabled={isSubmitting}
                        style={{ fontSize: '0.75rem', padding: '4px 8px' }}
                      >
                        <Trash2 size={12} />
                        <span>Xóa</span>
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function RouteModal({
  isOpen,
  onClose,
  routes,
  stations,
  onCreateRoute,
  onUpdateRoute,
  onDeleteRoute,
}: RouteModalProps) {
  if (!isOpen) return null;

  return (
    <RouteModalContent
      key={isOpen ? 'open' : 'closed'}
      onClose={onClose}
      routes={routes}
      stations={stations}
      onCreateRoute={onCreateRoute}
      onUpdateRoute={onUpdateRoute}
      onDeleteRoute={onDeleteRoute}
    />
  );
}
