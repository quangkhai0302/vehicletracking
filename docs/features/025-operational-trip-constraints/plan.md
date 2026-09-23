# Plan 025 — Ràng buộc vận hành chuyến

Trạng thái: **Approved by direct implementation request**.

1. **Schema/config** — V17 thêm cancellation reason và check tương thích; thêm lifecycle properties.
2. **Backend domain** — mở rộng TripRepository overlap queries; TripService enforce driver/start window/conflict/final check-in/cancel reason.
3. **API contract** — cập nhật cancel request/controller/DTO response và giữ tương thích đọc dữ liệu cũ.
4. **Frontend** — hiển thị driver là điều kiện bắt buộc trước khi start; readiness/error feedback; cancel reason dialog; hiển thị cancellation reason.
5. **Tests/docs** — bổ sung service tests, frontend lint/tsc, diff/evidence/walkthrough/review.
