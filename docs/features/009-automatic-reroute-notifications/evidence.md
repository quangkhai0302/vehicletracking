# Evidence

## Bugfix 2026-09-14: null identifier khi chạy mô phỏng có traffic

- Tái hiện: `RerouteSimulationIntegrationTest#firstLiveEvaluationPersistsSharedPrimaryKeyAndUpdatesExistingCheckpoint` thất bại trên PostgreSQL 17 với đúng `AssertionFailure: null identifier (TripTrafficAlertStateEntity)` trước bản sửa. Test chạy mô phỏng cũng phát hiện checkpoint không được lưu dù run vẫn RUNNING (exception bị bắt trong afterCommit).
- Nguyên nhân và sửa: `TripTrafficAlertStateEntity` gán `tripId` trong constructor khiến Spring Data chọn merge cho entity mới dùng `@MapsId`. Constructor hiện để ID null để persist lấy khóa từ quan hệ trip. Không đổi schema hoặc migration V7.
- `TrafficEtaController#calculate` giữ ETA đã tính thành công khi evaluator trong transaction riêng thất bại; test HTTP xác nhận vẫn trả 200, còn trip không tồn tại vẫn trả 404.
- `SimulatorPanel`: đưa điều khiển lên ngay sau chọn chuyến, thêm chữ Bắt đầu/Tạm dừng/Tiếp tục và hướng dẫn theo trạng thái; phân biệt Dừng & hủy chuyến với tạm dừng; thay nhóm nút sự kiện chưa hỗ trợ bằng thông tin rõ ràng. `useTripEta` hiển thị lỗi ngay lần tải đầu thay vì chỉ sau một lần tải thành công.
- Lệnh backend: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=-javaagent:/home/khainq/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar bash ./mvnw -q -Dtest=RerouteSimulationIntegrationTest,TrafficEtaControllerTest,OperationsIntegrationTest,OperationsHttpIntegrationTest,ReroutePolicyTest test` — exit 0; 22 tests, 0 failures/errors, 0 skipped. Testcontainers riêng; không thay đổi dữ liệu development. Test mới xác nhận checkpoint được lưu/cập nhật và mô phỏng chạy hết tuyến, đủ 3 check-in khi dùng phản hồi HERE_LIVE fixture.
- Frontend: `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH` với `npm run lint`, `./node_modules/.bin/tsc --noEmit`, `npm run build` — exit 0; build 1899 modules.
- Giới hạn: chưa xác minh live HERE sau bản sửa; chưa khởi động lại JVM development của người dùng. Browser test fixture lần đầu timeout do assertion chờ chuỗi HTTP 500 thay vì detail của response. Đã chỉnh script ở `/tmp/vehicletracking-simulator-ui-check.mjs`, nhưng lần chạy lại bị automatic approval review chặn do usage limit; không tuyên bố browser test pass. Full backend suite chưa chạy trong đợt sửa này.
- Log assertion không có tên entity trong test controller là exception được cố ý tạo để thử fallback. Cảnh báo database khi Testcontainers dọn container có xuất hiện cuối bộ test; các báo cáo Surefire phía trên đều pass.

## Source

- Migration: `vehicletracking-backend/src/main/resources/db/migration/V7__create_route_revisions_and_notifications.sql`.
- Evaluator/policy: `.../reroute/service/RerouteEvaluationService.java`, `ReroutePolicy.java`, `config/RerouteProperties.java`; ETA exposes baseline in `traffic/eta/TripEtaResponse.java`.
- Automatic trigger: `.../telemetry/service/TelemetryService.java` evaluates reroute after an accepted GPS or simulator sample; provider/routing errors are best-effort and do not reject telemetry. ETA uses the newest usable `fetchedAt` from both HERE flow and incidents, so a refreshed closure is not hidden by a cached flow response.
- API: `.../reroute/controller/NotificationController.java`, `RouteRevisionController.java`.
- SSE/UI: `.../telemetry/dto/OperationsSnapshot.java`, `vehicletracking-frontend/src/components/operations/AlertStream.tsx`.

## Verification

- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=-javaagent:/home/khainq/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar bash ./mvnw -q -Dtest=ReroutePolicyTest,TrafficEtaServicePolicyTest,HereTrafficProviderTest,TrafficQueryServiceTest,TrafficControllerTest,TrafficRouteMatcherTest,RoutePositionMatcherTest -DfailIfNoTests=false test` — PASS; 24 tests, 0 failures/errors.
- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q -DskipTests compile` — PASS after automatic telemetry trigger and reroute checkpoint hardening.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint` — PASS.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit` — PASS.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` — PASS; 1899 modules, JS 487.81 kB (gzip 144.96), CSS 119.05 kB (gzip 26.30).
- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q test` — NOT PASS in this environment: 79 errors from Mockito inline attach and unavailable Testcontainers/Docker; no assertion failure was reported for the new pure policy test.

## Limitations

Chưa chạy full integration với PostgreSQL/Docker và chưa gọi live HERE trong môi trường kiểm thử; migration cần được Flyway áp dụng trước khi chạy evaluator trên dữ liệu thật.

## Simulator failure hardening

- `TelemetryService` đăng ký trigger reroute ở `afterCommit`; `RerouteEvaluationService#evaluateCurrent` chạy trong `REQUIRES_NEW`. Vì vậy lỗi HERE/reroute không thể đánh dấu transaction ghi telemetry của simulator là rollback-only.
- `SimulationService` không gọi evaluator lần thứ hai trong cùng transaction tick; scheduler vẫn giữ trạng thái `FAILED` cho lỗi lifecycle/geometry thực sự và chỉ ghi loại exception trong log, không ghi chi tiết có thể chứa URL/provider credential.
- Backend compile và bộ test tập trung 27 test liên quan route/traffic/ETA/geometry đã chạy pass sau thay đổi; integration simulator cần PostgreSQL/Testcontainers khả dụng để xác minh runtime.
