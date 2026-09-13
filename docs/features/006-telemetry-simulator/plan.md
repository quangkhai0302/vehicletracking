# Kế hoạch 006 — đã được yêu cầu triển khai

1. Migration V5, telemetry entity/repository/service/DTO/controller, latest position và dedupe. Snapshot/SSE resync dữ liệu đã commit.
2. Decoder và timeline motion; simulation entity/service/scheduler và play/pause/speed/stop/reset, bảo toàn snapshot 005.
3. Tests JPA/HTTP/clock/SSE với PostgreSQL tạm, xử lý lỗi được tìm thấy.
4. Realtime hook/service/types, marker/follow và simulator controls, đồng bộ trip list/status nhưng giữ draft Map-First.
5. Lint/tsc/build và browser hai tab, kiểm tra responsive/regression, cập nhật verification/progress/handoff bằng kết quả thực tế.

Trạng thái 2026-09-14: bước 1/2/4 đã implement; bước 3 đạt 24 test chọn lọc, còn HTTP/SSE/full suite; bước 5 frontend và browser fixture đạt, browser nối Spring/PostgreSQL chờ hạn mức quyền thực thi. Xem [verification.md](verification.md); chưa chuyển sang 007.
