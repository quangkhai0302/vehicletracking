import { useState } from 'react';
import { Car, Gauge, Pause, Play, RotateCcw, Square } from 'lucide-react';
import type { useSimulator } from '../../hooks/useSimulator';
import type { OperationsSnapshot, StreamConnection } from '../../types/operations';
import { SIMULATION_LABELS } from '../../types/operations';
import { displayTripTime } from '../../utils/tripTime';
import { FleetConfirmDialog } from '../fleet/FleetConfirmDialog';
import { useTripEta } from '../../hooks/useTripEta';
import type { TrafficSource } from '../../types/traffic';
import './simulator.css';

export function SimulatorPanel({ simulator, snapshot, connection, connectionError, onReconnect, onShowRoute }: {
  simulator: ReturnType<typeof useSimulator>; snapshot: OperationsSnapshot | null; connection: StreamConnection;
  connectionError: string | null; onReconnect: () => void; onShowRoute: () => void;
}) {
  const [confirm, setConfirm] = useState<'stop' | 'reset' | null>(null);
  const { trip, run, detail, busy, loading, error } = simulator;
  const eta = useTripEta(trip?.id ?? null);
  const running = run?.status === 'RUNNING';
  const active = trip?.status === 'SCHEDULED' || trip?.status === 'IN_PROGRESS';
  const canPlay = !!trip && active && (!run || run.status === 'PAUSED' || running);
  const frame = run?.frame;
  const options = snapshot?.trips ?? [];
  const canControl = !busy && !loading && connection === 'live';
  const playLabel = running ? 'Tạm dừng' : run?.status === 'PAUSED' && trip?.status === 'IN_PROGRESS' ? 'Tiếp tục' : 'Bắt đầu';
  const trafficNextStop = eta.data?.nextStopSequence === null ? null : eta.data?.stops.find(stop => stop.state === 'NEXT');
  const metadataEta = run?.traffic?.nextStopEtaSeconds;
  const countdown = trafficNextStop?.etaSeconds !== null && trafficNextStop?.etaSeconds !== undefined
    ? Math.ceil(trafficNextStop.etaSeconds)
    : metadataEta !== null && metadataEta !== undefined ? Math.ceil(metadataEta)
      : eta.data?.status === 'BLOCKED' || run?.traffic?.blocked ? null : frame ? Math.ceil(frame.nextStopEtaSeconds) : null;
  const nextStop = detail?.stops.find(stop => stop.sequenceNumber === (eta.data?.nextStopSequence ?? frame?.nextStopSequence));
  const sourceNames: Record<TrafficSource, string> = {
    HERE_LIVE: 'HERE LIVE', HERE_LAST_KNOWN: 'HERE · DỮ LIỆU GẦN NHẤT', ROUTE_SNAPSHOT: 'TUYẾN ĐÃ LƯU', UNAVAILABLE: 'TRAFFIC KHÔNG KHẢ DỤNG',
  };
  const sourceLabel = eta.data ? `ETA · ${sourceNames[eta.data.source]}` : run?.traffic ? `ETA · ${sourceNames[run.traffic.source]}` : eta.loading ? 'ETA · ĐANG TẢI TRAFFIC' : 'TUYẾN ĐÃ LƯU · Thời lượng cơ sở';
  const checkin = trip && Array.isArray(snapshot?.checkIns) ? snapshot.checkIns.find(item => item.tripId === trip.id) : null;
  const latestCheckin = checkin?.visits[checkin.visits.length - 1];
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
    {trip && <div className="simulation-summary"><strong>#{trip.id} · {trip.vehiclePlateNumber}</strong><span data-testid="simulation-status">{run ? SIMULATION_LABELS[run.status] : active ? 'Sẵn sàng' : 'Chuyến đã kết thúc'}</span></div>}
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
    {trip && <div className="trip-checkin-summary">
      {snapshot && !Array.isArray(snapshot.checkIns) ? 'Backend chưa hỗ trợ dữ liệu check-in.' : <>
        <span>Đã ghi nhận {checkin?.visits.length ?? 0}/{detail?.stops.length ?? '—'} điểm dừng</span>
        {latestCheckin && <span className="trip-checkin-latest">Lần gần nhất: trạm {latestCheckin.stopSequence} · {latestCheckin.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS'} · {displayTripTime(latestCheckin.simulatedArrivalAt ?? latestCheckin.actualArrivalAt)}</span>}
      </>}
    </div>}
    <div className="source-label">{sourceLabel}</div>
    <div className="telemetry-grid">
      <div><span><Gauge size={13} /> Vận tốc</span><strong data-testid="simulation-speed">{frame ? frame.speedKmh.toFixed(1) : '—'} <small>km/h</small></strong></div>
      <div><span>Đến trạm tiếp theo</span><strong data-testid="simulation-eta">{countdown === null ? '—' : countdown < 60 ? countdown : Math.ceil(countdown / 60)} <small>{countdown !== null && countdown < 60 ? 'giây' : 'phút'}</small></strong></div>
    </div>
    {run && <div className="simulation-times">
      <div>{frame?.finished ? 'Đã đi hết tuyến' : frame?.dwelling ? 'Đang dừng theo lịch tuyến' : nextStop ? `Tiếp theo: ${nextStop.stationName}` : 'Chưa có thông tin trạm'}</div>
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
    {trip && <button className="fleet-text-button" disabled={loading || !detail} onClick={onShowRoute}>Xem tuyến mô phỏng trên bản đồ</button>}
    <p className="availability-note">Tạo kẹt xe, tai nạn hoặc công trường giả lập chưa được hỗ trợ.</p>
    {eta.error && <p className="availability-note simulation-error" role="alert">{eta.error} <button onClick={eta.retry}>Thử lại ETA</button></p>}
    {eta.data?.warning && !eta.error && <p className="availability-note" role="status">{eta.data.warning.startsWith('VEHICLE_POSITION_UNAVAILABLE')
      ? 'Chưa xác định được vị trí xe trên tuyến; ETA đang dựa trên lịch trình đã lưu.'
      : eta.data.status === 'BLOCKED' ? 'Tuyến đang có đoạn đường bị chặn theo dữ liệu giao thông.'
        : 'Dữ liệu giao thông chưa đầy đủ; ETA có thể dựa trên tuyến đã lưu hoặc dữ liệu gần nhất.'}</p>}
    {eta.data?.trafficObservedAt && <p className="availability-note">Traffic cập nhật: {displayTripTime(eta.data.trafficObservedAt)} · ETA tự làm mới mỗi 10 giây.</p>}
    {!eta.data && <p id="simulator-unavailable" className="availability-note">Chưa có ETA traffic; đang hiển thị thời lượng tuyến đã lưu. Lượt đi qua trạm được ghi nhận tự động ở dòng trạng thái.</p>}
    {confirm && <FleetConfirmDialog title={confirm === 'stop' ? 'Dừng mô phỏng và hủy chuyến?' : 'Chạy lại bằng chuyến mới?'}
      message={confirm === 'stop' ? 'Xe dừng mô phỏng và chuyến hiện tại được hủy. Lịch sử vị trí vẫn được lưu.' : 'Chuyến hiện tại sẽ kết thúc nếu còn chạy. Tạo chuyến mới cùng xe/tuyến ở trạng thái tạm dừng; giữ toàn bộ lịch sử cũ.'}
      confirmLabel={confirm === 'stop' ? 'Xác nhận dừng chuyến' : 'Tạo chuyến chạy lại'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => void simulator.command(confirm).then(ok => { if (ok) setConfirm(null); })} />}
  </section>;
}
