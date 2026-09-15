import { lazy, Suspense, useState } from 'react';
import { Car, Pause, Play, RotateCcw, Square } from 'lucide-react';
import type { useSimulator } from '../../hooks/useSimulator';
import type { OperationsSnapshot, StreamConnection } from '../../types/operations';
import { SIMULATION_LABELS } from '../../types/operations';
import { displayTripTime } from '../../utils/tripTime';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';
import { TripTrafficSummary } from './TripTrafficSummary';
import type { useSimulationFleet } from '../../hooks/useSimulationFleet';
import './simulator.css';

const SimulationFleetList = lazy(() => import('./SimulationFleetList').then(module=>({default:module.SimulationFleetList})));

export function SimulatorPanel({ simulator, snapshot, connection, connectionError, onReconnect, onShowRoute, now, fleet, onSelectVehicle, onFitFleet, onManageFleet }: {
  simulator: ReturnType<typeof useSimulator>; snapshot: OperationsSnapshot | null; connection: StreamConnection;
  connectionError: string | null; onReconnect: () => void; onShowRoute: () => void; now: number;
  fleet?: ReturnType<typeof useSimulationFleet>; onSelectVehicle?: (tripId: number) => void;
  onFitFleet?: () => void; onManageFleet?: () => void;
}) {
  const [confirm, setConfirm] = useState<'stop' | 'reset' | null>(null);
  const { trip, run, detail, busy, loading, error } = simulator;
  const running = run?.status === 'RUNNING';
  const active = trip?.status === 'SCHEDULED' || trip?.status === 'IN_PROGRESS';
  const canPlay = !!trip && active && (!run || run.status === 'PAUSED' || running);
  const frame = run?.frame;
  const options = snapshot?.trips ?? [];
  const canControl = !busy && !loading && connection === 'live';
  const playLabel = running ? 'Tạm dừng' : run?.status === 'PAUSED' && trip?.status === 'IN_PROGRESS' ? 'Tiếp tục' : 'Bắt đầu';
  const checkin = trip && Array.isArray(snapshot?.checkIns) ? snapshot.checkIns.find(item => item.tripId === trip.id) : null;
  const latestCheckin = checkin?.visits[checkin.visits.length - 1];
  return <section className="simulator-content" aria-label="Điều khiển mô phỏng">
    <div className="simulation-connection" data-state={connection}><span>{connection === 'live' ? 'Đã kết nối realtime' : connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · đang thử lại'}</span>
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
    {trip && <>
      {!run && trip.status==='SCHEDULED' ? detail?.stops[0] ? <div className="simulation-ready" role="status">
        <strong>Chờ xuất phát · 0 km/h · GIẢ LẬP</strong><p>Trạm đầu: {detail.stops[0].stationName}</p>
        <p>Vị trí chờ theo lịch trình. Bấm Bắt đầu để xe chạy và cập nhật ETA theo giao thông.</p>
      </div> : <p role="status">Đang tải trạm đầu của xe…</p>
        : <TripTrafficSummary tripId={trip.id} run={run} stops={detail?.stops} now={now} connection={connection} />}
      <button className="fleet-text-button simulation-locate" disabled={loading || !detail} onClick={onShowRoute}>Xem vị trí xe và tuyến đang chạy</button>
    </>}
    {trip && <p className="availability-note simulation-guide">
      {running ? 'Xe đang chạy. Chọn 5× hoặc 10× để xem nhanh; Tạm dừng để nghỉ rồi Tiếp tục.'
        : canPlay ? `${playLabel} để xe chạy theo tuyến đã lưu. Hệ thống tự ghi nhận khi xe đi qua trạm.`
          : run ? 'Phiên này đã kết thúc hoặc gặp lỗi. Chọn Chạy lại, xác nhận tạo chuyến mới, rồi bấm Bắt đầu.'
            : 'Chuyến đã kết thúc. Tạo chuyến mới trong Đội xe để mô phỏng.'}
    </p>}
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
    {trip && <p className="availability-note">1× / 5× / 10× là tốc độ phát mô phỏng. Vận tốc xe và ETA cập nhật theo dữ liệu giao thông.</p>}
    {trip && <div className="trip-checkin-summary">
      {snapshot && !Array.isArray(snapshot.checkIns) ? 'Backend chưa hỗ trợ dữ liệu check-in.' : <>
        <span>Đã ghi nhận {checkin?.visits.length ?? 0}/{detail?.stops.length ?? '—'} điểm dừng</span>
        {latestCheckin && <span className="trip-checkin-latest">Lần gần nhất: trạm {latestCheckin.stopSequence} · {latestCheckin.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS'} · {displayTripTime(latestCheckin.simulatedArrivalAt ?? latestCheckin.actualArrivalAt)}</span>}
      </>}
    </div>}
    {run && <div className="simulation-times">
      <div>{frame?.finished ? 'Đã đi hết tuyến' : frame?.dwelling ? 'Đang dừng tại trạm theo lịch tuyến' : 'Xe di chuyển theo tuyến của chuyến'}</div>
      <div>Đồng hồ giả lập: <time data-testid="simulation-clock">{displayTripTime(run.simulatedAt)}</time></div>
      <div>Đã chạy: <span data-testid="simulation-elapsed">{run.elapsedSeconds.toFixed(1)}</span> / {run.durationSeconds} giây giả lập</div>
      {run.status === 'PAUSED' && <div>Đồng hồ đang dừng; tiếp tục phát để cập nhật.</div>}
      {run.status === 'FAILED' && run.errorMessage && <div className="simulation-error" role="alert">
        <span>{run.errorMessage}</span>
        <button type="button" className="simulation-retry-action" disabled={!canControl} onClick={() => setConfirm('reset')}>
          Chạy lại chuyến
        </button>
      </div>}
    </div>}
    <div className="trip-progress"><span>Tiến độ tuyến <b>{frame ? `${Math.round(frame.progressPercent)}%` : '—'}</b></span><div className="progress-track" role="progressbar" aria-label="Tiến độ tuyến mô phỏng" aria-valuenow={frame?.progressPercent ?? 0} aria-valuemin={0} aria-valuemax={100}><span style={{ width: `${frame?.progressPercent ?? 0}%` }} /></div></div>
    {confirm && <FleetConfirmDialog title={confirm === 'stop' ? 'Dừng mô phỏng và hủy chuyến?' : 'Chạy lại bằng chuyến mới?'}
      message={confirm === 'stop' ? 'Xe dừng mô phỏng và chuyến hiện tại được hủy. Lịch sử vị trí vẫn được lưu.' : 'Chuyến hiện tại sẽ kết thúc nếu còn chạy. Tạo chuyến mới cùng xe/tuyến ở trạng thái tạm dừng; giữ toàn bộ lịch sử cũ.'}
      confirmLabel={confirm === 'stop' ? 'Xác nhận dừng chuyến' : 'Tạo chuyến chạy lại'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => void simulator.command(confirm).then(ok => { if (ok) setConfirm(null); })} />}
  </section>;
}
