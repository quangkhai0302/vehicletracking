# 007 — Walkthrough

1. Khởi động PostgreSQL và backend để Flyway áp dụng migration V6.
2. Tạo hai trạm, tạo tuyến, tạo chuyến và chọn chuyến trong Fleet.
3. Bấm **Khởi hành** rồi gửi telemetry GPS qua `/api/v1/telemetry`; mẫu ở trong vùng tạo POINT, mẫu ngoài→trong hợp lệ tạo SEGMENT.
4. Hoặc mở **Mô phỏng**, chọn chuyến và phát. Khi route trace đi qua các stop, timeline cập nhật “Đã ghi nhận · GIẢ LẬP”; giờ giả lập và thời điểm suy ra được giữ riêng.
5. GET `/api/v1/trips/{tripId}/check-ins` hoặc theo dõi `checkIns` trong `/api/v1/telemetry/stream` để kiểm tra revision/visits. Retry cùng event không tạo visit thứ hai.

Check-in chỉ chứng minh xe đi qua vùng bán kính trạm; không xác nhận xe đã dừng đón/trả khách. Chưa ghi nhận stop không bị đổi thành SKIPPED và lifecycle chuyến không bị detector tự sửa.
