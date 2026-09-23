# Survey 024 — Cảnh báo xe lệch tuyến

Ngày khảo sát: **2026-09-21**. Working tree có thay đổi chưa commit của Feature 021–023; phải bảo toàn.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| GPS chỉ nhận cho trip đang chạy, được lưu append-only và cập nhật vị trí mới nhất | `telemetry/service/TelemetryService.java:35-70`; `db/migration/V5__create_telemetry_and_simulation.sql:1-30` | Điểm tích hợp detector là sau commit của telemetry |
| Sau commit hiện đã gọi đánh giá reroute best-effort | `TelemetryService.evaluateRerouteSafely()`; `RerouteEvaluationService.evaluateCurrent()` | Off-route nên chạy cùng hook nhưng độc lập transaction |
| Geometry tuyến/revision đã có service dùng chung | `reroute/service/TripRouteGeometryService.java:21-46` | Không đọc route geometry riêng hoặc tạo snapshot khác |
| Utility point-to-polyline đã trả distance và section projection | `traffic/matching/RoutePositionMatcher.java:25-75` | Có thể tái sử dụng cho detector |
| Notification hiện lưu bền vững, dedupe key unique và được đưa vào snapshot/SSE | `reroute/entity/TripNotificationEntity.java`; `OperationsSnapshotService.java:24-36`; `telemetry/controller/TelemetryController.java:31-36` | Mở rộng notification type là đủ cho kênh hiện có |
| DB CHECK hiện chỉ cho hai loại reroute | `db/migration/V7__create_route_revisions_and_notifications.sql:75-107` | Cần migration V16 thay constraint, không sửa V7 |
| Frontend AlertStream hiện chỉ mô tả notification đổi tuyến; `/alerts` là roadmap | `components/operations/AlertStream.tsx:1-42`; `App.tsx` route alerts; `app/routeConfig.ts:52-55` | Cần page business riêng và cập nhật type/icon |
| API notifications đã có read, read-all, delete, list | `services/notifications.ts:1-24`; `reroute/controller/NotificationController.java:11-23` | Tái sử dụng service contract, chỉ thêm field optional |

## Rủi ro

- Geometry revision và next-stop phải đồng nhất với attempt để không báo sai section đã đi qua.
- GPS sai số lớn có thể tạo false positive nếu không dùng effective threshold.
- Callback sau commit có thể chạy đồng thời; trip lock và state row lock cần bảo đảm episode không tạo notification trùng.
- Detector không được gọi lại simulator hoặc làm rollback transaction telemetry.

## Hạ tầng

PostgreSQL/Flyway là nguồn sự thật; `ddl-auto=validate` đang bật trong `application.yaml`. Compose hiện chỉ cung cấp PostgreSQL, không có queue/Redis; MVP dùng transaction + unique dedupe key.
