# Walkthrough 026 — Dashboard quản lý vận hành

1. Mở `/dashboard`. Dashboard gọi aggregate endpoint và danh sách chuyến, sau đó tự làm mới mỗi 15 giây.
2. Hàng KPI hiển thị tình trạng đội xe, chuyến đang chạy/chờ/hoàn thành/hủy, chuyến đang trễ, xe lệch tuyến và cảnh báo chưa đọc.
3. Chuyến đang chạy quá giờ dự kiến được gắn nhãn **Đang trễ** trong danh sách gần đây.
4. Cảnh báo chưa đọc xuất hiện ở panel bên phải. Chọn một cảnh báo để mở đúng chuyến trên bản đồ; chọn **Mở trung tâm** để xử lý toàn bộ danh sách.
5. Nếu aggregate API lỗi, dashboard giữ dữ liệu trước đó (nếu có), hiển thị thông báo lỗi và cho phép thử lại.

“Chuyến trễ” trong MVP có nghĩa là chuyến `IN_PROGRESS` đã quá `plannedEndAt`; báo cáo đúng giờ theo khoảng thời gian vẫn thuộc feature báo cáo riêng.
