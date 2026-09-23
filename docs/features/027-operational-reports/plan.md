# Plan — Báo cáo và thống kê vận hành

Feature được triển khai trực tiếp theo yêu cầu người dùng.

1. **Backend contract và config** — thêm `ReportingProperties`, response DTO, repository query theo kỳ/xe/tài xế, service tính KPI, controller `GET /api/v1/reports/operations`; không cần migration. Thêm unit/controller test.
2. **Frontend API và route** — thêm type/service, thay route roadmap bằng `ReportsPage`, bỏ cờ roadmap của menu Báo cáo.
3. **Frontend UX** — thêm bộ lọc ngày/xe/tài xế, KPI cards, định nghĩa metric, loading/error/empty và responsive CSS.
4. **Verify và evidence** — chạy lint/tsc, thử build/test; ghi hạn chế JDK/Node nếu môi trường không đáp ứng; review độc lập.

Điều kiện hoàn thành: API/type/UI nhất quán, metric semantics được ghi rõ, không có secret hoặc migration ngoài phạm vi.
