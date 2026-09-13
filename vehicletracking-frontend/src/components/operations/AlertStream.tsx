import { BellOff, CircleCheck, Route } from 'lucide-react';

export function AlertStream() {
  return <section className="alert-stream-content" aria-label="Luồng cảnh báo">
    <div className="source-label"><span className="status-dot" /> Chưa kết nối nguồn sự kiện</div>
    <div className="alerts-empty"><BellOff size={24} /><strong>Chưa có luồng cảnh báo</strong><p>Sự kiện check-in và thay đổi lịch trình sẽ xuất hiện tại đây khi dịch vụ được kết nối.</p></div>
    <div className="alert-legend"><span><CircleCheck size={13} /> Check-in</span><span><Route size={13} /> Thay đổi lịch trình</span></div>
  </section>;
}
