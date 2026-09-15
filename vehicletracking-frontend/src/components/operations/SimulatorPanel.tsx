import { lazy, Suspense, useState } from 'react';
import { Car, Pause, Play, RotateCcw, Square } from 'lucide-react';
import type { useSimulator } from '../../hooks/useSimulator';
import type { OperationsSnapshot, StreamConnection } from '../../types/operations';
import { SIMULATION_LABELS } from '../../types/operations';
import { displayTripTime } from '../../utils/tripTime';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';
import type { useSimulationFleet } from '../../hooks/useSimulationFleet';
import './simulator.css';

const SimulationFleetList = lazy(() => import('./SimulationFleetList').then(module=>({default:module.SimulationFleetList})));

export function SimulatorPanel({ simulator, snapshot, connection, connectionError, onReconnect, onShowRoute, fleet, onSelectVehicle, onFitFleet, onManageFleet }: {
  simulator: ReturnType<typeof useSimulator>; snapshot: OperationsSnapshot | null; connection: StreamConnection;
  connectionError: string | null; onReconnect: () => void; onShowRoute: () => void; now: number;
  fleet?: ReturnType<typeof useSimulationFleet>; onSelectVehicle?: (tripId: number) => void;
  onFitFleet?: () => void; onManageFleet?: () => void;
}) {
  const [confirm, setConfirm] = useState<'stop' | 'reset' | null>(null);
  const { trip, run, detail, busy, loading, error } = simulator;
  const running = run?.status === 'RUNNING';
  const active = trip?.status === 'SCHEDULED' || trip?.status === 'IN_PROGRESS';
  const usingGps = snapshot?.positions.some(point => point.tripId === trip?.id && point.source === 'GPS');
  const canPlay = !!trip && !usingGps && active && (!run || run.status === 'PAUSED' || running);
  const frame = run?.frame;
  const options = snapshot?.trips ?? [];
  const canControl = !busy && !loading && connection === 'live';
  const playLabel = running ? 'Tạm dừng' : run?.status === 'PAUSED' && trip?.status === 'IN_PROGRESS' ? 'Tiếp tục' : 'Bắt đầu';
  return <section className="simulator-content" aria-label="Điều khiển mô phỏng">
    <div className="simulation-connection" data-state={connection} hidden={connection === 'live'}><span>{connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · đang thử lại'}</span>
      {connection !== 'live' && <button onClick={onReconnect}>Kết nối lại</button>}</div>
    {connectionError && <p className="simulation-error" role="alert">{connectionError}</p>}
    {fleet && onSelectVehicle && onFitFleet && onManageFleet && <Suspense fallback={<p>Đang mở đội xe…</p>}><SimulationFleetList fleet={fleet} snapshot={snapshot}
      selectedTripId={simulator.tripId} disabled={busy} onSelect={onSelectVehicle} onFit={onFitFleet} onManage={onManageFleet} /></Suspense>}
    <label className="simulation-select">Chuyến mô phỏng
      <select aria-label="Chọn chuyến mô phỏng" disabled={busy || !snapshot} value={simulator.tripId ?? ''} onChange={e => {
        if (e.target.value && onSelectVehicle) onSelectVehicle(Number(e.target.value));
        else simulator.select(e.target.value ? Number(e.target.value) : null);
      }}>
        <option value="">Chọn xe và chuyến đi…</option>
        {simulator.tripId && !options.some(item => item.id === simulator.tripId) && <option value={simulator.tripId}>Chuyến #{simulator.tripId}</option>}
        {options.map(item => <option key={item.id} value={item.id}>#{item.id} · {item.vehiclePlateNumber} · {item.routeName}</option>)}
      </select>
    </label>
    {!trip && !loading && <div className="telemetry-placeholder"><Car size={26} /><div><strong>Chọn xe để điều khiển</strong><span>Xe chờ nằm ở trạm đầu. Bấm từng xe để bắt đầu, tạm dừng hoặc đổi tốc độ phát.</span></div></div>}
    {loading && <p className="availability-note" role="status">Đang tải tuyến mô phỏng…</p>}
    {error && !confirm && <div className="simulation-error" role="alert">{error} <button disabled={busy} onClick={simulator.retry}>Tải lại chuyến</button></div>}
    {trip && <div className="simulation-summary"><strong>#{trip.id} · {trip.vehiclePlateNumber}</strong><span data-testid="simulation-status">{run ? SIMULATION_LABELS[run.status] : active ? 'Sẵn sàng' : 'Chuyến đã kết thúc'}</span></div>}
    {trip && <p className="availability-note">{usingGps ? 'Xe đang được theo dõi bằng GPS, không chạy mô phỏng.' : 'Chế độ mô phỏng · Không phải hành trình thực tế'}</p>}
    {trip && <>
      {!run && trip.status==='SCHEDULED' ? detail?.stops[0] ? <div className="simulation-ready" role="status">
        <strong>Chờ xuất phát</strong><p>Trạm đầu: {detail.stops[0].stationName}</p>
      </div> : <p role="status">Đang tải trạm đầu của xe…</p>
        : null}
      <button className="fleet-text-button simulation-locate" disabled={loading || !detail} onClick={onShowRoute}>Xem vị trí xe và tuyến đang chạy</button>
    </>}
    <fieldset disabled={!canControl}>
      <legend>Điều khiển mô phỏng · tốc độ phát</legend>
      <div className="playback-row">
        <button className="play-button" disabled={!canPlay} aria-label={running ? 'Tạm dừng mô phỏng' : `${playLabel} mô phỏng`} onClick={() => void simulator.command(running ? 'pause' : 'play')}>{running ? <Pause size={16} /> : <Play size={16} />}<span>{playLabel}</span></button>
        {([1,5,10] as const).map(speed => <button key={speed} disabled={!run || !active || (run.status !== 'RUNNING' && run.status !== 'PAUSED')} aria-label={`Tốc độ ${speed}x`} aria-pressed={run?.multiplier === speed} onClick={() => void simulator.command('speed',speed)}>{speed}x</button>)}
      </div>
      <div className="simulation-actions">
        <button className="btn-secondary" disabled={!run || !active} onClick={() => setConfirm('stop')}><Square size={13} />Dừng & hủy chuyến</button>
        <button className="btn-secondary" disabled={!run} onClick={() => setConfirm('reset')}><RotateCcw size={13} />Chạy lại</button>
      </div>
    </fieldset>
    {trip && <p className="availability-note">1× / 5× / 10× là tốc độ phát, không phải vận tốc xe.</p>}
    {run?.traffic?.blocked && <p className="simulation-error" role="status">Đường phía trước bị chặn, chưa xác định thời gian đến.</p>}
    {(run?.traffic?.status === 'STALE' || run?.traffic?.status === 'UNAVAILABLE') && <p role="status">Dữ liệu giao thông chưa được cập nhật.</p>}
    {run && <div className="simulation-times">
      <div>{frame?.finished ? 'Đã đi hết tuyến' : frame?.dwelling ? 'Đang dừng tại trạm theo lịch tuyến' : 'Xe di chuyển theo tuyến của chuyến'}</div>
      <details className="trip-traffic-details"><summary>Thông tin kỹ thuật</summary>
        <div>Đồng hồ mô phỏng: <time data-testid="simulation-clock">{displayTripTime(run.simulatedAt)}</time></div>
        <div>Đã chạy: <span data-testid="simulation-elapsed">{run.elapsedSeconds.toFixed(1)}</span> / {run.durationSeconds} giây mô phỏng</div>
      </details>
      {run.status === 'FAILED' && run.errorMessage && <div className="simulation-error" role="alert">
        <span>Mô phỏng gặp lỗi. Kiểm tra tuyến rồi chọn Chạy lại.</span>
        <details><summary>Chi tiết lỗi</summary>{run.errorMessage}</details>
        <button type="button" className="simulation-retry-action" disabled={!canControl} onClick={() => setConfirm('reset')}>
          Chạy lại chuyến
        </button>
      </div>}
    </div>}
    <div className="trip-progress"><span>Tiến độ tuyến <b>{frame ? `${Math.round(frame.progressPercent)}%` : '—'}</b></span><div className="progress-track" role="progressbar" aria-label="Tiến độ tuyến mô phỏng" aria-valuenow={frame?.progressPercent ?? 0} aria-valuemin={0} aria-valuemax={100}><span style={{ width: `${frame?.progressPercent ?? 0}%` }} /></div></div>
    {confirm && <FleetConfirmDialog title={confirm === 'stop' ? 'Dừng mô phỏng và hủy chuyến?' : 'Chạy lại chuyến này từ đầu?'}
      message={confirm === 'stop' ? 'Xe dừng mô phỏng và chuyến hiện tại được hủy. Lịch sử vị trí vẫn được lưu.' : 'Giữ nguyên mã chuyến và tuyến. Xe trở về trạm đầu, chờ bạn bấm Bắt đầu. Lịch sử và check-in được lưu riêng theo từng lần chạy.'}
      confirmLabel={confirm === 'stop' ? 'Xác nhận dừng chuyến' : 'Đặt lại chuyến'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => void simulator.command(confirm).then(ok => { if (ok) setConfirm(null); })} />}
  </section>;
}
