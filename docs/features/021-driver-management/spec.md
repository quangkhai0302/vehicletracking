# Spec 021 — Quản lý tài xế

Trạng thái: **Verified** ngày 2026-09-18.

## Data model

Migration `V14__create_drivers_and_assignments.sql`:

- `drivers`: `id`, `full_name VARCHAR(100)`, `phone_number VARCHAR(20)`, `license_number VARCHAR(50) UNIQUE`, `active BOOLEAN`, `created_at`, `updated_at`.
- `vehicles.driver_id BIGINT NULL REFERENCES drivers(id) ON DELETE RESTRICT`; index tra cứu và unique partial index trên `driver_id WHERE driver_id IS NOT NULL AND active`.
- `trips.driver_id BIGINT NULL REFERENCES drivers(id) ON DELETE RESTRICT` cùng ba snapshot nullable: `driver_name_snapshot`, `driver_phone_snapshot`, `driver_license_number_snapshot`.
- CHECK trên trip buộc `driver_id` và cả ba snapshot cùng null hoặc cùng non-null.
- Unique partial index `trips(driver_id) WHERE driver_id IS NOT NULL AND status='IN_PROGRESS'`.
- Mọi cột mới nullable để dữ liệu cũ migrate an toàn; không sửa migration cũ.

## API contract

### Driver CRUD

- `GET /api/v1/drivers` → `200 DriverResponse[]`, gồm cả active/inactive, sắp xếp GPLX.
- `GET /api/v1/drivers/{id}` → `200` hoặc `404`.
- `POST /api/v1/drivers` với `{fullName, phoneNumber, licenseNumber}` → `201` + `Location`.
- `PUT /api/v1/drivers/{id}` cùng payload → `200`; inactive nhận `409`.
- `DELETE /api/v1/drivers/{id}` → `204`, soft-delete, idempotent; nếu còn gán xe active hoặc trip `SCHEDULED/IN_PROGRESS` → `409`.

Validation: tên 1–100 sau trim; điện thoại 7–20 ký tự, bắt đầu bằng số hoặc `+`, chỉ gồm số/khoảng trắng/`().-`; GPLX 1–50 ký tự chữ/số/`.`/`-`, trim và uppercase; GPLX unique kể cả inactive.

### Vehicle assignment

- Payload `POST /api/v1/vehicles` và `PUT /api/v1/vehicles/{id}` giữ nguyên contract cũ, không nhận `driverId`.
- `PUT /api/v1/vehicles/{id}/driver` body `{driverId}` → `200 VehicleResponse`.
- `DELETE /api/v1/vehicles/{id}/driver` → `204`.
- Chỉ xe active và driver active được gán. Driver đã gán cho xe active khác → `409`.
- `VehicleResponse` thêm `driver: DriverSummaryResponse | null`.
- Khi ngừng xe, assignment hiện tại được xóa; lịch sử chuyến không đổi.

### Trip assignment

- `TripCreateRequest` thêm `driverId` nullable; `null` nghĩa là không gán. UI preselect tài xế hiện tại của xe nhưng backend luôn lưu đúng giá trị request để không nhập nhằng.
- `PUT /api/v1/trips/{id}/driver` body `{driverId}` → `200 TripDetailResponse`.
- `DELETE /api/v1/trips/{id}/driver` → `204`.
- Chỉ chuyến `SCHEDULED` được đổi/bỏ gán; driver phải active.
- `TripSummaryResponse` thêm `driver: DriverSnapshotResponse | null`; detail kế thừa qua summary.
- Mỗi lần gán khi `SCHEDULED`, `driver_id` và snapshot được cập nhật cùng transaction. Thay đổi hồ sơ hoặc assignment xe không cập nhật snapshot đã lưu.
- Start chuyến có driver inactive hoặc driver đang ở trip `IN_PROGRESS` khác → `409`; start không driver vẫn hợp lệ để tương thích ngược.

Mọi lỗi theo Spring Problem Details hiện tại; validation `400`, not found `404`, conflict `409`.

## UI contract

- Fleet Workspace thêm tab `Tài xế`, badge số active, search theo tên/điện thoại/GPLX, filter active/inactive/all, empty/loading/error/retry.
- Driver form tạo/sửa ba trường; dirty close confirmation; inactive driver không sửa.
- Driver card cho sửa/ngừng sử dụng và hiển thị xe hiện gán nếu có.
- Vehicle Editor có selector tài xế active hoặc `Chưa gán`; save thông tin xe rồi đồng bộ assignment theo API, giữ lỗi có kiểm soát.
- Trip Editor tải drivers, mặc định theo assignment xe và cho chọn driver active khác hoặc không gán.
- Trip card/detail hiển thị snapshot tài xế; detail `SCHEDULED` cho đổi/bỏ gán.
- Responsive dùng breakpoint/layout sẵn có trong `fleet.css`; không thêm router hoặc state global mới.

## Bảo mật và tương thích

- Không lưu secret. Điện thoại/GPLX là dữ liệu cá nhân nghiệp vụ, chỉ trả qua API nội bộ hiện có và không ghi log.
- Feature không tạo authorization mới; đây là giới hạn của hệ thống hiện tại.
- Contract response và trip request chỉ thêm field nullable. Payload CRUD xe cũ giữ nguyên; assignment xe dùng endpoint riêng, nên client cũ tiếp tục dùng được. Trip/vehicle cũ có driver null.

## Mapping acceptance criteria

Spec này giữ nguyên AC-01 đến AC-09 trong `requirement.md`; DB constraint, service transaction, API DTO và UI state cùng bảo vệ các tiêu chí tương ứng.
