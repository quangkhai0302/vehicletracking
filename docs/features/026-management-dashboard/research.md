# Research 026 — Dashboard quản lý vận hành

Không cần research bên ngoài. Dashboard sử dụng dữ liệu nghiệp vụ nội bộ đã có: trip lifecycle, route planned end, off-route state và notification. Quyết định thêm endpoint aggregate riêng để không buộc frontend tải toàn bộ telemetry snapshot cho một màn hình KPI.

## Quyết định

- `serverTime` lấy từ backend `Clock` để tránh lệch múi giờ giữa trình duyệt và server.
- “Chuyến trễ” MVP chỉ là chuyến `IN_PROGRESS` đã quá `plannedEndAt`; không tự suy diễn từ các chuyến completed.
- “Xe lệch tuyến” lấy từ state detector active, có lọc trip còn `IN_PROGRESS`, thay vì đếm notification lịch sử.
- Cảnh báo chưa đọc dùng count toàn bộ; danh sách dashboard chỉ lấy 5 item mới nhất để giữ payload nhỏ.
