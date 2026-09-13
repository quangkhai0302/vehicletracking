import { useMemo, useState } from 'react';
import {
  ArrowUpDown,
  Edit3,
  MapPin,
  Plus,
  Search,
  Trash2,
} from 'lucide-react';
import type { Station, StationFormMode } from '../types/station';

export type StationSortBy = 'NAME' | 'RADIUS';

interface StationPanelProps {
  stations: Station[];
  selectedStationId: number | null;
  loading: boolean;
  error: string | null;
  selectionDisabled: boolean;
  mode?: StationFormMode;
  onBeginCreate: () => void;
  onSelect: (station: Station) => void;
  onBeginEdit?: (station: Station) => void;
  onDelete?: (station: Station) => void;
}

export function StationPanel({
  stations,
  selectedStationId,
  loading,
  error,
  selectionDisabled,
  mode = 'closed',
  onBeginCreate,
  onSelect,
  onBeginEdit,
  onDelete,
}: StationPanelProps) {
  const [query, setQuery] = useState('');
  const [sortBy, setSortBy] = useState<StationSortBy>('NAME');

  const filteredStations = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('vi');
    let list = stations.filter((station) => {
      if (normalizedQuery) {
        const matchName = station.name.toLocaleLowerCase('vi').includes(normalizedQuery);
        const matchAddress = station.address?.toLocaleLowerCase('vi').includes(normalizedQuery);
        return matchName || matchAddress;
      }
      return true;
    });

    if (sortBy === 'NAME') {
      list = [...list].sort((a, b) => a.name.localeCompare(b.name, 'vi'));
    } else if (sortBy === 'RADIUS') {
      list = [...list].sort((a, b) => b.checkinRadiusMeters - a.checkinRadiusMeters);
    }

    return list;
  }, [query, sortBy, stations]);

  return (
    <aside className="station-panel" aria-label="Danh sách trạm">
      {/* Header */}
      <div className="station-panel-header">
        <div>
          <div className="panel-eyebrow">Dữ liệu vận hành</div>
          <h2>Danh sách trạm</h2>
        </div>
        <span className="count-badge tabular-numbers" title="Số trạm hiển thị / Tổng số trạm">
          {filteredStations.length} / {stations.length}
        </span>
      </div>

      {/* Thanh công cụ tìm kiếm và sắp xếp */}
      <div className="station-toolbar">
        <label className="station-search flex-1">
          <Search size={15} aria-hidden="true" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Tìm theo tên trạm hoặc địa chỉ..."
            aria-label="Tìm kiếm trạm"
          />
        </label>

        <button
          type="button"
          className={`station-sort-toggle-btn ${sortBy === 'RADIUS' ? 'active' : ''}`}
          onClick={() => setSortBy((cur) => (cur === 'NAME' ? 'RADIUS' : 'NAME'))}
          title={sortBy === 'NAME' ? 'Đang xếp theo Tên (A-Z). Bấm để xếp theo Bán kính' : 'Đang xếp theo Bán kính. Bấm để xếp theo Tên'}
          aria-label="Đổi thứ tự sắp xếp"
        >
          <ArrowUpDown size={14} />
          <span>{sortBy === 'NAME' ? 'Tên A-Z' : 'Bán kính'}</span>
        </button>
      </div>

      {error && <div className="station-alert" role="alert">{error}</div>}

      {/* Danh sách trạm */}
      <div className="station-list" aria-live="polite">
        {loading && <div className="station-empty">Đang tải danh sách trạm…</div>}
        {!loading && !error && stations.length === 0 && (
          <div className="station-empty">
            <MapPin size={26} aria-hidden="true" />
            <strong>Chưa có trạm nào</strong>
            <span>Nhấn “Thêm trạm” ở bên dưới để đặt trạm đầu tiên lên bản đồ.</span>
          </div>
        )}
        {!loading && stations.length > 0 && filteredStations.length === 0 && (
          <div className="station-empty compact">Không tìm thấy trạm phù hợp với tìm kiếm.</div>
        )}

        {!loading &&
          filteredStations.map((station) => {
            const isSelected = selectedStationId === station.id;

            return (
              <div
                key={station.id}
                className={`station-card ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelect(station)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.target !== event.currentTarget) return;
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onSelect(station);
                  }
                }}
                aria-pressed={isSelected}
              >
                <div className="station-card-top">
                  <div className="station-card-title-row">
                    <span
                      className="status-dot active"
                      title="Đang hoạt động"
                      aria-label="Đang hoạt động"
                    />
                    <strong className="station-card-name" title={station.name}>
                      {station.name}
                    </strong>
                  </div>

                  {/* Nút hành động Sửa & Xóa */}
                  <div className="station-card-actions">
                    {onBeginEdit && (
                      <button
                        type="button"
                        className="card-action-btn"
                        onClick={(event) => {
                          event.stopPropagation();
                          onBeginEdit(station);
                        }}
                        onKeyDown={(event) => {
                          event.stopPropagation();
                        }}
                        disabled={selectionDisabled}
                        title="Chỉnh sửa trạm này"
                        aria-label={`Sửa trạm ${station.name}`}
                      >
                        <Edit3 size={13} />
                      </button>
                    )}
                    {onDelete && (
                      <button
                        type="button"
                        className="card-action-btn danger"
                        onClick={(event) => {
                          event.stopPropagation();
                          onDelete(station);
                        }}
                        onKeyDown={(event) => {
                          event.stopPropagation();
                        }}
                        disabled={selectionDisabled}
                        title="Ngừng sử dụng trạm này"
                        aria-label={`Ngừng sử dụng trạm ${station.name}`}
                      >
                        <Trash2 size={13} />
                      </button>
                    )}
                  </div>
                </div>

                {station.address && (
                  <p className="station-card-address" title={station.address}>
                    {station.address}
                  </p>
                )}

                <div className="station-card-body">
                  <div className="station-card-meta tabular-numbers">
                    <span className="station-meta-coords">
                      {station.latitude.toFixed(5)}, {station.longitude.toFixed(5)}
                    </span>
                    <span className="station-meta-radius">
                      Bán kính: {station.checkinRadiusMeters} m
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
      </div>

      {/* Footer & Nút Thêm trạm */}
      <div className="station-panel-bottom">
        <button
          type="button"
          className="add-station-dashed-btn"
          onClick={onBeginCreate}
          disabled={selectionDisabled}
          aria-label="Thêm trạm mới"
        >
          <Plus size={16} /> Thêm trạm mới
        </button>
        <div className="station-panel-footer-mode">
          <span className="station-count-summary">{stations.length} trạm trong hệ thống</span>
          {mode === 'create' && <span className="mode-indicator create">Đang tạo trạm mới</span>}
          {mode === 'edit' && <span className="mode-indicator edit">Đang chỉnh sửa trạm</span>}
        </div>
      </div>
    </aside>
  );
}
