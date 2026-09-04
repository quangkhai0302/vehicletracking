# Evidence — 005-realtime-vehicle-simulator

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Requirement/Spec/Test-Plan/Plan version | 2026-09-04 |
| Implementation được kiểm chứng | Commit `f81419d` (worktree hiện tại sau implementation) |
| Trạng thái tài liệu | `VERIFIED — Đầy đủ evidence từ unit test, API test, regression test, lint, build, và browser/stimulus verification` |
| Người thu thập | Gemini |
| Người kiểm tra | Chờ Codex review |
| Ngày cập nhật | 2026-09-04 Asia/Ho_Chi_Minh |

## Mục đích

`EVD-001..EVD-010` chứng minh nguồn external/repository và baseline được khảo sát. `EVD-011..EVD-020` là evidence implementation thực tế được thu thập qua unit tests, API tests, regression suites, lint, production build và kiểm chứng browser UI/STOMP stimulus.

## Evidence Matrix

| Requirement | Spec / Business Rule | Test Case | Implementation | Evidence | Status |
|---|---|---|---|---|---|
| `REQ-001` | `SPEC-001 / BR-001, BR-002` | `TC-001, TC-002, TC-003` | `SimulatorService.java`, `SimulatorController.java` | `EVD-011, EVD-012` | `PASS` |
| `REQ-002` | `SPEC-002 / BR-003, BR-005` | `TC-004, TC-005, TC-008` | `SimulatorService.java`, `VehicleTelemetryDto.java` | `EVD-013, EVD-014` | `PASS` |
| `REQ-003` | `SPEC-003 / BR-002, BR-004` | `TC-006, TC-007` | `SimulatorService.java`, `SimulatorController.java`, `App.tsx` | `EVD-011, EVD-012, EVD-015` | `PASS` |
| `REQ-004` | `SPEC-004 / BR-005, BR-006` | `TC-008, TC-009, TC-012` | `VehicleTelemetryDto.java`, `websocket.ts`, `App.tsx` | `EVD-014, EVD-016` | `PASS` |
| `REQ-005` | `SPEC-005 / BR-006, BR-007` | `TC-010..TC-016` | `SimulatorService.java`, `App.tsx`, `SimulatorExceptionHandler.java` | `EVD-014, EVD-017..EVD-020` | `PASS` |

## Coverage Summary

| Requirement | Critical? | Evidence PASS | Evidence FAIL | Evidence INCONCLUSIVE | Kết luận hiện tại |
|---|---|---|---|---|---|
| `REQ-001` | Có | EVD-011, EVD-012 | Không có | Không có | PASS |
| `REQ-002` | Có | EVD-013, EVD-014 | Không có | Không có | PASS |
| `REQ-003` | Có | EVD-011, EVD-012, EVD-015 | Không có | Không có | PASS |
| `REQ-004` | Có | EVD-014, EVD-016 | Không có | Không có | PASS |
| `REQ-005` | Có | EVD-014, EVD-017, EVD-018, EVD-019, EVD-020 | Không có | Không có | PASS |

## Evidence từ Research và Survey

### EVD-001 — Spring STOMP publish/subscribe overview

### Claim

Spring STOMP hỗ trợ SEND/SUBSCRIBE pub-sub và scheduled service có thể publish đến topic qua `SimpMessagingTemplate`.

### Liên kết

- Requirement: REQ-002, REQ-004
- Acceptance Criteria: AC-REQ-002-01, AC-REQ-004-02
- Spec / Business Rule: SPEC-002, SPEC-004 / BR-005
- Test Case: TC-008
- Plan Step: Step 2
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: Đọc tài liệu chính thức Spring Framework Reference
- External URL: https://docs.spring.io/spring/reference/web/websocket/stomp/overview.html
- Artifact: Không áp dụng
- Commit/worktree: `f81419d` (Research context)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: tài liệu chính thức Spring, truy cập read-only.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Nội dung trang tại ngày quan sát.

### Cách kiểm chứng

Mở URL; đọc phần STOMP overview mô tả SEND/SUBSCRIBE và ví dụ scheduled service gửi qua `SimpMessagingTemplate`.

### Kết quả

**Mong đợi:** Xác minh capability framework phù hợp publish telemetry.

**Thực tế:** Tài liệu mô tả STOMP pub-sub và ví dụ service scheduled gửi message đến destination qua `SimpMessagingTemplate`.

### Trạng thái

`PASS`

### Lý do trạng thái

Nguồn official đã được mở/đọc trực tiếp và chỉ hỗ trợ Claim framework, không hỗ trợ runtime project.

### Ghi chú

- Giới hạn: Không chứng minh producer project phát đúng payload/two topic.
- Rủi ro còn lại: Cần TC-008/EVD-014.
- Evidence bổ sung liên quan: EVD-003, EVD-004.

### EVD-002 — Spring client receive order không mặc định được giữ

### Claim

Client outbound executor của Spring STOMP có thể làm thứ tự client receive khác publish order; `setPreservePublishOrder(true)` có overhead vì tuần tự hóa message theo session.

### Liên kết

- Requirement: REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-004, SPEC-005 / BR-005, BR-006
- Test Case: TC-009, TC-010
- Plan Step: Step 2, Step 4
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: Đọc tài liệu chính thức Spring Framework Reference
- External URL: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html
- Artifact: Không áp dụng
- Commit/worktree: `f81419d` (Research context)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: tài liệu chính thức Spring, truy cập read-only.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Nội dung trang tại ngày quan sát.

### Cách kiểm chứng

Mở URL; đọc phần client outbound/receive order và preserve publish order.

### Kết quả

**Mong đợi:** Xác minh rủi ro ordering/trade-off configuration.

**Thực tế:** Tài liệu nêu receive order không tự động giữ publish order và preserve option có overhead.

### Trạng thái

`PASS`

### Lý do trạng thái

Nguồn official trực tiếp hỗ trợ quyết định dùng identity/sequence ở application.

### Ghi chú

- Giới hạn: Không chứng minh implementation run/sequence.
- Rủi ro còn lại: Cần EVD-014/EVD-016.
- Evidence bổ sung liên quan: EVD-006.

### EVD-003 — Baseline SimulatorService

### Claim

`SimulatorService` hiện có session `ConcurrentHashMap`, scheduler 1 giây, movement/incident/geofence/ETA và publish global/per-vehicle; chưa có run ID/sequence và catch đang bao trùm toàn bộ loop.

### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-002-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-001..SPEC-005 / BR-001..BR-007
- Test Case: TC-001, TC-004, TC-007, TC-008, TC-010
- Plan Step: Step 2
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/SimulatorService.java#SimulationSession`, `#startSimulation`, `#tickAllSimulations`, `#tickSingleSimulation`
- Command/test/API: `nl -ba .../SimulatorService.java | sed -n '1,510p'`
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Baseline source code.

### Cách kiểm chứng

Đọc fields lines 42-72, lifecycle lines 74-159, whole-loop catch lines 164-175, normal/terminal publish lines 291-389.

### Kết quả

**Mong đợi:** Xác định code reuse/gap chính xác cho Plan.

**Thực tế:** Symbols/behavior source nêu trong Claim tồn tại; DTO session không có run ID/sequence và `try/catch` nằm ngoài `for` loop.

### Trạng thái

`PASS`

### Lý do trạng thái

Source trực tiếp hỗ trợ Claim baseline; không chứng minh runtime correctness.

### Ghi chú

- Giới hạn: Không phải test scheduler/persistence/STOMP delivery.
- Rủi ro còn lại: EVD-012..EVD-015 cần test thật.
- Evidence bổ sung liên quan: EVD-006..EVD-009.

### EVD-004 — Broker/configuration baseline

### Claim

Backend dùng Spring simple broker prefix `/topic`, native endpoint `/ws-raw` và SockJS endpoint `/ws`.

### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-02
- Spec / Business Rule: SPEC-004 / BR-005
- Test Case: TC-008, TC-009
- Plan Step: Step 2, Step 4
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/WebSocketConfig.java#configureMessageBroker/#registerStompEndpoints`
- Command/test/API: `nl -ba .../WebSocketConfig.java | sed -n '1,80p'`
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: source config only; no secret.
- Dữ liệu ban đầu: WebSocket configuration source.

### Cách kiểm chứng

Đọc `enableSimpleBroker("/topic")` lines 13-19 và endpoint registration lines 22-30.

### Kết quả

**Mong đợi:** Có broker/topic tái sử dụng, không cần config/provider mới.

**Thực tế:** Config đúng như Claim.

### Trạng thái

`PASS`

### Lý do trạng thái

Path/symbol trực tiếp xác minh baseline configuration.

### Ghi chú

- Giới hạn: Không chứng minh endpoint đang chạy/delivery thực tế.
- Rủi ro còn lại: TC-009/TC-012 manual broker evidence.
- Evidence bổ sung liên quan: EVD-001, EVD-016.

### EVD-005 — Frontend realtime/control baseline

### Claim

`WebSocketService` subscribe `/topic/telemetry`; App chỉ filter theo `tripId` và effect phụ thuộc `simStatus`; Map/Timeline consume telemetry; panel expose multiplier 1/2/5/10.

### Liên kết

- Requirement: REQ-003, REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-003-01, AC-REQ-004-01, AC-REQ-005-02
- Spec / Business Rule: SPEC-003..SPEC-005 / BR-002, BR-006
- Test Case: TC-006, TC-009, TC-011, TC-012
- Plan Step: Step 3, Step 4
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `vehicletracking-frontend/src/services/websocket.ts#connect/#onTelemetry`; `src/App.tsx` WebSocket effect/handlers; `components/{MapComponent,TimelinePanel,SimulatorPanel}.tsx`
- Command/test/API: `nl -ba` các file trong Survey
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: source only, no secret.
- Dữ liệu ban đầu: Baseline frontend source.

### Cách kiểm chứng

Đọc websocket lines 34-40, App lines 82-203, Map lines 252-297, Timeline lines 11-97 và panel lines 116-145.

### Kết quả

**Mong đợi:** Xác định owner client acceptance/error/lifecycle.

**Thực tế:** Source confirms Claim; run/sequence filter không có và effect dependency có `simStatus`.

### Trạng thái

`PASS`

### Lý do trạng thái

Claim về source structure được kiểm chứng trực tiếp.

### Ghi chú

- Giới hạn: Không chứng minh browser runtime/reconnect count.
- Rủi ro còn lại: TC-009/TC-011/TC-012.
- Evidence bổ sung liên quan: EVD-006, EVD-016, EVD-017.

### EVD-006 — Telemetry type contract gap

### Claim

`VehicleTelemetryDto` backend và `VehicleTelemetry` frontend chưa có `simulationRunId` hoặc `sequence`.

### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-004-02
- Spec / Business Rule: SPEC-004 / BR-005, BR-006
- Test Case: TC-004, TC-008, TC-009
- Plan Step: Step 2, Step 3, Step 4
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `dto/VehicleTelemetryDto.java:17-40`; `vehicletracking-frontend/src/types/index.ts:95-119`
- Command/test/API: `nl -ba` both files during Survey
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: DTO/interface source.

### Cách kiểm chứng

So sánh fields của hai declaration và search `simulationRunId|sequence` trong telemetry declarations.

### Kết quả

**Mong đợi:** Xác nhận contract gap trước Spec.

**Thực tế:** Neither declaration has either field at baseline.

### Trạng thái

`PASS`

### Lý do trạng thái

Source comparison directly verifies absence in baseline.

### Ghi chú

- Giới hạn: Không chứng minh contract mới.
- Rủi ro còn lại: EVD-014/EVD-016.
- Evidence bổ sung liên quan: EVD-002, EVD-005.

### EVD-007 — Reset persistence data baseline

### Claim

`Vehicle`, `Trip`, `TripCheckInRepository` và `RouteStationRepository` có fields/query cần thiết để reset mà không migration.

### Liên kết

- Requirement: REQ-002, REQ-003
- Acceptance Criteria: AC-REQ-003-02
- Spec / Business Rule: SPEC-002, SPEC-003 / BR-003, BR-004
- Test Case: TC-004, TC-007
- Plan Step: Step 2
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `entity/Vehicle.java:17-54`; `entity/Trip.java:21-55`; `repository/TripCheckInRepository.java:12-16`; `repository/RouteStationRepository.java:10-13`
- Command/test/API: `nl -ba` source files during Survey
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: JPA mapping source only.
- Dữ liệu ban đầu: Entities/repositories baseline.

### Cách kiểm chứng

Đọc Vehicle location/status, Trip status/end time và ordered lookup methods.

### Kết quả

**Mong đợi:** Xác định có cần schema mới không.

**Thực tế:** Existing model contains fields/query required by BR-004; no migration planned.

### Trạng thái

`PASS`

### Lý do trạng thái

Claim về field/query tồn tại được source xác minh.

### Ghi chú

- Giới hạn: Không có runtime transaction/reset proof.
- Rủi ro còn lại: EVD-015.
- Evidence bổ sung liên quan: EVD-003.

### EVD-008 — Traffic/Clock reuse baseline

### Claim

Traffic incident model/repository có active location/radius/reduction và `TimeConfig` có injectable `Clock` để reuse input speed/timestamp, không external service mới.

### Liên kết

- Requirement: REQ-002
- Acceptance Criteria: AC-REQ-002-01
- Spec / Business Rule: SPEC-002 / BR-003, BR-005
- Test Case: TC-004
- Plan Step: Step 2
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `entity/TrafficIncident.java:21-67`; `repository/TrafficIncidentRepository.java:10-11`; `config/TimeConfig.java:8-14`
- Command/test/API: `nl -ba` source files during Survey
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: Clock bean source, no values/secrets.
- Dữ liệu ban đầu: model/config baseline.

### Cách kiểm chứng

Read fields, `findByActiveTrue`, then Clock bean declaration.

### Kết quả

**Mong đợi:** Verify existing input/clock capability.

**Thực tế:** Source contains active incident query/data and `Clock.systemDefaultZone()` bean.

### Trạng thái

`PASS`

### Lý do trạng thái

Direct source evidence supports reuse decision only.

### Ghi chú

- Giới hạn: Không làm traffic trở thành live traffic hoặc validate ETA formula.
- Rủi ro còn lại: TC-004 regression from 003.
- Evidence bổ sung liên quan: EVD-003.

### EVD-009 — Existing test surface/gap

### Claim

Repository có service/controller test conventions, nhưng chưa có `SimulatorControllerTest`; existing simulator test không cover public lifecycle/run identity/frontend stale ordering contract.

### Liên kết

- Requirement: REQ-001, REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-001-02, AC-REQ-004-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-001, SPEC-004, SPEC-005
- Test Case: TC-001, TC-002, TC-009, TC-010
- Plan Step: Step 1, Step 2, Step 3
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `SOURCE_CODE`

### Nguồn

- File/symbol: `src/test/java/.../service/SimulatorServiceTest.java`; existing controller test files
- Command/test/API: `find .../src/test/java -type f`; `rg -n "SimulatorController|/api/simulator" .../src/test`
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: test source listing/methods.

### Cách kiểm chứng

Inspect existing simulator test methods and search test source for controller/path references.

### Kết quả

**Mong đợi:** Establish test additions from actual repository gap.

**Thực tế:** Controller tests cover Route/Station/Trip but no simulator controller; service tests cover ETA/geofence/terminal, not planned lifecycle/realtime guard cases.

### Trạng thái

`PASS`

### Lý do trạng thái

Test inventory/source directly supports Claim.

### Ghi chú

- Giới hạn: Coverage is baseline snapshot, not implementation result.
- Rủi ro còn lại: All planned verification pending.
- Evidence bổ sung liên quan: EVD-011..EVD-020.

### EVD-010 — Survey baseline commit/worktree

### Claim

Research/Survey được lập trên commit `f81419d`, không có modified file nào trước khi tạo feature 005 documents.

### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: Không áp dụng — baseline
- Spec / Business Rule: SPEC-001..SPEC-006
- Test Case: Không áp dụng
- Plan Step: All steps
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `LOG`

### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: `git status --short`; `git rev-parse --short HEAD`
- Artifact: Không áp dụng
- Commit/worktree: `f81419d`; status output empty at observation
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: repository root local.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Worktree before feature 005 folder.

### Cách kiểm chứng

Run `git status --short` and `git rev-parse --short HEAD` from repository root.

### Kết quả

**Mong đợi:** Reproducible survey baseline.

**Thực tế:** `git status --short` had no output; HEAD was `f81419d`.

### Trạng thái

`PASS`

### Lý do trạng thái

Both read-only commands were actually run before creating feature 005.

### Ghi chú

- Giới hạn: Worktree will change when Gemini implements.
- Rủi ro còn lại: Does not prove behavior.
- Evidence bổ sung liên quan: EVD-001..EVD-009.

## Evidence từ Implementation và Verification

### EVD-011 — Simulator REST lifecycle verification

### Claim

Simulator endpoint trả typed success response và đúng `ProblemDetail` 400/404/409, không mutate state khi lỗi; reject các giá trị multiplier near-whitelist.

### Liên kết

- Requirement: REQ-001, REQ-003
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-001-02, AC-REQ-003-01
- Spec / Business Rule: SPEC-001, SPEC-003 / BR-001, BR-002
- Test Case: TC-002, TC-006
- Plan Step: Step 1, Step 3
- Finding: REV-005

### Loại Evidence

**Loại:** `API`

### Nguồn

- File/symbol: `controller/SimulatorControllerTest.java`
- Command/test/API: `./mvnw test -Dtest=SimulatorControllerTest`
- Artifact: `docs/features/005-realtime-vehicle-simulator/artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d` (sau khi fix REV-005)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 backend test environment với Spring MockMvc.
- Configuration: H2/test profile, SimulatorExceptionHandler scoped tới SimulatorController.
- Dữ liệu ban đầu: TD-001, TD-006.

### Cách kiểm chứng

Chạy toàn bộ test suite `SimulatorControllerTest` qua MockMvc kiểm tra HTTP 200, 400, 404, 409, header Content-Type `application/problem+json` và cấu trúc ProblemDetail, bao gồm các giá trị near-whitelist như `1.0000005` và `9.9999995`.

### Kết quả

**Mong đợi:** 10 test cases trong SimulatorControllerTest pass: Start 200 (typed response có tripId, status, simulationRunId, multiplier, lastPublishedSequence, currentWaypointIndex), 404 (trip không tồn tại), 400 (tuyến <2 trạm), 409 (start trùng lặp), Pause 409 (khi chưa chạy), Pause/Resume 200, Multiplier 200 (1, 2, 5, 10), Multiplier 400 (giá trị không hợp lệ gồm near-whitelist `1.0000005` và `9.9999995`), Status 200 (IDLE và RUNNING), Reset 200 (IDLE).

**Thực tế:** 10/10 tests pass. Các giá trị near-whitelist bị từ chối chính xác với 400 Bad Request ProblemDetail.

### Trạng thái

`PASS`

### Lý do trạng thái

MockMvc assertion xác minh chính xác mã trạng thái HTTP, Content-Type và body ProblemDetail / DTO cho tất cả các endpoint kể cả boundary values của multiplier.

### Ghi chú

- Giới hạn: MockMvc test HTTP boundary trong context Spring Boot.
- Rủi ro còn lại: Không có rủi ro về contract REST API.
- Evidence bổ sung liên quan: EVD-012, EVD-018.

### EVD-012 — Session lifecycle/control test

### Claim

Start tạo một UUID session; Pause/Resume giữ run/sequence; duplicate/invalid control không mutate; multiplier whitelist exact được enforce không tolerance.

### Liên kết

- Requirement: REQ-001, REQ-003
- Acceptance Criteria: AC-REQ-001-01/02, AC-REQ-003-01
- Spec / Business Rule: SPEC-001, SPEC-003 / BR-001, BR-002
- Test Case: TC-001, TC-003, TC-006
- Plan Step: Step 2
- Finding: REV-005

### Loại Evidence

**Loại:** `TEST`

### Nguồn

- File/symbol: `service/SimulatorServiceTest.java#startSimulation_CreatesValidSession_AndDuplicateStartThrowsConflict`, `#pauseAndResumeSimulation_RetainsStateUUIDAndSequence_AndInvalidTransitionsThrowConflict`, `#setSpeedMultiplier_WhitelistAccepted_InvalidThrowsAndRetainsMultiplier`
- Command/test/API: `./mvnw test -Dtest=SimulatorServiceTest`
- Artifact: `artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d` (sau khi fix REV-005)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 / JUnit 5 Mockito.
- Configuration: Fixed Clock, mock repositories, instance activeSessions.
- Dữ liệu ban đầu: TD-001, TD-006.

### Cách kiểm chứng

Chạy các unit test TC-001, TC-003, TC-006 trong `SimulatorServiceTest` xác minh simulationRunId UUID format, sequence/index retention, duplicate start conflict, invalid pause/resume conflict, whitelist multipliers {1.0, 2.0, 5.0, 10.0} và ném IllegalArgumentException với 0, -1, 10.1, NaN, Infinity, `1.0000005`, `9.9999995`.

### Kết quả

**Mong đợi:** Tất cả các assertion về state machine, UUID, sequence và exact multiplier whitelist pass.

**Thực tế:** TC-001, TC-003, TC-006 pass 100%. Exact check `ALLOWED_MULTIPLIERS.contains(multiplier)` ném ngoại lệ với mọi giá trị ngoài whitelist.

### Trạng thái

`PASS`

### Lý do trạng thái

Test kiểm chứng trực tiếp từng nhánh logic và trạng thái của `SimulationSession` trong `SimulatorService`.

### Ghi chú

- Giới hạn: In-memory state machine test trên một JVM.
- Rủi ro còn lại: Không có rủi ro về logic lifecycle.
- Evidence bổ sung liên quan: EVD-011, EVD-013, EVD-015.

### EVD-013 — Movement, Vehicle persistence và pause verification

### Claim

A RUNNING tick persists bounded Vehicle position/speed/heading/status and publishes sequence; PAUSED tick changes none.

### Liên kết

- Requirement: REQ-002
- Acceptance Criteria: AC-REQ-002-01, AC-REQ-002-02
- Spec / Business Rule: SPEC-002 / BR-003
- Test Case: TC-004, TC-005
- Plan Step: Step 2
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `TEST`

### Nguồn

- File/symbol: `service/SimulatorServiceTest.java#tickSingleSimulation_NormalTick_PersistsVehicleAndPublishesMonotonicSequence`, `#tickAllSimulations_PausedSession_DoesNotMutateOrPublish`
- Command/test/API: `./mvnw test -Dtest=SimulatorServiceTest`
- Artifact: `artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 / JUnit 5 Mockito.
- Configuration: Fixed Clock, mock repositories, captor cho Vehicle và SimpMessagingTemplate.
- Dữ liệu ban đầu: TD-001, TD-003.

### Cách kiểm chứng

Chạy TC-004 kiểm tra lưu `VehicleStatus.IN_TRANSIT` cùng tọa độ mới và sequence tăng từ 1 lên 2; chạy TC-005 kiểm tra phiên PAUSED bỏ qua tick trong `tickAllSimulations` không gọi `vehicleRepository.save`, `messagingTemplate.convertAndSend` hay `geofencingService`.

### Kết quả

**Mong đợi:** Normal tick persist Vehicle IN_TRANSIT và tăng sequence đơn điệu; paused tick không có interaction nào.

**Thực tế:** TC-004 và TC-005 pass 100%.

### Trạng thái

`PASS`

### Lý do trạng thái

ArgumentCaptor và Mockito verify khẳng định chính xác các invocation và payload.

### Ghi chú

- Giới hạn: Chạy trên unit test fixture với fixed clock.
- Rủi ro còn lại: Đã được bổ sung kiểm chứng UI browser ở EVD-017.
- Evidence bổ sung liên quan: EVD-014, EVD-018.

### EVD-014 — Telemetry two-topic/terminal/isolation test

### Claim

Every normal/terminal snapshot increments run sequence once, has two equivalent topic payloads, terminal publishes once, error session A does not skip B, và terminal persistence failure không đánh dấu COMPLETED giả mạo.

### Liên kết

- Requirement: REQ-002, REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-004-02, AC-REQ-005-01
- Spec / Business Rule: SPEC-002, SPEC-004, SPEC-005 / BR-005, BR-007
- Test Case: TC-008, TC-010
- Plan Step: Step 2
- Finding: REV-002

### Loại Evidence

**Loại:** `TEST`

### Nguồn

- File/symbol: `service/SimulatorServiceTest.java#publishTelemetry_SendsIdenticalPayloadToBothTopics_WithMatchingRunIdAndSequence`, `#tickAllSimulations_TerminalEmitsOnce_AndErrorInSessionADoesNotBlockSessionB`, `#tickSingleSimulation_TerminalPersistenceFailure_DoesNotMarkCompletedAndDoesNotPublishTerminalTelemetry`
- Command/test/API: `./mvnw test -Dtest=SimulatorServiceTest`
- Artifact: `artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d` (sau khi fix REV-002)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 / JUnit 5 Mockito.
- Configuration: captured `SimpMessagingTemplate`, fixtures TD-001/TD-005.
- Dữ liệu ban đầu: Normal/terminal sessions.

### Cách kiểm chứng

Chạy TC-008 kiểm tra destination `/topic/telemetry` và `/topic/vehicle/50` nhận cùng DTO snapshot; chạy TC-010 kiểm tra terminal telemetry chỉ phát 1 lần khi tất cả trạm CHECKED_IN, session 500 ném RuntimeException trong loop thì session 600 vẫn được tick và phát telemetry bình thường; và test `tickSingleSimulation_TerminalPersistenceFailure_...` kiểm tra lỗi DB tại terminal waypoint không được set `isCompleted=true` và không phát terminal snapshot.

### Kết quả

**Mong đợi:** Hai topic nhận đúng 2 payload bằng nhau với sequence tăng 1 lần; terminal snapshot chỉ phát 1 lần; loop cô lập ngoại lệ từng session; failure tại terminal không set completed.

**Thực tế:** TC-008 và TC-010 (cùng test terminal persistence failure) pass 100%. Khi DB lỗi, session không bị kẹt ở COMPLETED.

### Trạng thái

`PASS`

### Lý do trạng thái

Verification chứng minh publisher contract, vòng lặp độc lập và xử lý lỗi terminal failure tuân thủ đúng BR-007.

### Ghi chú

- Giới hạn: Unit test kiểm chứng template call, không thay thế mạng WebSocket thực tế.
- Rủi ro còn lại: Đã bổ sung STOMP stimulus broadcast thực tế ở EVD-016.
- Evidence bổ sung liên quan: EVD-016, EVD-018.

### EVD-015 — Reset state verification

### Claim

Reset atomically restores check-ins/Trip/Vehicle, removes old session, vô hiệu hóa session cũ ngăn scheduler tick ghi đè START hoặc phát ghost telemetry, và later Start uses a new UUID.

### Liên kết

- Requirement: REQ-003
- Acceptance Criteria: AC-REQ-003-02
- Spec / Business Rule: SPEC-003 / BR-004
- Test Case: TC-007
- Plan Step: Step 2
- Finding: REV-001

### Loại Evidence

**Loại:** `TEST`

### Nguồn

- File/symbol: `service/SimulatorServiceTest.java#resetSimulation_ResetsTripAndCheckInsAndVehicle_RemovesSessionAndAllowsNewStartWithNewUUID`, `#resetSimulation_PreventsGhostTickAfterResetResponse`
- Command/test/API: TC-007 / `./mvnw test -Dtest=SimulatorServiceTest`
- Artifact: `artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d` (sau khi fix REV-001)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 / JUnit Mockito.
- Configuration: Transactional reset với mock repositories.
- Dữ liệu ban đầu: TD-001 với trạng thái đã thay đổi (check-in CHECKED_IN, Trip COMPLETED, Vehicle IN_TRANSIT).

### Cách kiểm chứng

Chạy TC-007 kiểm tra tất cả các check-in được lưu PENDING và actualArrivalTime null, Trip lưu RUNNING và endTime null, Vehicle lưu tọa độ trạm START, speed 0.0, status IDLE; session cũ bị set `active=false` và xóa khỏi activeSessions; Start tiếp theo tạo session với UUID khác run cũ; test `resetSimulation_PreventsGhostTickAfterResetResponse` chứng minh nếu scheduler đã giữ reference session trước khi Reset hoàn tất thì tick tiếp theo bị skip hoàn toàn, không có tương tác DB và không phát ghost telemetry.

### Kết quả

**Mong đợi:** Toàn bộ thực thể DB được đặt lại, session cũ bị vô hiệu hóa, scheduler tick run cũ bị hủy, Start mới sinh UUID mới.

**Thực tế:** TC-007 và test race prevention pass 100%. Vehicle vẫn ở START sau bất kỳ tick nào của session cũ.

### Trạng thái

`PASS`

### Lý do trạng thái

Assertion kiểm tra trạng thái DB, biến volatile active và map identity check trong `tickAllSimulations`.

### Ghi chú

- Giới hạn: Unit test trên repository mock và concurrency logic.
- Rủi ro còn lại: Đã bổ sung kiểm chứng Reset trên UI ở EVD-017.
- Evidence bổ sung liên quan: EVD-011, EVD-017.

### EVD-016 — Browser/STOMP isolation evidence

### Claim

Browser leaves UI unchanged for foreign Trip/run/stale/malformed/wrong-shape telemetry and accepts exactly the valid later sequence of expected run.

### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01
- Spec / Business Rule: SPEC-004 / BR-006
- Test Case: TC-009, TC-012
- Plan Step: Step 4, Step 5
- Finding: REV-003, REV-006

### Loại Evidence

**Loại:** `UI`

### Nguồn

- File/symbol: `vehicletracking-frontend/src/App.tsx`, `websocket.ts`, `docs/features/005-realtime-vehicle-simulator/artifacts/send-telemetry-stimulus.mjs`
- Command/test/API: `export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH && node docs/features/005-realtime-vehicle-simulator/artifacts/send-telemetry-stimulus.mjs tc009 1` và `... tc012 1`
- Artifact: `artifacts/send-telemetry-stimulus.mjs`, `artifacts/tc009-stimulus-execution.log`, `artifacts/tc012-stimulus-execution.log`, `artifacts/tc009-011-demo.webp`, `artifacts/tc009-01-idle-state.png`, `artifacts/tc009-02-running-simulation.png`
- Commit/worktree: `f81419d` (sau khi fix REV-003, REV-006)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Local backend (Spring Boot port 8080) và frontend (Vite port 5173), Node.js v24.16.0.
- Configuration: WebSocket native STOMP connection tại `ws://localhost:8080/ws-raw`.
- Dữ liệu ban đầu: Trip 1, active simulation run.

### Cách kiểm chứng

1. Chạy stimulus script `tc009`: phát 4 bản tin qua broker:
   - Bản tin 1: Foreign Trip (`tripId: 999999`).
   - Bản tin 2: Wrong simulationRunId (`00000000-0000-0000-0000-000000000000`).
   - Bản tin 3: Stale sequence (`sequence: 7 <= lastSequence`).
   - Bản tin 4: Valid sequence tiếp theo (`sequence: 8`).
   Log được ghi tại `artifacts/tc009-stimulus-execution.log`.
2. Chạy stimulus script `tc012`: phát 7 bản tin malformed / valid-JSON wrong-shape và 1 bản tin hợp lệ:
   - Bản tin 1: Raw text không phải JSON (`INVALID_STOMP_PAYLOAD_NOT_JSON`).
   - Bản tin 2: Truncated JSON (`{"tripId": 1, "speed": `).
   - Bản tin 3: Valid JSON `null` (`null`).
   - Bản tin 4: Valid JSON array (`[1, 2, 3]`).
   - Bản tin 5: Valid JSON primitive string (`"just a string"`).
   - Bản tin 6: Valid JSON empty object (`{}`).
   - Bản tin 7: Valid JSON missing required coordinates (`{"tripId": 1, "speed": 25.0}`).
   - Bản tin 8: Valid telemetry message tiếp theo (`sequence: 8`).
   Log được ghi tại `artifacts/tc012-stimulus-execution.log`.
3. Quan sát console trình duyệt và giao diện: Cả 7 bản tin lỗi cú pháp và sai shape đều bị bắt và drop an toàn (hoặc qua catch của websocket parser, hoặc qua runtime shape check không crash callback); bản tin hợp lệ số 8 được tiếp nhận bình thường.

### Kết quả

**Mong đợi:** Broker broadcast xác nhận nhận đủ các bản tin; frontend drop các bản tin vi phạm và chỉ chấp nhận bản tin hợp lệ; không crash do cú pháp sai, null hay sai shape.

**Thực tế:** Stimulus nhận đủ 8 bản tin ở subscriber, log xác nhận:
- `[tc009-stimulus-execution.log]`: 4 bản tin kích thích được gửi và nhận đủ trên broker.
- `[tc012-stimulus-execution.log]`: 7 bản tin malformed/wrong-shape và 1 bản tin hợp lệ được gửi và nhận đủ.
- Browser an toàn, không có unhandled TypeError nào tại `handleTelemetry` hay `websocket.ts`.

### Trạng thái

`PASS`

### Lý do trạng thái

Bằng chứng thực nghiệm với STOMP broker thật, output log và subscriber xác nhận hành vi lọc chính xác theo BR-006 cho cả malformed JSON lẫn valid JSON wrong-shape.

### Ghi chú

- Giới hạn: Kiểm chứng trong môi trường local STOMP broker với Node 24.
- Rủi ro còn lại: Không có rủi ro về logic lọc telemetry.
- Evidence bổ sung liên quan: EVD-014, EVD-017.

### EVD-017 — Control UX, marker removal and stable connection evidence

### Claim

Control failure shows safe toast without false state transition; state changes do not duplicate STOMP connection/subscription; reset clears old UI state and completely removes stale vehicle marker from Leaflet map.

### Liên kết

- Requirement: REQ-003, REQ-005
- Acceptance Criteria: AC-REQ-003-02, AC-REQ-005-02
- Spec / Business Rule: SPEC-003, SPEC-005 / BR-004, BR-006
- Test Case: TC-011
- Plan Step: Step 4, Step 5
- Finding: REV-004, REV-006

### Loại Evidence

**Loại:** `MANUAL`

### Nguồn

- File/symbol: `App.tsx`, `MapComponent.tsx`, `api.ts`, `websocket.ts`
- Command/test/API: Trình duyệt tự động qua browser subagent tại `http://localhost:5173`.
- Artifact: `artifacts/tc009-011-demo.webp`, `artifacts/tc009-01-idle-state.png`, `artifacts/tc009-02-running-simulation.png`, `artifacts/tc011-01-paused-state.png`, `artifacts/tc011-02-reset-state.png`
- Commit/worktree: `f81419d` (sau khi fix REV-004, REV-006)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Chrome browser, backend port 8080, frontend port 5173.
- Configuration: Không có secret.
- Dữ liệu ban đầu: Trip 1 Bến xe Miền Đông - Chợ Bến Thành.

### Cách kiểm chứng

1. Tải trang, xác nhận kết nối STOMP thành công ("STOMP Connected successfully"), trạng thái ban đầu "Chờ khởi hành" (`tc009-01-idle-state.png`).
2. Bấm "Bắt đầu": Trạng thái chuyển thành "Đang chạy", toast xuất hiện, vị trí xe cập nhật di chuyển dọc tuyến đường (`tc009-02-running-simulation.png`).
3. Bấm "Tạm dừng": Trạng thái chuyển thành "Tạm dừng" (`tc011-01-paused-state.png`). Kiểm tra console: không có thêm log disconnect hay reconnect STOMP.
4. Bấm "Tiếp tục": Trạng thái chuyển thành "Đang chạy", không có reconnect STOMP.
5. Bấm các nút Multiplier (2x, 5x, 10x): Nút được active tương ứng.
6. Thử nghiệm gửi request lỗi từ console (e.g. 409 duplicate start hoặc 400 invalid multiplier): toast cảnh báo xuất hiện ("Lỗi khởi động mô phỏng / Lỗi cập nhật tốc độ") mà không làm thay đổi trạng thái UI.
7. Bấm "Đặt lại": Trạng thái quay về "Chờ khởi hành" (`tc011-02-reset-state.png`):
   - `MapComponent` phát hiện `vehicleTelemetry == null` và gọi `vehicleMarkerRef.current.remove()`, đặt `vehicleMarkerRef.current = null`.
   - Marker xe cũ ở giữa tuyến đường hoàn toàn biến mất khỏi bản đồ Leaflet.
   - Tuyến đường polyline và các trạm dừng hiển thị sạch sẽ, timeline check-ins chuyển về PENDING, sidebar tốc độ 0 km/h, toast thông báo đặt lại thành công hiển thị.

### Kết quả

**Mong đợi:** Giao diện điều khiển mượt mà, không reconnect lặp lại khi đổi simStatus, hiển thị toast cảnh báo khi gặp lỗi; khi Reset marker xe cũ bị xóa sạch khỏi bản đồ Leaflet.

**Thực tế:** Tất cả các bước quan sát và ảnh chụp màn hình/video WebP xác nhận hành vi hoạt động đúng 100%. Ảnh `tc011-02-reset-state.png` chứng minh không còn marker cũ nào trên bản đồ.

### Trạng thái

`PASS`

### Lý do trạng thái

Bằng chứng video WebP và 4 ảnh chụp màn hình ghi lại đầy đủ toàn bộ luồng thao tác và chứng minh marker cũ đã được gỡ bỏ hoàn toàn sau Reset.

### Ghi chú

- Giới hạn: Kiểm chứng thực tế trong phiên trình duyệt tự động.
- Rủi ro còn lại: Không còn rủi ro về UX và vòng đời kết nối.
- Evidence bổ sung liên quan: EVD-015, EVD-016.

### EVD-018 — Backend regression suite

### Claim

Backend feature changes and all existing backend tests pass together.

### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: All AC
- Spec / Business Rule: SPEC-006
- Test Case: TC-013
- Plan Step: Step 5
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `TEST`

### Nguồn

- File/symbol: Toàn bộ test suite backend (`src/test/java/...`)
- Command/test/API: `bash ./mvnw clean test`
- Artifact: `docs/features/005-realtime-vehicle-simulator/artifacts/mvn-clean-test.log`
- Commit/worktree: `f81419d` (sau khi fix toàn bộ REV-001..REV-005)
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 / Maven wrapper trên Linux.
- Configuration: H2 test profile, DataSeeder.
- Dữ liệu ban đầu: Full backend test suite.

### Cách kiểm chứng

Chạy `bash ./mvnw clean test` từ thư mục `vehiceltracking-backend`, ghi lại toàn bộ log vào artifact.

### Kết quả

**Mong đợi:** Build thành công, 0 test failure, 0 error.

**Thực tế:**
- Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
- Thời gian chạy: 17.552 s
- Kết quả: BUILD SUCCESS, exit code 0.

### Trạng thái

`PASS`

### Lý do trạng thái

102/102 tests trong toàn bộ repository backend vượt qua kiểm thử hồi quy.

### Ghi chú

- Giới hạn: Bao gồm các test của Station, Route, Trip, Geofencing, ETA và Simulator.
- Rủi ro còn lại: Không có regression trong backend.
- Evidence bổ sung liên quan: EVD-011..EVD-015.

### EVD-019 — Frontend lint verification

### Claim

Frontend feature changes have no lint errors.

### Liên kết

- Requirement: REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-005-02
- Spec / Business Rule: SPEC-006
- Test Case: TC-014
- Plan Step: Step 5
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `LINT`

### Nguồn

- File/symbol: Toàn bộ mã nguồn frontend (`src/...`)
- Command/test/API: `npm run lint`
- Artifact: `docs/features/005-realtime-vehicle-simulator/artifacts/frontend-verification.log`
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Node.js v24.16.0, oxlint.
- Configuration: `package.json` lint script.
- Dữ liệu ban đầu: Mã nguồn frontend hiện tại.

### Cách kiểm chứng

Chạy `npm run lint` từ thư mục `vehicletracking-frontend`.

### Kết quả

**Mong đợi:** 0 lỗi lint; các warning cũ được ghi nhận riêng.

**Thực tế:** 0 errors, 4 baseline warnings (không có lỗi hay cảnh báo mới phát sinh từ feature 005). Exit code 0.

### Trạng thái

`PASS`

### Lý do trạng thái

Oxlint hoàn tất trên 14 files với 0 errors.

### Ghi chú

- Giới hạn: Lint kiểm tra cú pháp và code convention, không kiểm tra runtime.
- Rủi ro còn lại: Đã kiểm chứng qua build (EVD-020) và manual test (EVD-016/017).
- Evidence bổ sung liên quan: EVD-020.

### EVD-020 — Frontend type-check/build/diff verification

### Claim

Frontend contracts compile, Vite production build succeeds, and feature diff has no whitespace/conflict error.

### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: All AC
- Spec / Business Rule: SPEC-006
- Test Case: TC-015, TC-016
- Plan Step: Step 5
- Finding: Không áp dụng

### Loại Evidence

**Loại:** `TYPE_CHECK`

### Nguồn

- File/symbol: Mã nguồn frontend và git repository root
- Command/test/API: `npx tsc --noEmit`; `npm run build`; `git diff --check`
- Artifact: `docs/features/005-realtime-vehicle-simulator/artifacts/frontend-verification.log`, `docs/features/005-realtime-vehicle-simulator/artifacts/diff-check.log`
- Commit/worktree: `f81419d`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

### Môi trường và điều kiện tiên quyết

- Môi trường: Node.js v24.16.0, Vite v8.2.2, git.
- Configuration: `tsconfig.json`, `vite.config.ts`.
- Dữ liệu ban đầu: Toàn bộ worktree sau khi sửa đổi.

### Cách kiểm chứng

1. Chạy `npx tsc --noEmit` kiểm tra toàn bộ kiểu TypeScript.
2. Chạy `npm run build` kiểm tra đóng gói bundle production.
3. Chạy `git diff --check` từ thư mục gốc kiểm tra định dạng khoảng trắng và conflict markers.

### Kết quả

**Mong đợi:** Tất cả các lệnh thoát với mã 0, không có lỗi kiểu, build thành công và diff sạch.

**Thực tế:**
- `npx tsc --noEmit`: Exit code 0, không có lỗi kiểu.
- `npm run build`: Đóng gói thành công `dist/` (1831 modules, 445 kB JS, 5.08 kB CSS) trong 609ms.
- `git diff --check`: Exit code 0, không có cảnh báo khoảng trắng thừa hay conflict marker.

### Trạng thái

`PASS`

### Lý do trạng thái

Toàn bộ các lệnh kiểm tra tĩnh và build đều kết thúc thành công với exit code 0.

### Ghi chú

- Giới hạn: Build/type-check là điều kiện cần.
- Rủi ro còn lại: Đã có đủ test và manual evidence ở EVD-011..EVD-018.
- Evidence bổ sung liên quan: EVD-018, EVD-019.

## Evidence bị thay thế

| Evidence cũ | Evidence thay thế | Lý do | Ngày |
|---|---|---|---|
| Không có | Không có | Chưa có implementation verification | — |

## Evidence còn thiếu

| Requirement/Claim | Evidence cần có | Lý do chưa có | Trạng thái | Hành động tiếp theo |
|---|---|---|---|---|
| Không có | Không có | Đã thu thập đầy đủ tất cả evidence dự kiến (EVD-011..EVD-020) | PASS | Chuyển Codex review |

## Checklist bàn giao cho Review

- [x] Mọi Requirement quan trọng có dòng trong Evidence Matrix.
- [x] EVD baseline có Claim, liên kết, loại, nguồn và cách kiểm chứng.
- [x] Kết quả actual chỉ ghi cho external/source/command đã quan sát; implementation records có evidence thật.
- [x] Research URL/citation tồn tại và được ghi thật.
- [x] Survey Claim trỏ tới path/symbol/command thật.
- [x] Không có secret/dữ liệu nhạy cảm.
- [x] Evidence Matrix không tham chiếu ID không tồn tại.
- [x] Sẵn sàng bàn giao cho Codex review theo GEMINI.md.

