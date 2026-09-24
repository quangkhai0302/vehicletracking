# Walkthrough — Giờ chạy cố định và chuyến tức thời

## Điều phối chuyến thủ công

1. Mở **Kế hoạch vận hành → Chuyến đi**.
2. Chọn **Điều phối chuyến ngay**.
3. Chọn xe, tuyến và tài xế nếu đã xác định. Form chỉ preview thời lượng, số trạm và quãng đường; không yêu cầu ngày/giờ.
4. Tạo chuyến. Chuyến ở trạng thái chờ khởi hành và mang nhãn **Điều phối tức thời**.
5. Có thể gán cùng tài xế cho nhiều chuyến chờ. Khi bấm khởi hành, hệ thống chỉ từ chối nếu tài xế hoặc xe đang thực hiện một chuyến khác.

Trong chi tiết chuyến tức thời, giao diện hiển thị thời điểm điều phối, khởi hành thực tế và kết thúc thực tế. Mốc kỹ thuật `scheduledDepartureAt` vẫn tồn tại trong API để tương thích, nhưng không được trình bày là giờ kế hoạch.

## Chuyến chạy cố định

1. Mở **Lịch chạy tự động** và cấu hình tuyến, xe, tài xế, ngày/thứ cùng giờ chạy.
2. Scheduler sinh occurrence với nhãn **Theo lịch cố định**.
3. Chi tiết occurrence hiển thị **Xuất phát kế hoạch** và **Hoàn thành theo lịch**, bên cạnh mốc thực tế.
4. Muốn đổi giờ chạy, chỉnh cấu hình lịch tự động; không sửa trực tiếp occurrence đã sinh.

Replay/mô phỏng không ghi đè giờ kế hoạch của occurrence cố định. Dashboard và báo cáo chỉ dùng nhóm này để tính chuyến trễ và tỷ lệ đúng giờ.

## Phân biệt dữ liệu

- `dispatchMode=ON_DEMAND`, `scheduleId=null`: điều phối tức thời.
- `dispatchMode=FIXED_SCHEDULE`, có `scheduleId` và `scheduleName`: sinh từ lịch chạy tự động.

Tuyến đường chỉ chứa lộ trình, trạm, khoảng cách và thời lượng ước tính. Tuyến không tự mang ngày/giờ khởi hành.
