# Requirement 025 — Ràng buộc vận hành chuyến

Trạng thái: **Implemented** — kiểm tra non-Docker đạt ngày 2026-09-21; PostgreSQL integration cần Docker.

## Bối cảnh

Hệ thống đã có lifecycle chuyến, phân công xe/tài xế, check-in và lịch tự động nhưng một số thao tác vận hành vẫn có thể đi qua khi thiếu điều kiện nghiệp vụ. Mục tiêu là ngăn chuyến khởi hành/hoàn thành sai trạng thái và giúp điều phối viên hiểu rõ lý do bị chặn.

## Phạm vi MVP

- Bắt buộc chuyến có tài xế active trước khi khởi hành.
- Chặn khởi hành ngoài cửa sổ thời gian cấu hình.
- Chặn xung đột xe/tài xế theo khoảng thời gian dự kiến, không chỉ cùng giờ xuất phát.
- Chỉ hoàn thành chuyến sau khi đã check-in trạm cuối; giữ một đường override rõ ràng cho quản trị viên ở giai đoạn sau, chưa mở trong MVP.
- Hủy chuyến phải có lý do; lưu lý do trong trip snapshot/lịch sử hiện có.
- UI disable/nêu lý do tại thao tác khởi hành, hoàn thành và hủy; backend vẫn là nguồn quyết định cuối cùng.

## Ngoài phạm vi

- RBAC/audit actor đầy đủ.
- Hạng bằng lái, ngày hết hạn, bảo trì xe, nghỉ phép tài xế.
- Tự động cấp tài xế mặc định từ xe cho mọi chuyến.
- Thay đổi schema telemetry/GPS hoặc rule cảnh báo lệch tuyến.

## Acceptance criteria

- AC-01: `POST /trips/{id}/start` trả `409` nếu chuyến chưa có tài xế hoặc tài xế/xe đã inactive.
- AC-02: Start chỉ thành công trong cửa sổ từ `scheduledDepartureAt - earlyWindow` đến `scheduledDepartureAt + lateWindow`; ngoài cửa sổ trả lỗi có mã nghiệp vụ.
- AC-03: Tạo/cập nhật chuyến và sinh chuyến tự động không cho xe hoặc tài xế có các chuyến `SCHEDULED/IN_PROGRESS` bị chồng khoảng thời gian dự kiến.
- AC-04: `POST /trips/{id}/complete` trả `409` nếu chưa ghi nhận trạm cuối của attempt hiện tại.
- AC-05: `POST /trips/{id}/cancel` yêu cầu lý do không trống và lưu được lý do; hủy idempotent giữ nguyên lý do ban đầu.
- AC-06: Frontend hiển thị tài xế bắt buộc, trạng thái readiness, lỗi xung đột, điều kiện hoàn thành và textarea lý do hủy.
- AC-07: Migration mới, DTO/API/frontend type đồng bộ; test unit/service và kiểm tra frontend có evidence.

## Quyết định MVP

- `trip.driver_id` là phân công thực tế của chuyến; `vehicle.driver_id` chỉ là phân công hiện tại/mặc định của xe.
- Khoảng bận dự kiến dùng `scheduledDepartureAt` đến `scheduledDepartureAt + route.estimatedTripDurationSeconds`; không cộng buffer trong MVP.
- Cửa sổ start mặc định: sớm 30 phút, trễ 120 phút; cấu hình qua `trip.lifecycle.*`.
