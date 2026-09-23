# Research 024 — Cảnh báo xe lệch tuyến

## Kết luận

Không cần research bên ngoài. Feature dùng geometry và telemetry nội bộ, Spring scheduling/transaction hiện hữu và notification API đã có. Quyết định thuật toán dựa trên source code repository và các giới hạn GPS thông thường được mô hình hóa thành sai số `accuracyMeters`.

## Quyết định kỹ thuật

- Đánh giá sau commit telemetry để detector không làm hỏng giao dịch ghi vị trí.
- Dùng khoảng cách điểm tới polyline section còn lại; không dùng khoảng cách tới station thẳng tuyến vì có thể bỏ qua hình dạng đường.
- Dùng state bền vững theo trip/attempt và consecutive samples để chống GPS jitter/cảnh báo lặp.
- Tái sử dụng `trip_notifications`, mở rộng enum/structured fields bằng migration V16 thay vì tạo hệ thống notification thứ hai.
- Bỏ qua simulator trong MVP vì `RouteMotion` sinh điểm trên geometry; test detector sẽ dùng GPS fixture.
