# Requirement — Báo cáo và thống kê vận hành

## Bối cảnh

Dashboard hiện có các chỉ số realtime, nhưng điều phối viên chưa thể xem lại hiệu suất theo khoảng thời gian, phương tiện hoặc tài xế. Feature này bổ sung một báo cáo lịch sử có định nghĩa KPI rõ ràng và có thể đối soát từ dữ liệu chuyến, telemetry và cảnh báo.

## Phạm vi

### In scope

- Lọc theo ngày bắt đầu/kết thúc, xe và tài xế.
- Thống kê số chuyến, số chuyến hoàn thành, tổng quãng đường, tổng thời gian chạy.
- Tính tỷ lệ đúng giờ, số chuyến trễ, số lần lệch tuyến và số lần quá tốc độ.
- API aggregate nội bộ và trang Báo cáo trong business shell.
- Trạng thái loading, lỗi, empty và hiển thị định nghĩa dữ liệu.

### Out of scope

- Xuất Excel/PDF.
- Phân quyền người dùng và báo cáo riêng cho từng tài khoản.
- So sánh nhiều kỳ hoặc biểu đồ chuỗi thời gian.
- Tự suy ra giới hạn tốc độ từ biển báo/nhà cung cấp bản đồ.

## Actor và luồng chính

Admin mở Báo cáo → chọn khoảng ngày và bộ lọc xe/tài xế → hệ thống trả aggregate → admin đối soát KPI và ngưỡng quá tốc độ. Nếu không có dữ liệu, trang hiển thị empty state; nếu API lỗi, có nút thử lại.

## Quy tắc nghiệp vụ

- Phạm vi chuyến dựa trên `scheduledDepartureAt`, ngày `to` bao gồm toàn bộ ngày đó theo UTC.
- Quãng đường là tổng `RouteEntity.totalDistanceMeters` của các chuyến phù hợp.
- Thời gian chạy là `endedAt - startedAt`; chuyến đang chạy tính đến thời điểm tạo báo cáo.
- Đúng giờ là chuyến hoàn thành có `endedAt` không sau thời điểm kết thúc kế hoạch.
- Trễ gồm chuyến hoàn thành sau kế hoạch và chuyến đang chạy đã quá thời điểm kết thúc kế hoạch.
- Lệch tuyến là số notification `OFF_ROUTE_DETECTED` trong kỳ.
- Quá tốc độ chỉ dùng telemetry GPS; các mẫu liên tiếp trên ngưỡng cấu hình được gộp thành một event.

## Acceptance criteria

1. Người dùng có thể lọc báo cáo theo khoảng ngày, xe và tài xế.
2. API trả đủ số chuyến, quãng đường, thời gian chạy, tỷ lệ đúng giờ, trễ, lệch tuyến và quá tốc độ.
3. Tỷ lệ đúng giờ có mẫu số là chuyến hoàn thành; không có chuyến hoàn thành trả 0%.
4. Một chuỗi GPS liên tiếp trên ngưỡng chỉ tính một lần quá tốc độ; ngưỡng được cấu hình ở backend và trả về response.
5. Lệch tuyến chỉ đếm notification loại `OFF_ROUTE_DETECTED` trong khoảng lọc.
6. UI có loading/error/empty và không gắn nhãn Roadmap cho Báo cáo.
7. Lọc sai ngày hoặc ID không dương trả lỗi 400 có thông báo rõ ràng.

## Phụ thuộc và giả định

- Dữ liệu thời gian lưu dưới dạng `Instant`; API dùng ngày UTC để tránh phụ thuộc timezone trình duyệt.
- Tài xế dùng quan hệ đang gán trên chuyến khi lọc lịch sử.
- Ngưỡng mặc định quá tốc độ là 80 km/h, có thể thay bằng `REPORT_DEFAULT_SPEED_LIMIT_KMH`; đây là ngưỡng vận hành cấu hình, không phải giới hạn biển báo tự động.
- Khoảng truy vấn tối đa mặc định 366 ngày (`REPORT_MAX_RANGE_DAYS`) để giới hạn dữ liệu materialize trong MVP.
