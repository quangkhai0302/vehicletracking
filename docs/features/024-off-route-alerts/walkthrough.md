# Walkthrough 024 — Cảnh báo xe lệch tuyến

## Backend

1. Cấu hình `offroute.*` trong `application.yaml` cho phép bật/tắt detector, đặt khoảng cách, thời gian duy trì và số mẫu liên tiếp.
2. Khi GPS được ghi thành công, `TelemetryService` chỉ gọi detector sau commit. Detector đọc vị trí mới nhất, bỏ qua simulator và trip không chạy.
3. Detector lấy geometry tuyến hiện hành, bắt đầu từ section sau stop chưa check-in, rồi đo khoảng cách GPS tới polyline.
4. State bền vững theo trip/attempt gom các mẫu liên tiếp. Chỉ khi đủ cả số mẫu và thời gian duy trì, hệ thống mới tạo một `OFF_ROUTE_DETECTED` notification.
5. Khi xe quay lại corridor, state được clear. Lần lệch kế tiếp tạo episode mới; unique dedupe key ngăn notification trùng.

## Frontend

1. Mở **Cảnh báo** trong thanh điều hướng để vào `/alerts`.
2. Trang hiển thị tổng số cảnh báo, chưa đọc và lệch tuyến; lọc theo loại/mức độ.
3. Mỗi cảnh báo hiển thị xe, chuyến, thời điểm, khoảng cách đo được, ngưỡng và thời lượng; có thao tác mở giám sát, đánh dấu đã xử lý, xóa và đọc tất cả.
4. Dữ liệu tự làm mới 15 giây; lỗi có nút thử lại, danh sách rỗng có empty state, layout co xuống màn hình nhỏ.
5. **Mở giám sát** truyền `tripId` vào `/operations`; bản đồ chọn chuyến tương ứng khi snapshot live đã tải.
