# Walkthrough 021 — Quản lý tài xế

## Kết quả

Fleet Workspace nay quản lý được hồ sơ tài xế, tài xế hiện tại của xe và tài xế thực hiện chuyến. Trip lưu snapshot tài xế riêng, nên lịch sử không đổi khi hồ sơ hoặc assignment xe thay đổi.

## Luồng người dùng

1. Mở tab **Tài xế** để tìm/lọc, tạo hoặc sửa họ tên, số điện thoại và GPLX.
2. Trong form xe, chọn một tài xế active hoặc **Chưa gán tài xế**. Frontend lưu thông tin xe qua contract cũ rồi đồng bộ assignment qua endpoint riêng.
3. Khi tạo chuyến, selector tài xế mặc định theo xe đã chọn; điều phối viên có thể chọn người khác hoặc bỏ trống.
4. Trong chi tiết chuyến `SCHEDULED`, dùng **Đổi tài xế** để gán/bỏ gán. Sau khi khởi hành, snapshot chỉ còn để hiển thị.
5. Không thể ngừng tài xế khi họ còn được gán cho xe active hoặc chuyến chưa kết thúc. Không thể bắt đầu hai chuyến đồng thời với cùng tài xế.

## Luồng dữ liệu

- `DriverEditor`/Fleet Workspace gọi `services/fleet.ts`; không gọi HTTP trực tiếp trong component.
- `DriverController` và endpoint assignment chỉ nhận/trả DTO; nghiệp vụ và transaction nằm trong service.
- `vehicles.driver_id` biểu diễn assignment hiện tại. `trips.driver_id` cùng ba cột snapshot biểu diễn assignment lịch sử của chuyến.
- Service dùng pessimistic lock cho driver/vehicle/trip; PostgreSQL partial unique index là lớp bảo vệ cuối cho race condition.
- Payload CRUD xe không chứa `driverId`, giữ tương thích client cũ. `PUT/DELETE /vehicles/{id}/driver` chịu trách nhiệm assignment.

## Điểm code quan trọng

- Migration và constraint: `V14__create_drivers_and_assignments.sql`.
- Driver lifecycle: `driver/service/DriverService.java`.
- Assignment xe: `vehicle/service/VehicleService.java`.
- Snapshot và kiểm tra lúc start: `trip/entity/TripEntity.java`, `trip/service/TripService.java`.
- State và đồng bộ API frontend: `hooks/useFleetWorkspace.ts`.
- UI: `FleetWorkspace.tsx`, `DriverEditor.tsx`, `VehicleEditor.tsx`, `TripEditor.tsx`, `TripDetailPanel.tsx`.

## Cách kiểm tra nhanh

1. Chạy backend và frontend theo hướng dẫn repository.
2. Tạo hai tài xế, thử trùng GPLX để thấy `409`.
3. Gán một tài xế cho xe A rồi thử gán cùng người cho xe B để thấy conflict.
4. Tạo chuyến từ xe A, kiểm tra tài xế được preselect; đổi sang người khác trước khi khởi hành.
5. Sửa hồ sơ tài xế và xác nhận chi tiết chuyến vẫn hiển thị snapshot cũ.
6. Khởi hành một chuyến rồi thử khởi hành chuyến khác cùng tài xế.

## Hạn chế

- Không có lịch ca, kiểm tra trùng lịch các trip `SCHEDULED`, phân quyền hoặc lịch sử nhiều lần assignment.
- UI chưa được browser/E2E test tự động; kết quả hiện được bảo đảm bởi backend tests, TypeScript, lint và production build.
