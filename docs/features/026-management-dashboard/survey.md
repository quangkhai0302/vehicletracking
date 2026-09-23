# Survey 026 — Dashboard quản lý vận hành

Ngày khảo sát: **2026-09-21**. Working tree có thay đổi chưa commit của Features 021–025; không hoàn tác.

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Dashboard đã có route riêng nhưng chỉ gọi REST danh mục một lần | `vehicletracking-frontend/src/pages/DashboardPage.tsx`, `useEffect()` và `Promise.all(fetchFleetVehicles, fetchDrivers, fetchTrips)` | Cần refresh định kỳ và nguồn aggregate cho KPI mới |
| Snapshot vận hành đã có trips/check-ins/notifications | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/telemetry/dto/OperationsSnapshot.java`; `OperationsSnapshotService.snapshot()` | Có thể tái sử dụng cho map, nhưng dashboard KPI cần endpoint nhẹ hơn |
| Trip có status, planned end và route duration | `TripSummaryResponse.from()`; `TripEntity`/`TripStopEntity` | Có thể định nghĩa overdue theo planned end |
| Off-route detector lưu state active theo trip | `TripOffRouteAlertStateEntity.active`; `OffRouteEvaluationService.evaluateCurrent()` | Count cần lọc state active và trip IN_PROGRESS |
| Notification service chỉ trả top 50 recent/unread | `NotificationService.recent()`; `TripNotificationRepository.findTop50...()` | Dashboard cần count repository riêng để không giới hạn tổng unread |
| Dashboard đang hiển thị “Chỉ số đang phát triển” cho cảnh báo/lệch tuyến | `DashboardPage.tsx` phần `dashboard-data-gaps` | Thay bằng card và danh sách actionable |

## Working tree/risk

- Không thêm migration vì feature chỉ bổ sung aggregate query/API.
- Backend hiện yêu cầu Java 26; môi trường kiểm tra hiện tại chỉ có Java 17.
- Frontend build yêu cầu Node 22.12+, trong khi môi trường hiện tại là Node 18.19.1.
