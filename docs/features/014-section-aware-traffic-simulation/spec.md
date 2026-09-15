# Spec — Implemented

- Khi tick simulator, backend xác định section của vị trí telemetry hiện tại và chọn flow gần nhất trong các flow đã khớp section đó.
- Rate mô phỏng là `baseline duration / traffic duration` của section hiện tại, giới hạn 5% đến 150%.
- Nếu flow đang mở có tốc độ 0, ETA và simulator dùng 1 km/h để biểu diễn trạng thái bò chậm; closure vẫn trả rate 0.
- Khi tính ETA, section được chia theo các đoạn thẳng của encoded polyline. Mỗi đoạn dùng flow gần vị trí giữa đoạn; phần không có flow dùng tốc độ baseline.
- Không thay đổi endpoint, DTO hoặc database.
