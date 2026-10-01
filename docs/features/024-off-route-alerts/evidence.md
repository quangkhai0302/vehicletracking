# Evidence 024 — Cảnh báo xe lệch tuyến

Ngày kiểm tra: **2026-09-21**.

## Bổ sung 2026-09-30 — trung tâm cảnh báo trong phạm vi simulator-only

Đây là bằng chứng cho thay đổi UI **sau** snapshot 2026-09-21 bên dưới; không phải thay đổi detector GPS của feature 024.

- `vehicletracking-frontend/src/pages/AlertsManagementPage.vue`: nhãn read là “Đã đọc”, không còn tuyên bố đã giải quyết sự cố; KPI “Đổi tuyến” thay KPI “Lệch tuyến”, bỏ filter lệch tuyến nhưng vẫn hiển thị notification lịch sử trong “Tất cả”; polling hủy GET cũ và giữ state read/delete đã xác nhận khi response cũ tới sau.
- `vehicletracking-frontend/tests/unit/page-workflows.test.ts`: kiểm tra filter/KPI simulator-only, read/delete không bị stale GET đảo ngược, poll mới thắng poll cũ. `npm run test:unit -- tests/unit/page-workflows.test.ts` exit 0 (**8/8**).
- Node 24: `npm run lint`, `npm run typecheck`, `npm run test:unit` (**26 file, 196 test**), `npm run test:motion` (**5/5**), `npm run build` đều exit 0. Build cảnh báo bundle JS chính 535.55 kB > 500 kB. `git diff --check` exit 0.
- Không sửa backend, API, migration hay dữ liệu cảnh báo. Không kiểm thử browser thật, HERE production hoặc full backend suite sau thay đổi UI. Báo sự cố thủ công, trạng thái resolved và phân trang vẫn chưa triển khai; xem `review.md`.

## Source evidence

| Acceptance criterion | Evidence sau implementation |
|---|---|
| AC-01 | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/reroute/service/OffRouteEvaluationService.java` — `evaluateCurrent()` khóa trip đang chạy, lấy `TripRouteGeometryService.routeForTracking()` (route gốc hoặc live revision), xác định section còn lại và gọi `RoutePositionMatcher.project()`. |
| AC-02 | `OffRouteEvaluationService.evaluateCurrent()` bỏ qua detector disabled, trip không `IN_PROGRESS`, sample không phải `GPS`, sai attempt hoặc thiếu geometry; threshold hiệu dụng dùng `max(configured, accuracyMeters)`. |
| AC-03 | `TripOffRouteAlertStateEntity.observeBreach()`, `markActive()`, state `active`, `episode` và `OffRouteProperties` consecutive/grace; dedupe key `OFF_ROUTE_DETECTED:<trip>:<attempt>:<episode>`. |
| AC-04 | `TripOffRouteAlertStateEntity.clear()` re-arm sau khi về corridor; `resetForAttempt()` xóa state detector khi attempt thay đổi. |
| AC-05 | `TripNotificationEntity` và `NotificationResponse` có distance/threshold/duration; `NotificationService`, `OperationsSnapshotService` và SSE dùng cùng notification contract hiện hữu. |
| AC-06 | `TelemetryService.evaluatePostCommitSafely()` gọi off-route sau commit và bắt `RuntimeException`; migration `V16__create_off_route_alerts.sql` mở rộng CHECK/type/metrics mà không sửa migration cũ. |
| AC-07 | `vehicletracking-frontend/src/pages/AlertsManagementPage.tsx` có KPI, filter, loading, empty, error/retry, read/read-all/delete và link mở giám sát; route `/alerts` trong `App.tsx`, nav không còn `planned`; `MapComponent` nhận `tripId` query để chọn/focus chuyến. |
| AC-08 | V16, `OffRouteProperties`, backend/frontend types và `TripOffRouteAlertStateTest` đã thêm; lint/tsc/build và non-Docker backend tests đạt. PostgreSQL integration còn phụ thuộc Docker. |

## Verification commands

- `git diff --check` — đạt.
- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build` — exit 0; lint còn 6 warning không chặn.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — exit 0, 254 tests passed.
- `cd vehicletracking-backend && ./mvnw test` — 6 test tích hợp lỗi vì môi trường không có Docker daemon; không có failure assertion.

Chưa tuyên bố test tích hợp PostgreSQL/Flyway thành công vì Docker daemon không khả dụng trong môi trường kiểm tra.
