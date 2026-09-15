# Requirement — Implemented

## Mục tiêu

Mô phỏng xe phải phản ánh giao thông tại vị trí xe: xe giảm tốc khi vào đoạn kẹt, tăng tốc khi sang đoạn thoáng và ETA cộng thời gian theo các đoạn giao thông còn lại.

## Phạm vi

- Dùng HERE Flow đang có để chọn tốc độ gần vị trí xe trong mỗi section của tuyến.
- Dùng nhiều flow khớp trên cùng section để ETA thay đổi theo từng phần hình học.
- Giữ fallback tốc độ tuyến khi HERE, vị trí xe hoặc geometry không dùng được.
- Giữ nguyên API và schema hiện có.

## Acceptance criteria

1. Tốc độ mô phỏng được xác định từ traffic tại section hiện tại, không còn từ trung bình toàn bộ quãng đường còn lại.
2. ETA của một section có các đoạn nhanh/chậm thay đổi theo flow gần từng phần geometry.
3. Flow tốc độ 0 không trở về thời lượng tuyến gốc; xe chỉ di chuyển chậm tối thiểu.
4. Đường đóng hoặc incident closure vẫn dừng mô phỏng.
5. Các test policy và matcher đạt.
