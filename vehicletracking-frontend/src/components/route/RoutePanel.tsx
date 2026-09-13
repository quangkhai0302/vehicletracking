import { useMemo, useState } from 'react';
import {
  AlertCircle,
  Clock,
  MapPin,
  Milestone,
  Plus,
  RefreshCw,
  Route,
  Search,
} from 'lucide-react';
import type { RouteSummary } from '../../types/route';
import { formatDuration } from '../../utils/format';

interface RoutePanelProps {
  routes: RouteSummary[];
  selectedRouteId: number | null;
  loading: boolean;
  error: string | null;
  onSelectRoute: (route: RouteSummary) => void;
  onBeginCreate: () => void;
  onRetry: () => void;
}

export function RoutePanel({
  routes,
  selectedRouteId,
  loading,
  error,
  onSelectRoute,
  onBeginCreate,
  onRetry,
}: RoutePanelProps) {
  const [query, setQuery] = useState('');

  const filteredRoutes = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('vi');
    if (!normalized) return routes;
    return routes.filter((r) => {
      const matchName = r.name.toLocaleLowerCase('vi').includes(normalized);
      const matchStart = r.startStationName?.toLocaleLowerCase('vi').includes(normalized);
      const matchEnd = r.endStationName?.toLocaleLowerCase('vi').includes(normalized);
      return matchName || matchStart || matchEnd;
    });
  }, [query, routes]);

  return (
    <aside className="route-panel" aria-label="Danh sách tuyến đường">
      <div className="route-panel-header">
        <div>
          <div className="panel-eyebrow">Dữ liệu vận hành</div>
          <h2>Tuyến đường cố định</h2>
        </div>
        <span className="count-badge tabular-numbers" title="Số tuyến hiển thị / Tổng số tuyến">
          {filteredRoutes.length}/{routes.length}
        </span>
      </div>

      <div className="route-panel-actions">
        <div className="route-search-box">
          <Search size={15} />
          <input
            type="search"
            className="route-search-input"
            placeholder="Tìm theo tên tuyến, trạm..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <button
          type="button"
          className="btn-primary-create"
          onClick={onBeginCreate}
          title="Tạo tuyến đường mới"
        >
          <Plus size={15} />
          <span>Tạo tuyến</span>
        </button>
      </div>

      <div className="route-list">
        {loading && (
          <div className="panel-loading" role="status">
            <RefreshCw size={18} className="animate-spin" />
            <span>Đang tải danh sách tuyến đường...</span>
          </div>
        )}

        {!loading && error && (
          <div className="panel-error" role="alert">
            <AlertCircle size={20} />
            <div className="panel-error-content">
              <strong>Không thể tải dữ liệu</strong>
              <p>{error}</p>
              <button type="button" className="btn-retry" onClick={onRetry}>
                <RefreshCw size={14} /> Thử lại
              </button>
            </div>
          </div>
        )}

        {!loading && !error && filteredRoutes.length === 0 && (
          <div className="panel-empty">
            <Route size={32} className="empty-icon" />
            <h3>Chưa có tuyến đường nào</h3>
            <p>
              {query
                ? 'Không tìm thấy tuyến đường phù hợp với từ khóa.'
                : 'Bắt đầu tạo tuyến đường cố định đầu tiên bằng cách chọn danh sách điểm dừng.'}
            </p>
            {!query && (
              <button type="button" className="btn-primary-create" onClick={onBeginCreate}>
                <Plus size={15} /> Tạo tuyến ngay
              </button>
            )}
          </div>
        )}

        {!loading &&
          !error &&
          filteredRoutes.map((route) => {
            const isSelected = route.id === selectedRouteId;
            const distanceKm = (route.totalDistanceMeters / 1000).toFixed(1);
            const durationFormatted = formatDuration(route.estimatedTripDurationSeconds);

            return (
              <article
                key={route.id}
                className={`route-card ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelectRoute(route)}
                tabIndex={0}
                role="button"
                aria-pressed={isSelected}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onSelectRoute(route);
                  }
                }}
              >
                <div className="route-card-header">
                  <div className="route-card-name">{route.name}</div>
                  <span className="count-badge tabular-numbers">{route.stopCount} trạm</span>
                </div>

                <div className="route-card-endpoints">
                  <MapPin size={13} className="text-emerald" />
                  <span>{route.startStationName || 'Điểm đầu'}</span>
                  <span className="text-muted">→</span>
                  <MapPin size={13} className="text-red" />
                  <span>{route.endStationName || 'Điểm cuối'}</span>
                </div>

                <div className="route-card-metrics tabular-numbers">
                  <span className="route-metric-item">
                    <Milestone size={12} /> {distanceKm} km
                  </span>
                  <span className="route-metric-item">
                    <Clock size={12} /> {durationFormatted}
                  </span>
                  <span className="route-metric-item text-muted">
                    HERE CAR
                  </span>
                </div>
              </article>
            );
          })}
      </div>
    </aside>
  );
}
