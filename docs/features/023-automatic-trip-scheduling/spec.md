# Spec 023 — Lập lịch chuyến tự động

Trạng thái: **Implemented**.

## Data model

### `trip_schedules`

- `id` bigint identity primary key.
- `name` varchar(150) nullable.
- `route_id`, `vehicle_id`, `driver_id` bigint not null foreign key.
- `frequency` `ONCE` hoặc `WEEKLY`.
- `scheduled_date` date nullable; bắt buộc với `ONCE`.
- `weekdays_mask` smallint; `0` với `ONCE`, bit ISO Mon=1 … Sun=64 với `WEEKLY`.
- `departure_time` time without time zone not null.
- `timezone` varchar(64) not null.
- `effective_from` date not null; `effective_until` date nullable.
- `enabled` boolean not null default true.
- `last_run_at` timestamptz nullable, `last_run_status` varchar(20) nullable, `last_run_message` varchar(500) nullable.
- `version` bigint not null dùng cho optimistic locking khi polling cập nhật run-state đồng thời với chỉnh sửa lịch.
- `created_at`, `updated_at` timestamptz not null.

### Trip provenance/idempotency

`trips` nhận `schedule_id` nullable và `schedule_occurrence_at` timestamptz nullable. Partial unique index `(schedule_id, schedule_occurrence_at)` khi `schedule_id IS NOT NULL` bảo đảm retry không tạo cùng occurrence hai lần. Trip thủ công giữ hai cột null.
Database bắt buộc hai cột provenance cùng null hoặc cùng có giá trị.

## API contract

- `GET /api/v1/schedules` → `ScheduleSummary[]`.
- `POST /api/v1/schedules` → `201 ScheduleDetail`.
- `PUT /api/v1/schedules/{id}` → `ScheduleDetail`; chỉ sửa cấu hình, không sửa trip đã sinh.
- `POST /api/v1/schedules/{id}/enable` và `/disable` → `ScheduleDetail`.

Request gồm `name`, `routeId`, `vehicleId`, `driverId`, `frequency`, `scheduledDate`, `weekdaysMask`, `departureTime`, `timezone`, `effectiveFrom`, `effectiveUntil`.

Response gồm cấu hình đã chuẩn hóa, tên route/vehicle/driver, `enabled`, `nextRunAt`, `lastRunAt`, `lastRunStatus`, `lastRunMessage`; `lastRunAt` là thời điểm scheduler xử lý occurrence gần nhất.

Validation: ID dương; assignment active; route active và có đủ station active; timezone hợp lệ; time/date hợp lệ; `ONCE` có scheduledDate và mask 0; `WEEKLY` có mask 1–127 và effective range hợp lệ.

## Scheduler behavior

- Poll mặc định mỗi 60 giây, mở rộng lịch trong 7 ngày kể từ `Clock` hiện tại.
- Local date + departure time + timezone được resolve thành `Instant`; giờ nằm trong DST gap bị từ chối, còn DST overlap dùng offset sớm hơn một cách xác định.
- Mỗi occurrence gọi service tạo trip nội bộ để giữ lock, validation và snapshot hiện có.
- Duplicate do unique index được coi là idempotent; conflict/inactive được ghi vào `last_run_*` và không dừng lịch khác.
- Khi sinh trip tự động, xe và tài xế được kiểm tra không có trip `SCHEDULED`/`IN_PROGRESS` khác cùng thời điểm; xung đột được ghi là thất bại của occurrence.
- Tắt lịch ngăn tạo occurrence mới; trip đã tạo không bị xóa/hủy.
- Trip đã sinh từ lịch không thể xóa vật lý; nếu không thực hiện, điều phối viên dùng thao tác hủy trip hoặc tạm dừng lịch để giữ provenance và idempotency.

## UI contract

`/schedules` thay roadmap bằng business page: header/KPI, filter route/status, list card/table, CTA tạo lịch. Editor có route/vehicle/driver, ONCE/WEEKLY, date hoặc weekdays, time, timezone, effective dates; validation và error inline. Card có bật/tắt với confirmation, edit và link tới `/trips`.

## Security/compatibility

Không thêm secret, auth hoặc RBAC trong feature này. Migration V15 backward-compatible với trip thủ công; backend `ddl-auto=validate` phải pass.
