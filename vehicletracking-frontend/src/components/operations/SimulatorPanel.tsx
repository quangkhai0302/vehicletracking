import { useState } from 'react';
import { Activity, Car, Construction, Gauge, Pause, Play, RotateCcw, Square, TriangleAlert } from 'lucide-react';
import type { useSimulator } from '../../hooks/useSimulator';
import type { OperationsSnapshot, StreamConnection } from '../../types/operations';
import { SIMULATION_LABELS } from '../../types/operations';
import { displayTripTime } from '../../utils/tripTime';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';
import './simulator.css';

export function SimulatorPanel({ simulator, snapshot, connection, connectionError, onReconnect, onShowRoute }: {
  simulator: ReturnType<typeof useSimulator>; snapshot: OperationsSnapshot | null; connection: StreamConnection;
  connectionError: string | null; onReconnect: () => void; onShowRoute: () => void;
}) {
  const [confirm, setConfirm] = useState<'stop' | 'reset' | null>(null);
  const { trip, run, detail, busy, loading, error } = simulator;
  const running = run?.status === 'RUNNING';
  const active = trip?.status === 'SCHEDULED' || trip?.status === 'IN_PROGRESS';
  const canPlay = !!trip && active && (!run || run.status === 'PAUSED' || running);
  const frame = run?.frame;
  const options = snapshot?.trips ?? [];
  const canControl = !busy && !loading && connection === 'live';
  const countdown = frame ? Math.ceil(frame.nextStopEtaSeconds) : null;
  const nextStop = detail?.stops.find(stop => stop.sequenceNumber === frame?.nextStopSequence);
  return <section className="simulator-content" aria-label="Điều khiển mô phỏng">
    <div className="simulation-connection" data-state={connection}><span>{connection === 'live' ? 'Đã kết nối realtime' : connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · đang thử lại'}</span>
      {connection !== 'live' && <button onClick={onReconnect}>Kết nối lại</button>}</div>
    {connectionError && <p className="simulation-error" role="alert">{connectionError}</p>}
    <label className="simulation-select">Chuyến mô phỏng
      <select aria-label="Chọn chuyến mô phỏng" disabled={busy || !snapshot} value={simulator.tripId ?? ''} onChange={e => simulator.select(e.target.value ? Number(e.target.value) : null)}>
        <option value="">Chọn xe và chuyến đi…</option>
        {simulator.tripId && !options.some(item => item.id === simulator.tripId) && <option value={simulator.tripId}>Chuyến #{simulator.tripId}</option>}
        {options.map(item => <option key={item.id} value={item.id}>#{item.id} · {item.vehiclePlateNumber} · {item.routeName}</option>)}
      </select>
    </label>
    {!trip && !loading && <div className="telemetry-placeholder"><Car size={26} /><div><strong>Chưa chọn chuyến mô phỏng</strong><span>Tạo xe và chuyến ở Đội xe, rồi chọn chuyến tại đây.</span></div></div>}
    {loading && <p className="availability-note" role="status">Đang tải tuyến mô phỏng…</p>}
    {error && !confirm && <div className="simulation-error" role="alert">{error} <button disabled={busy} onClick={simulator.retry}>Tải lại chuyến</button></div>}
    {trip && <div className="simulation-summary"><strong>{trip.vehiclePlateNumber}</strong><span data-testid="simulation-status">{run ? SIMULATION_LABELS[run.status] : active ? 'Sẵn sàng' : 'Chuyến đã kết thúc'}</span></div>}
    <div className="source-label">GIẢ LẬP · Thời lượng tuyến đã lưu</div>
    <div className="telemetry-grid">
      <div><span><Gauge size={13} /> Vận tốc</span><strong data-testid="simulation-speed">{frame ? frame.speedKmh.toFixed(1) : '—'} <small>km/h</small></strong></div>
      <div><span>Đến trạm tiếp theo</span><strong data-testid="simulation-eta">{countdown === null ? '—' : countdown < 60 ? countdown : Math.ceil(countdown / 60)} <small>{countdown !== null && countdown < 60 ? 'giây' : 'phút'}</small></strong></div>
    </div>
    {run && <div className="simulation-times">
      <div>{frame?.finished ? 'Đã đi hết tuyến' : frame?.dwelling ? 'Đang dừng theo lịch tuyến' : nextStop ? `Tiếp theo: ${nextStop.stationName}` : 'Chưa có thông tin trạm'}</div>
      <div>Đồng hồ giả lập: <time data-testid="simulation-clock">{displayTripTime(run.simulatedAt)}</time></div>
      <div>Đã chạy: <span data-testid="simulation-elapsed">{run.elapsedSeconds.toFixed(1)}</span> / {run.durationSeconds} giây giả lập</div>
      {run.status === 'PAUSED' && <div>Đồng hồ đang dừng; tiếp tục phát để cập nhật.</div>}
      {run.errorMessage && <div className="simulation-error" role="alert">{run.errorMessage}</div>}
    </div>}
    <div className="trip-progress"><span>Tiến độ tuyến <b>{frame ? `${Math.round(frame.progressPercent)}%` : '—'}</b></span><div className="progress-track" role="progressbar" aria-label="Tiến độ tuyến mô phỏng" aria-valuenow={frame?.progressPercent ?? 0} aria-valuemin={0} aria-valuemax={100}><span style={{ width: `${frame?.progressPercent ?? 0}%` }} /></div></div>
    <fieldset disabled={!canControl}>
      <legend>Tốc độ phát · không phải vận tốc xe</legend>
      <div className="playback-row">
        <button className="play-button" disabled={!canPlay} aria-label={running ? 'Tạm dừng mô phỏng' : 'Bắt đầu mô phỏng'} onClick={() => void simulator.command(running ? 'pause' : 'play')}>{running ? <Pause size={16} /> : <Play size={16} />}</button>
        {([1,5,10] as const).map(speed => <button key={speed} disabled={!run || !active || (run.status !== 'RUNNING' && run.status !== 'PAUSED')} aria-label={`Tốc độ ${speed}x`} aria-pressed={run?.multiplier === speed} onClick={() => void simulator.command('speed',speed)}>{speed}x</button>)}
      </div>
      <div className="simulation-actions">
        <button className="btn-secondary" disabled={!run || !active} onClick={() => setConfirm('stop')}><Square size={13} />Dừng chuyến</button>
        <button className="btn-secondary" disabled={!run} onClick={() => setConfirm('reset')}><RotateCcw size={13} />Chạy lại</button>
      </div>
    </fieldset>
    {trip && <button className="fleet-text-button" disabled={loading || !detail} onClick={onShowRoute}>Xem tuyến mô phỏng trên bản đồ</button>}
    <fieldset disabled aria-describedby="simulator-unavailable">
      <p className="inject-label">Tạo sự kiện thử nghiệm</p>
      <div className="incident-actions"><button><Activity size={16} />Kẹt xe</button><button><TriangleAlert size={16} />Tai nạn</button><button><Construction size={16} />Công trường</button></div>
    </fieldset>
    <p id="simulator-unavailable" className="availability-note">Chưa áp dụng giao thông thực tế. Thời gian tới trạm là thời gian giả lập còn lại, chưa phải ETA theo traffic hoặc check-in.</p>
    {confirm && <FleetConfirmDialog title={confirm === 'stop' ? 'Dừng mô phỏng và hủy chuyến?' : 'Chạy lại bằng chuyến mới?'}
      message={confirm === 'stop' ? 'Xe dừng mô phỏng và chuyến hiện tại được hủy. Lịch sử vị trí vẫn được lưu.' : 'Chuyến hiện tại sẽ kết thúc nếu còn chạy. Tạo chuyến mới cùng xe/tuyến ở trạng thái tạm dừng; giữ toàn bộ lịch sử cũ.'}
      confirmLabel={confirm === 'stop' ? 'Xác nhận dừng chuyến' : 'Tạo chuyến chạy lại'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => void simulator.command(confirm).then(ok => { if (ok) setConfirm(null); })} />}
  </section>;
}
