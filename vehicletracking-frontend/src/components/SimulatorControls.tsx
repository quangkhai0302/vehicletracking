import { Pause, Play, RotateCcw, Zap } from 'lucide-react';
import type { SimulatorMultiplier } from '../types/vehicle';

interface SimulatorControlsProps {
  isRunning: boolean;
  multiplier: SimulatorMultiplier;
  onTogglePlay: () => void;
  onReset: () => void;
  onChangeMultiplier: (multiplier: SimulatorMultiplier) => void;
}

const MULTIPLIERS: SimulatorMultiplier[] = [1, 2, 5, 10];

export function SimulatorControls({
  isRunning,
  multiplier,
  onTogglePlay,
  onReset,
  onChangeMultiplier,
}: SimulatorControlsProps) {
  return (
    <div className="simulator-controls-bar" role="region" aria-label="Bộ điều khiển mô phỏng telemetry">
      <div className="simulator-header">
        <div className="simulator-badge">
          <span className={`simulator-indicator ${isRunning ? 'running' : 'paused'}`} />
          <span className="simulator-tag">MÔ PHỎNG TELEMETRY</span>
        </div>
        <button
          type="button"
          className="simulator-reset-btn"
          onClick={onReset}
          title="Khởi động lại mô phỏng"
          aria-label="Khởi động lại mô phỏng"
        >
          <RotateCcw size={13} />
        </button>
      </div>

      <div className="simulator-actions">
        <button
          type="button"
          className={`simulator-play-btn ${isRunning ? 'active' : ''}`}
          onClick={onTogglePlay}
          aria-label={isRunning ? 'Tạm dừng mô phỏng' : 'Bắt đầu mô phỏng'}
        >
          {isRunning ? (
            <>
              <Pause size={14} /> Tạm dừng
            </>
          ) : (
            <>
              <Play size={14} /> Tiếp tục
            </>
          )}
        </button>

        <div className="multiplier-group" role="group" aria-label="Tốc độ mô phỏng">
          <Zap size={13} className="multiplier-icon" />
          {MULTIPLIERS.map((m) => (
            <button
              key={m}
              type="button"
              className={`multiplier-btn ${multiplier === m ? 'active' : ''}`}
              onClick={() => onChangeMultiplier(m)}
              aria-pressed={multiplier === m}
              title={`Tốc độ mô phỏng ${m}x`}
            >
              {m}x
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
