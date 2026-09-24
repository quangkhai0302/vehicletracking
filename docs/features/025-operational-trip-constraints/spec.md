# Spec 025 — Ràng buộc vận hành chuyến

Trạng thái: **Implemented**.

## Configuration

Từ 2026-09-24 không còn cấu hình `trip.lifecycle.*`: giờ dự kiến chỉ dùng để lập kế hoạch, không giới hạn thời điểm khởi hành thực tế hoặc bắt đầu mô phỏng.

## API contract

- `POST /api/v1/trips/{id}/start`: kiểm tra driver, active resources và running conflict; không chặn theo giờ dự kiến. Nếu tài xế đang có chuyến khác `IN_PROGRESS`, trả `409` với detail `Tài xế đang chạy một chuyến khác.`
- `POST /api/v1/trips/{id}/complete`: yêu cầu trip `IN_PROGRESS` và final stop visit của attempt hiện tại; thiếu visit trả `409`.
- `POST /api/v1/trips/{id}/cancel` nhận `{ "reason": "..." }`; reason trim, 3–500 ký tự; thiếu/sai trả `400`; trip đã cancel trả response cũ, không ghi đè reason.
- `POST /api/v1/trips` và `PUT /api/v1/trips/{id}` dùng conflict interval cho xe khi tạo/cập nhật giờ; chuyến nháp có thể chưa có tài xế, còn lịch tự động luôn tham chiếu tài xế active. Việc một tài xế đã được phân công vào chuyến khác không làm request thất bại.

## Data model

V17 thêm `trips.cancellation_reason VARCHAR(500)` nullable và check: chỉ `CANCELLED` mới có reason khác null; các status khác phải null. Dữ liệu cũ `CANCELLED` chưa có reason được backfill giá trị hệ thống hoặc cho phép null để migration không phá dữ liệu lịch sử.

Repository truy vấn các chuyến theo xe và status `SCHEDULED, IN_PROGRESS`, loại trừ trip hiện tại khi update. Khoảng cuối dự kiến được tính ở service bằng route estimated duration. Với tài xế, service chỉ truy vấn sự tồn tại của chuyến khác `IN_PROGRESS` khi bắt đầu chuyến; unique partial index `uq_trips_running_driver` là lớp bảo vệ đồng thời ở database.

## UI contract

- Trip editor cho phép lưu chuyến nháp chưa có tài xế nhưng phải cảnh báo rõ; không thể khởi hành cho tới khi chọn tài xế active.
- Trip detail hiển thị readiness: tài xế, conflict và final-stop check-in.
- Nút Start/Complete bị disable theo dữ liệu đã tải nhưng API vẫn quyết định cuối cùng.
- Dialog Cancel có textarea bắt buộc, hiển thị lỗi inline và giữ dialog mở khi API trả lỗi.
- Chi tiết CANCELLED hiển thị reason nếu có.

## Rules

- Conflict interval của xe là half-open `[departure, departure + estimatedTripDuration)`; cùng endpoint/time boundary không bị xem là overlap.
- Một tài xế có thể được gán nhiều chuyến `SCHEDULED`, kể cả có khoảng dự kiến giao nhau. Chỉ một chuyến của tài xế được phép `IN_PROGRESS` tại một thời điểm.
- `IN_PROGRESS` trip không thể đổi driver, route hoặc departure.
- Complete chỉ thành công khi stop có `sequenceNumber` lớn nhất đã được visit ở attempt hiện tại.
- Cancel từ `SCHEDULED` hoặc `IN_PROGRESS`; cancel idempotent nếu đã `CANCELLED`.
