# Evidence 026 — Dashboard quản lý vận hành

Ngày kiểm tra: **2026-09-21**.

## Source evidence

| Acceptance criterion | Evidence sau implementation |
|---|---|
| AC-01 | `dashboard/service/DashboardService.java` tổng hợp count xe/tài xế/trip, server time và `pendingAlerts`; `DashboardController` expose `GET /api/v1/dashboard/summary`. |
| AC-02 | `DashboardService.plannedEndAt()` lấy stop cuối/fallback route duration và chỉ đếm `IN_PROGRESS` quá hạn. |
| AC-03 | `TripOffRouteAlertStateRepository.countActiveForStatus(IN_PROGRESS)` lọc state active gắn với chuyến đang chạy. |
| AC-04 | `TripNotificationRepository.countByReadAtIsNullAndDismissedAtIsNull()` đếm toàn bộ cảnh báo đang hiển thị; service giới hạn `pendingAlerts` còn 5 item. |
| AC-05 | `DashboardPage.tsx` có 9 KPI cards, danh sách chuyến gần đây, cảnh báo chưa đọc và link tới `/alerts`/`/operations?tripId=...`. |
| AC-06 | `DashboardPage.tsx` polling 15 giây, loading/error/retry/empty; lỗi poll không xóa dữ liệu dashboard đã có. |
| AC-07 | Có `DashboardServiceTest`, `DashboardControllerTest`; frontend lint/typecheck và `git diff --check` đã chạy. |

## Verification commands

- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint` — exit 0; còn 6 warning lint không chặn.
- `cd vehicletracking-frontend && ./node_modules/.bin/tsc --noEmit` — exit 0.
- `git diff --check` — đạt.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — exit 0, 254 tests passed.
- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` — exit 0.
- Full backend `./mvnw test` còn 6 integration test lỗi do Docker daemon không khả dụng.
