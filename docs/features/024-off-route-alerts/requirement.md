# Requirement 024 — Cảnh báo xe lệch tuyến

Trạng thái: **Implemented** — kiểm tra non-Docker đạt ngày 2026-09-21; PostgreSQL integration cần Docker.

## Bối cảnh và mục tiêu

Hệ thống đã nhận GPS và hiển thị vị trí xe nhưng chưa xác định được xe đang cách xa tuyến được giao. Điều phối viên cần một cảnh báo có thể xử lý, tránh cảnh báo giả do sai số GPS hoặc một điểm đo đơn lẻ.

## Phạm vi

### Actor và luồng chính

- **Telemetry pipeline** gửi bản tin GPS cho trip đang chạy; hệ thống đo khoảng cách tới tuyến và cập nhật state detector.
- **Điều phối viên/admin** xem danh sách cảnh báo, lọc theo loại/mức độ, mở chuyến trên màn hình giám sát, đánh dấu đã đọc hoặc xóa notification.

Luồng chính: GPS commit thành công → detector kiểm tra geometry còn lại → đủ điều kiện debounce → lưu notification → snapshot/SSE và trang trung tâm cảnh báo hiển thị.

### Functional requirements

- FR-01: Chỉ đánh giá mẫu GPS mới nhất thuộc đúng trip/attempt đang `IN_PROGRESS`.
- FR-02: Áp dụng ngưỡng khoảng cách, accuracy GPS, số mẫu liên tiếp và thời gian duy trì từ cấu hình backend.
- FR-03: Lưu state bền vững, chống lặp trong một episode và cho phép episode mới sau khi xe quay lại corridor.
- FR-04: Notification phải giữ thông tin trip/xe, khoảng cách, ngưỡng và thời lượng; API hiện hữu phải đọc/đánh dấu/xóa được.
- FR-05: UI trung tâm cảnh báo phải có trạng thái tải, lỗi, rỗng, lọc và xác nhận hành động xóa.

### In scope

- Theo dõi vị trí GPS của chuyến đang `IN_PROGRESS` so với geometry tuyến hiện hành.
- Ngưỡng vận hành cấu hình được: khoảng cách tối đa, thời gian duy trì ngoài tuyến và số mẫu liên tiếp.
- Dùng sai số GPS để không cảnh báo khi sai số lớn hơn ngưỡng khoảng cách.
- Lưu state theo trip/attempt, chống lặp notification trong cùng một episode và tự re-arm khi xe quay lại corridor.
- Phát notification `OFF_ROUTE_DETECTED` qua API notifications, snapshot và SSE hiện có.
- Trang **Trung tâm cảnh báo** hiển thị, lọc, đánh dấu đã đọc và xóa cảnh báo; giữ các notification đổi tuyến hiện tại.

### Out of scope

- Tự động điều khiển xe hoặc tự đổi tuyến.
- Cấu hình ngưỡng riêng từng tuyến trên UI; MVP dùng cấu hình backend/env.
- Cảnh báo cho dữ liệu `SIMULATOR` mặc định, xe chưa có trip đang chạy, hoặc vị trí không có geometry hợp lệ.
- Phân quyền/RBAC, push notification, SMS/email và báo cáo lịch sử chuyên sâu.

## Quy tắc MVP

- Một mẫu được xem là ngoài tuyến khi khoảng cách tới section còn lại gần nhất lớn hơn `max(configuredDistanceMeters, accuracyMeters)`.
- Episode chỉ phát cảnh báo khi vượt ngưỡng trong ít nhất `consecutiveSamples` mẫu và kéo dài ít nhất `gracePeriodSeconds`.
- Khi khoảng cách trở lại trong ngưỡng, state được clear; episode kế tiếp được phép phát notification mới.
- Chỉ đánh giá `GPS`; simulator vốn sinh trên geometry tuyến nên không tạo cảnh báo vận hành.
- Nếu lỗi detector/geometry/notification, ingestion GPS đã commit vẫn giữ nguyên; lỗi chỉ được ghi log best-effort.

## Acceptance criteria

- AC-01: Một GPS ngoài corridor của trip đang chạy được đo theo geometry route/revision hiện hành.
- AC-02: Không phát cảnh báo cho điểm trong ngưỡng, sai số GPS bao phủ ngưỡng, simulator hoặc trip không chạy.
- AC-03: Cảnh báo chỉ phát sau đủ số mẫu liên tiếp và thời gian duy trì; một episode chỉ có tối đa một notification.
- AC-04: Khi xe quay lại corridor rồi lệch lại, episode mới phát được notification mới; replay/attempt mới reset state.
- AC-05: Notification chứa trip, xe, mức độ, khoảng cách đo được, ngưỡng và thời lượng lệch; xuất hiện trong API, snapshot và SSE.
- AC-06: Ingestion GPS không rollback vì detector lỗi; notification được lưu bền vững và có thể đọc/xóa như notification hiện tại.
- AC-07: `/alerts` là trang quản trị business có loading, empty, lỗi/retry, lọc loại/mức độ, read-all, read và delete.
- AC-08: Migration mới, config, backend/frontend type và test/lint/build có evidence; không sửa migration đã áp dụng.

## Giả định và phụ thuộc

- Route geometry đã được tính và nằm trong `RouteDetailResponse.sections`.
- `RoutePositionMatcher` được tái sử dụng để đo point-to-polyline; detector chọn các section từ next stop chưa check-in.
- Ngưỡng mặc định MVP: 150m, 30 giây, 3 mẫu; có thể thay bằng biến môi trường an toàn.

## Non-functional requirements

- Detector không được làm rollback transaction lưu telemetry; lỗi geometry/notification là best-effort.
- Không đưa credential hoặc API key vào frontend; các ngưỡng chỉ là cấu hình backend/env.
- State detector và notification phải chịu được callback lặp, replay attempt và restart ứng dụng.
