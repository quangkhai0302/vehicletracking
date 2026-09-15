# Evidence

- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q -DskipTests compile` — PASS.
- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q clean test-compile` — PASS.
- `RouteServiceTest` được gọi riêng nhưng 14 test không chạy được do Mockito inline/Byte Buddy không self-attach trên JDK 26; không có assertion failure.
- Frontend API clients được thêm cho route/trip/telemetry.
- Endpoint source: `route/controller/RouteController.java`, `trip/controller/TripController.java`, `telemetry/controller/TelemetryController.java`, `reroute/controller/NotificationController.java`, `RouteRevisionController.java`.
- Frontend lint/tsc/build — PASS (1896 modules).
- Full integration PostgreSQL/Docker phụ thuộc môi trường hiện tại.

## Sửa lỗi khởi động ngày 2026-09-14

- Log người dùng: PostgreSQL kết nối thành công, Flyway ở V8, JPA khởi tạo thành công; lỗi xuất hiện khi tạo `routeService`: `No default constructor found`.
- Nguyên nhân: `RouteService` có hai constructor không chỉ định constructor injection. Constructor phụ chỉ phục vụ test còn truyền `TripRepository=null`.
- Sửa tại `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/service/RouteService.java`: giữ một constructor với đủ năm dependency; bỏ nhánh bỏ qua kiểm tra trip khi repository null. `RouteServiceTest#setUp` truyền thêm mock TripRepository.
- Regression: `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/service/RouteServiceWiringTest.java#springCreatesControllerAndServiceWithAllRequiredDependencies` khởi tạo Spring context với controller/service thật, repository stub, không cần PostgreSQL/HERE/Mockito agent. Test thất bại đúng lỗi constructor trước sửa (exit 1), thành công sau sửa (exit 0).
- Lệnh từ backend: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q -Dtest=RouteServiceWiringTest test` — PASS, 1 test.
- Hồi quy: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=-javaagent:/home/khainq/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar bash ./mvnw -q -Dtest=RouteServiceWiringTest,RouteServiceTest test` — PASS, 15 test, 0 failures/errors. Agent được truyền riêng cho lệnh test để tránh self-attach; không đổi cấu hình runtime của ứng dụng.
- `git diff --check` — PASS. Chưa chạy lại toàn bộ backend với database người dùng; kết quả trên xác minh wiring Spring của module route và các test service, không phải full application/end-to-end.

## Tích hợp frontend ngày 2026-09-14

- Route UI: `RouteWorkspace`/`RouteDrawer` gọi `PUT /routes/{id}` để sửa lại HERE route và `DELETE /routes/{id}` có xác nhận, đồng bộ danh sách/geometry sau `204`.
- Trip UI: `TripDetailPanel` gọi `PUT /trips/{id}` cho chuyến `SCHEDULED` (ISO UTC) và `DELETE /trips/{id}` có xác nhận; `useFleetWorkspace` cập nhật local detail/list sau khi server thành công.
- Telemetry UI: `TelemetryHistoryPanel` gọi `GET /telemetry/history` theo `tripId`, source, from/to, page/size; có abort cleanup, retry, empty/error và pagination.
- Reroute UI: `RouteRevisionPanel` gọi list/supersede revision; `AlertStream` gọi read-all, read-one và delete với confirmation/error. Không optimistic update khi request thất bại.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint` — PASS, không warning.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit` — PASS.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` — PASS, Vite 8.2.2, 1,899 modules.
- Backend tương thích: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q -DskipTests compile` — PASS; các test controller/service trước đó pass với Mockito javaagent.
- Hồi quy endpoint: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=-javaagent:/home/khainq/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar bash ./mvnw -q -Dtest=RouteControllerTest,FleetControllerTest test` — PASS (exit 0).
- Chưa chạy browser E2E/live HERE/DB trong lượt này; UI mới được chứng minh bằng typecheck/lint/build và contract source.
