# Spec — Báo cáo và thống kê vận hành

## API

`GET /api/v1/reports/operations`

Query parameters:

| Parameter | Kiểu | Bắt buộc | Ý nghĩa |
|---|---|---|---|
| `from` | `YYYY-MM-DD` | Không | Ngày bắt đầu UTC; mặc định 29 ngày trước ngày `to` |
| `to` | `YYYY-MM-DD` | Không | Ngày kết thúc UTC, inclusive; mặc định ngày hiện tại |
| `vehicleId` | positive long | Không | Lọc xe |
| `driverId` | positive long | Không | Lọc tài xế đang gán cho chuyến |

`from > to` hoặc ID không dương trả HTTP 400 Problem Detail.

Response 200:

```json
{
  "from": "2026-09-01",
  "to": "2026-09-21",
  "generatedAt": "2026-09-21T08:00:00Z",
  "tripCount": 10,
  "completedTripCount": 8,
  "totalDistanceMeters": 123000,
  "totalRunningSeconds": 72000,
  "onTimeRatePercent": 87.5,
  "lateTripCount": 1,
  "offRouteEventCount": 2,
  "overspeedEventCount": 3,
  "speedLimitKmh": 80.0
}
```

## Metric contract

- Trip scope: `scheduledDepartureAt >= from 00:00 UTC` và `< (to + 1 day) 00:00 UTC`.
- `totalDistanceMeters`: tổng distance route snapshot.
- `totalRunningSeconds`: actual started-to-ended; in-progress dùng `generatedAt`.
- `onTimeRatePercent`: completed on-time / completed, làm tròn hai chữ số; mẫu số 0 trả 0. Mốc kế hoạch dùng `scheduledDepartureAt + finalStop.arrivalOffsetSeconds`, không dùng live ETA đã bị cập nhật bởi traffic.
- `lateTripCount`: completed late + in-progress quá planned end.
- `offRouteEventCount`: count notification `OFF_ROUTE_DETECTED` theo createdAt và filter.
- `overspeedEventCount`: GPS sample speed > `speedLimitKmh`, đếm transition vào trạng thái vi phạm theo `(tripId, attemptNumber)`; đây là “vượt ngưỡng cấu hình”, không tự khẳng định vi phạm biển báo theo từng đoạn đường.
- Khoảng ngày inclusive không vượt quá `reporting.max-range-days` (mặc định 366), quá giới hạn trả 400.

## UI contract

Trang `ReportsPage` hiển thị hero, bốn KPI chính (chuyến, km, runtime, đúng giờ), ba KPI rủi ro (trễ, lệch tuyến, quá tốc độ), bộ lọc ngày/xe/tài xế và định nghĩa chỉ số. Có loading, error/retry, empty; responsive dưới 640px.

## Data/migration

Không thêm bảng hoặc sửa migration. Config mới `reporting.default-speed-limit-kmh` có env `REPORT_DEFAULT_SPEED_LIMIT_KMH`, mặc định 80 và được validation positive; `reporting.max-range-days` có env `REPORT_MAX_RANGE_DAYS`, mặc định 366.
