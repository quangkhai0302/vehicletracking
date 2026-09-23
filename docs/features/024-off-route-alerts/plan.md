# Plan 024 — Cảnh báo xe lệch tuyến

Trạng thái: **Approved by direct implementation request**.

1. **Migration/config/state** — V16 tạo state detector, mở rộng notification type/metrics và thêm `OffRouteProperties`; kiểm tra constraint.
2. **Detector backend** — thêm entity/repository/service, tái sử dụng route geometry/matcher, threshold/accuracy/consecutive/grace/episode và hook after-commit telemetry.
3. **Notification contract** — cập nhật enum/entity/DTO/repository-compatible API và tests cho dedupe/read fields.
4. **Frontend alert center** — cập nhật type/AlertStream, thay `/alerts` roadmap bằng page list/filter/read/delete/responsive.
5. **Tests/verification/docs** — unit detector, service conflict/error, frontend tsc/lint/build/diff; cập nhật evidence/walkthrough/review.
