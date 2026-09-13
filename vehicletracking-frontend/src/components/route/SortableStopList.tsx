import { useEffect, useId, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, GripVertical, MapPin, Trash2 } from 'lucide-react';
import type { RouteDraftStop } from '../../types/route';
import type { Station } from '../../types/station';

interface SortableStopListProps {
  stops: RouteDraftStop[]; stations: Station[]; disabled: boolean;
  onChange: (stops: RouteDraftStop[]) => void;
  selectedId: string | null; onFocusStop: (id: string) => void;
}

export function SortableStopList({ stops, stations, disabled, onChange, selectedId, onFocusStop }: SortableStopListProps) {
  const instructionsId = useId();
  const listRef = useRef<HTMLOListElement>(null);
  const [grabbedId, setGrabbedId] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const originalRef = useRef<RouteDraftStop[]>([]);
  const keyboardRef = useRef(false);

  useEffect(() => {
    if (selectedId) listRef.current?.querySelector<HTMLElement>(`[data-stop-id="${selectedId}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [selectedId]);

  const move = (from: number, to: number) => {
    if (disabled || from === to || to < 0 || to >= stops.length) return;
    const next = [...stops];
    next.splice(to, 0, next.splice(from, 1)[0]);
    onChange(next);
    setAnnouncement(`Đã chuyển điểm ${from + 1} đến vị trí ${to + 1} trên ${stops.length}.`);
  };
  const endDrag = () => { setGrabbedId(null); setDropTarget(null); keyboardRef.current = false; };

  return <>
    <p id={instructionsId} className="sort-instructions">Kéo tay nắm để đổi thứ tự. Bàn phím: Space để nhấc, ↑ ↓ để di chuyển, Enter để thả, Esc để hủy.</p>
    <ol className="sortable-stop-list" ref={listRef}>
      {stops.map((stop, index) => {
        const role = index === 0 ? 'start' : index === stops.length - 1 ? 'end' : 'stop';
        return <li key={stop.id} data-stop-id={stop.id} data-station-id={stop.stationId}
          className={`sortable-stop ${selectedId === stop.id ? 'selected' : ''} ${grabbedId === stop.id ? 'grabbed' : ''} ${dropTarget === stop.id ? 'drop-target' : ''}`}
          onDragOver={event => { if (!disabled && grabbedId) { event.preventDefault(); event.dataTransfer.dropEffect = 'move'; setDropTarget(stop.id); } }}
          onDrop={event => { event.preventDefault(); if (grabbedId) move(stops.findIndex(item => item.id === grabbedId), index); endDrag(); }}>
          <div className="sortable-stop-top">
            <button type="button" className="drag-handle" draggable={!disabled} disabled={disabled}
              aria-label={`Sắp xếp điểm ${index + 1}`} aria-describedby={instructionsId} aria-pressed={grabbedId === stop.id}
              onDragStart={event => { originalRef.current = stops; keyboardRef.current = false; setGrabbedId(stop.id); event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', stop.id); }}
              onDragEnd={endDrag}
              onKeyDown={event => {
                if (event.key === ' ' || event.key === 'Enter') {
                  event.preventDefault();
                  if (grabbedId === stop.id) { endDrag(); setAnnouncement(`Đã thả điểm tại vị trí ${index + 1}.`); }
                  else { originalRef.current = stops; keyboardRef.current = true; setGrabbedId(stop.id); setAnnouncement(`Đã nhấc điểm ${index + 1}. Dùng mũi tên để di chuyển.`); }
                } else if (keyboardRef.current && grabbedId === stop.id && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
                  event.preventDefault(); move(index, index + (event.key === 'ArrowUp' ? -1 : 1));
                } else if (event.key === 'Escape' && grabbedId) {
                  event.preventDefault(); if (keyboardRef.current) onChange(originalRef.current); endDrag(); setAnnouncement('Đã hủy thay đổi thứ tự.');
                }
              }}><GripVertical size={17} /></button>
            <span className={`stop-order ${role}`}>{index + 1}</span>
            <span className={`stop-badge ${role}`}>{role === 'start' ? 'Điểm đầu' : role === 'end' ? 'Điểm cuối' : 'Trạm dừng'}</span>
            <button type="button" className="stop-locate" aria-label={`Xem điểm ${index + 1} trên bản đồ`} onClick={() => onFocusStop(stop.id)}><MapPin size={14} /></button>
          </div>
          <select className="form-select" value={stop.stationId} disabled={disabled} aria-label={`Chọn trạm cho điểm dừng ${index + 1}`}
            onChange={event => onChange(stops.map(item => item.id === stop.id ? { ...item, stationId: Number(event.target.value) } : item))}>
            {!stations.some(station => station.id === stop.stationId) && <option value={stop.stationId}>Trạm không còn hoạt động</option>}
            {stations.map(station => <option key={station.id} value={station.id}>{station.name}</option>)}
          </select>
          <div className="sortable-stop-bottom">
            <label>Dừng {role === 'stop' ? <><input type="number" min={0} max={3600} step={1} value={stop.dwellDurationSeconds} disabled={disabled} aria-label={`Thời gian dừng cho điểm ${index + 1} (giây)`}
              onChange={event => onChange(stops.map(item => item.id === stop.id ? { ...item, dwellDurationSeconds: Number(event.target.value) } : item))} />giây</> : <span>0 giây</span>}</label>
            <div className="stop-actions">
              <button type="button" className="btn-icon-small" disabled={disabled || index === 0} aria-label={`Di chuyển điểm ${index + 1} lên`} onClick={() => move(index, index - 1)}><ArrowUp size={14} /></button>
              <button type="button" className="btn-icon-small" disabled={disabled || index === stops.length - 1} aria-label={`Di chuyển điểm ${index + 1} xuống`} onClick={() => move(index, index + 1)}><ArrowDown size={14} /></button>
              <button type="button" className="btn-icon-small danger" disabled={disabled || stops.length <= 2} aria-label={`Xóa điểm dừng ${index + 1}`} onClick={() => onChange(stops.filter(item => item.id !== stop.id))}><Trash2 size={14} /></button>
            </div>
          </div>
        </li>;
      })}
    </ol>
    <div className="sr-only" role="status">{announcement}</div>
  </>;
}
