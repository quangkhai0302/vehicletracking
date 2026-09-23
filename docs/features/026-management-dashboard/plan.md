# Plan 026 — Dashboard quản lý vận hành

Trạng thái: **Approved by direct implementation request**.

1. **Aggregate backend** — thêm repository count/query cho xe, tài xế, trip, off-route state và unread notification.
2. **Dashboard API** — tạo `dashboard` feature package với DTO/service/controller và test service/controller.
3. **Frontend contract** — thêm `DashboardSummary` type và service gọi `/dashboard/summary`.
4. **Dashboard UI** — thay placeholder bằng KPI cards, chuyến trễ, cảnh báo actionable và polling/error/empty states.
5. **Verify/docs** — chạy lint/typecheck/diff check, ghi evidence/walkthrough/review; Maven/build theo giới hạn môi trường.
