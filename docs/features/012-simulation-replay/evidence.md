# Evidence — Verified

Ngày kiểm tra: 2026-09-15. Working tree chưa commit; không thay đổi hoặc đọc secret.

## Acceptance criteria

| AC | Evidence source | Evidence kiểm thử |
|---|---|---|
| AC1 — giữ trip/route/run | `SimulationService.reset`, `TripEntity.replay`, migration V9 | `SimulationReplayTest`; `OperationsIntegrationTest.resetRetainsHistoryAndIsIdempotent` |
| AC2 — tách lịch sử theo lần | `attempt_number` trên trip/sample/visit/checkpoint; bảng `simulation_attempts`; bộ lọc API | Integration test giữ sample/visit lần 1 và đọc lại bằng `attemptNumber=1` |
| AC3 — lần mới bắt đầu sạch | `SimulationRunEntity.replay`, `TripCheckInStateEntity.replay` | Reset trả PAUSED/0/1x; lần 2 check-in lại stop 1, không nối segment từ sample lần 1 |
| AC4 — realtime/ETA không dùng dữ liệu cũ | `OperationsSnapshotService`, `TrafficEtaService`, `RerouteEvaluationService` kiểm tra attempt hiện hành | Integration test snapshot rỗng ngay sau reset, rồi chỉ trả sample attempt 2 |
| AC5 — validation trước khi ghi | `SimulationService.reset` | Unit test từ chối GPS, xe inactive, chuyến khác IN_PROGRESS; integration test xác nhận không tạo archive dở |

## Lệnh và kết quả

- Backend full suite, JDK 26 + Mockito javaagent + Docker/PostgreSQL 17: `./mvnw test -q -Dlogging.level.root=WARN` — exit 0, 194 test, 0 failure, 0 error, 0 skipped.
- Riêng integration mục tiêu: `OperationsIntegrationTest` 13/13 và `OperationsHttpIntegrationTest` 2/2. Flyway V1–V9 chạy trên database Testcontainers; Hibernate `ddl-auto=validate` khởi động thành công.
- Backend compile gồm test source: `./mvnw test -DskipTests -q` — exit 0.
- Frontend Node 24.16.0: `npm run lint`, `tsc --noEmit`, `npm run build` — exit 0. Lint còn hai warning `set-state-in-effect` trong `useFleetWorkspace`; không có lint error. Build tạo bundle production thành công.
- `git diff --check` — không có lỗi whitespace.

## Giới hạn

Browser automation không được chạy lại trong lượt này vì quyền mở trình duyệt đã bị bộ duyệt môi trường từ chối trước đó. Fixture browser đã được đổi sang reset cùng trip/attempt nhưng kết quả UI chỉ được xác minh bằng lint, typecheck và build. Migration chỉ chạy trên PostgreSQL tạm của Testcontainers, không tự áp dụng vào database phát triển của người dùng.
