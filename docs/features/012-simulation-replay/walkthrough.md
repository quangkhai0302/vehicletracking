# Walkthrough

Sau khi một mô phỏng đã dừng, hoàn thành hoặc gặp lỗi, chọn **Chạy lại** rồi xác nhận **Đặt lại chuyến**. Backend không tạo trip hay route mới. Trip giữ nguyên ID, route và xe; `attemptNumber` tăng một, trạng thái về `SCHEDULED`, phiên mô phỏng về `PAUSED`, tiến độ 0 và tốc độ phát 1×. Người dùng bấm **Bắt đầu** để chạy lần mới.

Telemetry và check-in đã ghi không bị xóa. Mỗi sample/visit mang `attemptNumber`; API realtime, ETA và reroute chỉ đọc attempt hiện tại. Có thể đọc lịch sử bằng:

- `GET /api/v1/trips/{tripId}/simulation/attempts`
- `GET /api/v1/trips/{tripId}/check-ins?attemptNumber=1`
- `GET /api/v1/telemetry/history?tripId={tripId}&attemptNumber=1`

Frontend nhận `attemptNumber` từ SSE/response reset, bỏ vị trí, ETA, check-in và detail cũ, rồi tải lại cùng `tripId`. Tuyến không được tính lại và cũng không tạo thêm dòng route.

Khi khởi động backend lần đầu sau thay đổi, Flyway áp dụng `V9__simulation_replay_attempts.sql`. Nên sao lưu database phát triển theo quy trình vận hành hiện có trước khi nâng schema.
