# Walkthrough

1. Khởi động backend với `HERE_TRAFFIC_ENABLED=true`, `HERE_ROUTING_ENABLED=true`, API key ở backend và PostgreSQL đã migrate V7.
2. Tạo/chạy một trip có telemetry; mở `Cảnh báo` trên bản đồ.
3. Khi hai lần fetch HERE liên tiếp báo `BLOCKED` hoặc vượt ngưỡng trễ, evaluator tạo revision và notification.
4. UI hiển thị tiêu đề, trip, mức độ và lý do; bấm dấu kiểm để gọi `POST /api/v1/notifications/{id}/read`.
5. Kiểm tra `GET /api/v1/trips/{tripId}/revisions` để xem geometry/schedule revision, route gốc vẫn giữ nguyên.
# Sửa lỗi mô phỏng/ETA ngày 2026-09-14

Lỗi `null identifier (TripTrafficAlertStateEntity)` đã được tái hiện và sửa tại constructor entity dùng `@MapsId`; tham khảo mục bugfix trong `evidence.md`. Lần sửa trước chỉ cô lập transaction nên xe có thể chạy nhưng checkpoint cảnh báo vẫn không lưu được. Kiểm thử mới bắt buộc kiểm tra cả checkpoint và hoàn thành tuyến.

Sau khi dừng backend đang chạy bằng Ctrl+C, chạy lại `bash ./mvnw spring-boot:run` trong `vehicletracking-backend` với Java 26 rồi tải lại frontend.

1. Vào **Mô phỏng**, chọn đúng chuyến trong bảng **Mô phỏng xe** bên phải (ID chuyến được hiển thị cạnh biển số).
2. Chuyến sẵn sàng: bấm **Bắt đầu**. Chuyến đang chạy: chọn 1×/5×/10× để đổi tốc độ phát. Trạm được ghi nhận tự động.
3. Muốn nghỉ rồi chạy tiếp: **Tạm dừng → Tiếp tục**. **Dừng & hủy chuyến** kết thúc chuyến, không phải pause.
4. Phiên FAILED/STOPPED/COMPLETED: **Chạy lại → Tạo chuyến chạy lại → Bắt đầu**. Hệ thống chọn chuyến thay thế; lịch sử cũ vẫn còn. Nếu thao tác trên chuyến cũ đã có replacement thì API trả lại replacement đó; chọn Chạy lại trên replacement khi cần một lượt tiếp theo.
5. Các sự kiện kẹt xe/tai nạn/công trường giả lập chưa được triển khai. Traffic và ETA đang dùng dữ liệu nhà cung cấp hoặc tuyến lưu theo trạng thái hiển thị.
