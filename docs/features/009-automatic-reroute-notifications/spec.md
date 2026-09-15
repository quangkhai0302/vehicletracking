# Specification

## Business rules

- Chỉ trip `IN_PROGRESS` được đánh giá.
- Nguồn hợp lệ: `HERE_LIVE`, `HERE_LAST_KNOWN`; cần `trafficFetchedAt` mới hơn checkpoint trước.
- Breach: `BLOCKED`, hoặc `dynamic - baseline >= reroute.delay-seconds` và dynamic >= `(100 + reroute.delay-percent)%` baseline. Mặc định lần lượt 600 giây và 30%, đọc từ `REROUTE_DELAY_SECONDS`/`REROUTE_DELAY_PERCENT`.
- Hai fetch liên tiếp cùng fingerprint mới trigger; fingerprint gồm trạng thái, loại/id segment và stop sequence. Cùng fingerprint trong cooldown 300 giây không lặp thông báo.
- Revision dùng vị trí telemetry hiện tại làm origin và các stop chưa check-in làm waypoint. Route gốc và `trip_stops` không bị sửa.

## API

- `GET /api/v1/trips/{tripId}/revisions` → danh sách revision mới nhất trước, gồm stop/section và baseline/revised ETA.
- `GET /api/v1/notifications?unreadOnly={boolean}` → tối đa 50 thông báo gần nhất, kèm trip/vehicle, lý do, stop ảnh hưởng, ETA cũ/mới và trạng thái đọc.
- `POST /api/v1/notifications/{id}/read` → cập nhật `readAt`.
- `GET /api/v1/telemetry/snapshot` và SSE `snapshot` thêm `notifications`.

## Data model

Migration `V7__create_route_revisions_and_notifications.sql` tạo `trip_route_revisions`, `trip_route_revision_stops`, `trip_route_revision_sections`, `trip_traffic_alert_states`, `trip_notifications`; có FK, unique active revision, dedupe key, index thời gian và check constraint.

## Security/compatibility

HERE secret chỉ backend. Các field mới trong ETA/snapshot là additive; frontend cũ được normalization về mảng rỗng khi backend chưa nâng cấp.
