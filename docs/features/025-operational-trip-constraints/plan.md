# Plan 025 — Ràng buộc vận hành chuyến

Trạng thái: **Approved by direct implementation request**. Giới hạn giờ trong kế hoạch ban đầu đã được người dùng hủy ngày 2026-09-24; xem `spec.md` và `evidence.md` để biết hành vi hiện tại.

1. **Schema/config** — V17 thêm cancellation reason và check tương thích; thêm lifecycle properties.
2. **Backend domain** — TripRepository/TripService kiểm tra interval cho xe; cho phép tài xế được phân công nhiều chuyến và chỉ chặn khi bắt đầu nếu đã có chuyến khác `IN_PROGRESS`; enforce final check-in/cancel reason.
3. **API contract** — cập nhật cancel request/controller/DTO response và giữ tương thích đọc dữ liệu cũ.
4. **Frontend** — hiển thị driver là điều kiện bắt buộc trước khi start; readiness/error feedback; cancel reason dialog; hiển thị cancellation reason.
5. **Tests/docs** — bổ sung service tests, frontend lint/tsc, diff/evidence/walkthrough/review.
