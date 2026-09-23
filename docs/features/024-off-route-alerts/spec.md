# Spec 024 — Cảnh báo xe lệch tuyến

Trạng thái: **Implemented**.

## Data model

### `trip_off_route_alert_states`

- `trip_id` bigint primary key/FK `trips`.
- `attempt_number` integer not null.
- `breach_started_at` timestamptz nullable.
- `consecutive_breach_count` integer not null default 0.
- `active` boolean not null default false.
- `episode` integer not null default 0.
- `last_distance_meters` double precision nullable.
- `last_recorded_at` timestamptz nullable.
- `updated_at` timestamptz not null.

State được lock theo trip trong transaction detector; attempt đổi thì reset count/active và bắt đầu episode mới.

### `trip_notifications` additions

V16 mở rộng `type` với `OFF_ROUTE_DETECTED`, thêm `measured_distance_meters`, `threshold_distance_meters`, `breach_duration_seconds` nullable để notification cũ vẫn tương thích.

## Configuration

`offroute.enabled` (mặc định `true`), `offroute.threshold-meters` (150), `offroute.grace-period-seconds` (30), `offroute.consecutive-samples` (3). Giá trị phải không âm; threshold > 0 và consecutive >= 1.

## Detection flow

1. `TelemetryService` commit GPS sample, latest position và check-in.
2. After-commit callback gọi `OffRouteEvaluationService.evaluateCurrent(tripId)`.
3. Service lock trip; bỏ qua nếu không `IN_PROGRESS`, source không phải GPS, attempt/position không phù hợp hoặc detector disabled.
4. Lấy route geometry hiện hành, xác định first remaining section theo stop chưa check-in, project point và tính effective threshold.
5. Cập nhật state. Nếu vượt ngưỡng, tăng count và giữ `breachStartedAt`; khi đủ count + duration và state chưa active, tạo notification duy nhất cho `trip:attempt:episode`.
6. Nếu quay lại corridor, clear state; lần lệch tiếp theo tăng episode và được cảnh báo lại.

## Notification contract

`NotificationResponse` giữ field cũ và thêm `measuredDistanceMeters`, `thresholdDistanceMeters`, `breachDurationSeconds`. `type=OFF_ROUTE_DETECTED`, `severity=MAJOR`, title “Xe lệch tuyến”, reason chứa route/trip và thông số định dạng cho người vận hành. Detector ưu tiên geometry của live revision đang `ACTIVE`, sau đó fallback về route gốc.

API dùng contract notifications hiện hữu:

- `GET /api/v1/notifications?unreadOnly=false` trả tối đa 50 notification mới nhất; `OFF_ROUTE_DETECTED` có ba metric mới, notification cũ trả các metric này là `null`.
- `POST /api/v1/notifications/{id}/read` đánh dấu một notification đã đọc, `POST /api/v1/notifications/read-all` đánh dấu tất cả notification chưa đọc.
- `DELETE /api/v1/notifications/{id}` xóa notification; trả `204`, hoặc `404` nếu không tìm thấy.
- Snapshot `GET /api/v1/telemetry/snapshot` và SSE `/api/v1/telemetry/stream` tiếp tục phát cùng danh sách notification để AlertStream cập nhật realtime.

## UI contract

`/alerts` thay roadmap bằng business page: KPI tổng/chưa đọc/lệch tuyến, filter type/severity, list card hiển thị xe, trip, distance, threshold, duration, thời điểm; actions read/read-all/delete; loading, empty, error/retry và responsive.

AlertStream trong operations nhận type mới, icon/legend phù hợp và vẫn hiển thị notification reroute.

## Error and compatibility

Detector là best-effort sau commit; exception không làm rollback telemetry. Trip thủ công, simulator và route thiếu geometry không phát alert. Không thêm secret, auth/RBAC hay provider ngoài.

Các thao tác list/read/delete giữ semantics notification hiện tại; MVP chưa thêm vòng đời `acknowledged/resolved` riêng cho off-route alert.
