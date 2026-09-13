import { useEffect, useRef, useState } from 'react';
import { fetchOperations, subscribeOperations } from '../services/operations';
import type { OperationsSnapshot, StreamConnection } from '../types/operations';

export function useLiveOperations() {
  const [snapshot, setSnapshot] = useState<OperationsSnapshot | null>(null);
  const [connection, setConnection] = useState<StreamConnection>('connecting');
  const [now, setNow] = useState(Date.now);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const offset = useRef(0);
  useEffect(() => {
    let alive = true, lastMessage = Date.now(), newest = 0;
    const abort = new AbortController();
    const accept = (data: OperationsSnapshot) => {
      const time = Date.parse(data.serverTime);
      if (!alive || !Number.isFinite(time) || time < newest) return;
      newest = time; offset.current = time - Date.now();
      setSnapshot(data); setError(null);
    };
    const disconnect = () => { if (alive) setConnection('reconnecting'); };
    const stop = subscribeOperations(data => {
      if (!alive) return;
      lastMessage = Date.now(); accept(data); setConnection('live');
    }, disconnect);
    fetchOperations(abort.signal).then(accept).catch((err: unknown) => {
      if (alive && !abort.signal.aborted && newest === 0) setError(err instanceof Error ? err.message : 'Không thể tải vị trí xe.');
    });
    const timer = window.setInterval(() => {
      setNow(Date.now() + offset.current);
      if (Date.now() - lastMessage > 5000) disconnect();
    }, 1000);
    return () => { alive = false; abort.abort(); stop(); window.clearInterval(timer); };
  }, [attempt]);
  const reconnect = () => { setConnection('connecting'); setError(null); setAttempt(value => value + 1); };
  return { snapshot, connection, now, error, reconnect };
}
