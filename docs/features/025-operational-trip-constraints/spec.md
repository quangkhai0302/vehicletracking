# Spec 025 — Ràng buộc vận hành chuyến

Trạng thái: **Implemented**.

## Configuration

```yaml
trip:
  lifecycle:
    early-start-window-seconds: 1800
    late-start-window-seconds: 7200
```

Giá trị không âm; early/late có thể bằng 0. Mặc định tương ứng 30 phút và 120 phút.

## API contract

- `POST /api/v1/trips/{id}/start`: kiểm tra driver, active resources, start window, running conflict; lỗi nghiệp vụ trả `409` Problem Details với `detail` tiếng Việt.
- `POST /api/v1/trips/{id}/complete`: yêu cầu trip `IN_PROGRESS` và final stop visit của attempt hiện tại; thiếu visit trả `409`.
- `POST /api/v1/trips/{id}/cancel` nhận `{ "reason": "..." }`; reason trim, 3–500 ký tự; thiếu/sai trả `400`; trip đã cancel trả response cũ, không ghi đè reason.
- `POST /api/v1/trips` và `PUT /api/v1/trips/{id}` dùng conflict interval cho resource khi tạo/cập nhật giờ; chuyến nháp có thể chưa có tài xế, còn lịch tự động luôn tham chiếu tài xế active.

## Data model

V17 thêm `trips.cancellation_reason VARCHAR(500)` nullable và check: chỉ `CANCELLED` mới có reason khác null; các status khác phải null. Dữ liệu cũ `CANCELLED` chưa có reason được backfill giá trị hệ thống hoặc cho phép null để migration không phá dữ liệu lịch sử.

Repository thêm query overlap theo vehicle/driver và status `SCHEDULED, IN_PROGRESS`, loại trừ trip hiện tại khi update. Khoảng cuối dự kiến được tính ở service bằng route estimated duration.

## UI contract

- Trip editor cho phép lưu chuyến nháp chưa có tài xế nhưng phải cảnh báo rõ; không thể khởi hành cho tới khi chọn tài xế active.
- Trip detail hiển thị readiness: tài xế, cửa sổ start, conflict và final-stop check-in.
- Nút Start/Complete bị disable theo dữ liệu đã tải nhưng API vẫn quyết định cuối cùng.
- Dialog Cancel có textarea bắt buộc, hiển thị lỗi inline và giữ dialog mở khi API trả lỗi.
- Chi tiết CANCELLED hiển thị reason nếu có.

## Rules

- Conflict interval half-open `[departure, departure + estimatedTripDuration)`; cùng endpoint/time boundary không bị xem là overlap.
- `IN_PROGRESS` trip không thể đổi driver, route hoặc departure.
- Complete chỉ thành công khi stop có `sequenceNumber` lớn nhất đã được visit ở attempt hiện tại.
- Cancel từ `SCHEDULED` hoặc `IN_PROGRESS`; cancel idempotent nếu đã `CANCELLED`.
