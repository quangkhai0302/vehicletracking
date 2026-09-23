# Spec 026 — Dashboard quản lý vận hành

Trạng thái: **Implemented**.

## API contract

`GET /api/v1/dashboard/summary` trả `200`:

```json
{
  "serverTime": "2026-09-21T08:00:00Z",
  "activeVehicleCount": 4,
  "activeDriverCount": 3,
  "tripsInProgress": 2,
  "scheduledTrips": 5,
  "completedTrips": 8,
  "cancelledTrips": 1,
  "overdueTrips": 1,
  "offRouteVehicleCount": 1,
  "unreadAlertCount": 2,
  "pendingAlerts": []
}
```

`pendingAlerts` dùng `NotificationResponse` hiện hữu, tối đa 5 item chưa đọc mới nhất.

## Rules

- `overdueTrips`: `IN_PROGRESS` và `plannedEndAt < serverTime`. `plannedEndAt` lấy arrival time của stop cuối, fallback route estimated duration.
- `offRouteVehicleCount`: state detector `active=true` và trip status `IN_PROGRESS`; mỗi trip/xe chỉ tính một lần.
- Các count trạng thái trip bao gồm toàn bộ dữ liệu hiện có, không giới hạn “hôm nay”.
- Count unread không bị giới hạn 50; danh sách pending mới giới hạn 5.

## UI contract

- 9 KPI cards: xe active, tài xế active, đang chạy, chờ khởi hành, hoàn thành, đang trễ, lệch tuyến, cảnh báo chưa đọc, đã hủy.
- Danh sách tối đa 6 chuyến gần đây; chuyến `IN_PROGRESS` quá planned end hiển thị nhãn “Đang trễ”.
- Danh sách cảnh báo chưa đọc hiển thị xe/chuyến và link mở `/operations?tripId=...`; heading link tới `/alerts`.
- Loading hiển thị skeleton KPI và trạng thái tải; lỗi có retry; không có alert có empty state.
- Dashboard gọi lại mỗi 15 giây và dùng `serverTime` backend cho so sánh thời gian.

## Tương thích và bảo mật

- Không đổi schema/migration; dùng các repository count/query hiện có và bổ sung aggregate query.
- Endpoint không nhận secret hoặc dữ liệu nhạy cảm ngoài thông tin vận hành đã được phép hiển thị.
- Phân quyền endpoint sẽ dùng auth/RBAC hiện có khi feature phân quyền được triển khai.
