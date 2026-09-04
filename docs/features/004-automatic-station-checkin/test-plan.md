# Test-Plan — 004-automatic-station-checkin

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `004-automatic-station-checkin` |
| Requirement/Spec version | 2026-09-04 |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người viết | Codex |
| Người duyệt | Người dùng |
| Ngày cập nhật | 2026-09-04 |

## Mục tiêu kiểm thử

Chứng minh backend chỉ ghi nhận đúng trạm khi xe vào geofence, giữ thứ tự và idempotency, không bỏ sót START/waypoint do simulator nhảy bước, phát event đúng payload và để frontend cô lập Trip. Test cũng phải phát hiện input lỗi làm check-in giả, completion sai hoặc làm dừng scheduler.

## Phạm vi kiểm thử

### Trong phạm vi

- Unit test `GeofencingService` với fixed `Clock` và Mockito.
- Unit/service test `SimulatorService#tickSingleSimulation` cho START và waypoint trung gian.
- Test event payload, save-before-publish và final completion.
- Manual realtime/UI verification bằng frontend hiện có.
- Backend full regression; frontend lint/type-check/build và diff hygiene.

### Ngoài phạm vi

- `npm test`/UI automation vì frontend chưa có test script/framework (`package.json`).
- GPS hardware, external routing/traffic, distributed multi-instance concurrency và reconnect replay.
- Performance load test cho hàng nghìn xe vì chưa có yêu cầu throughput.

## Chiến lược kiểm thử

### Unit Test

- Đối tượng: `GeofencingService`, `GeoUtil` và logic tick của `SimulatorService`.
- Mục tiêu: business rule radius, order, no-op, idempotency tuần tự, fixed time và completion.
- Framework hiện có: JUnit 5/Mockito theo test backend hiện tại.

### Integration Test

- Boundary: service/repository với H2 nếu môi trường Maven có dependency; STOMP broker/UI manual cho event.
- Dependency thật/mocked: Unit mock repository/messaging; test tích hợp dùng H2 fixture không chứa secret.
- Mục tiêu: persistence status/actual time và JSON event nếu có thể.

### API Test

- Endpoint: `POST /api/simulator/start/{tripId}` chỉ regression; không có endpoint check-in mới.
- Mục tiêu: simulator vẫn khởi động và side effect không phá response hiện có.

### UI Test

- Flow/component: `WebSocketService` → `App` toast và `TimelinePanel`.
- Mục tiêu: event matching hiển thị, foreign event bị bỏ qua.
- Công cụ: manual browser verification; frontend chưa có UI test framework.

### Realtime/Event Test

- Topic/event: `/topic/checkins`, payload `CheckInEventDto`.
- Mục tiêu: event chỉ phát sau transition/save, field đúng và không duplicate producer theo state.
- Delivery/reconnect: chỉ kiểm tra live subscription; replay ngoài phạm vi.

### End-to-End/Acceptance Test

- Luồng: khởi động backend/frontend, chọn Trip, start simulator, quan sát auto check-in trên timeline/toast và cuối chuyến.
- Môi trường: local dev với H2 profile hiện có và WebSocket native.

## Môi trường và điều kiện tiên quyết

| Thành phần | Yêu cầu | Cách chuẩn bị |
|---|---|---|
| Runtime backend | Java 26 theo `pom.xml` | `java --version`; chạy Maven wrapper bằng `bash ./mvnw` nếu permission bit không cho `./mvnw` |
| Database | H2 in-memory profile `dev` | Dùng config `application.yaml`; không ghi credential vào artifact |
| Frontend/Browser | Node 24.16.0 hoặc runtime tương thích Vite 8; browser hỗ trợ WebSocket | `npm run lint`, type-check và Vite build; mở dev server |
| Backend service | Port 8080 | Start application theo README/IDE; không commit log secret |
| Frontend service | Vite port mặc định | `npm run dev` |

## Test Data

| ID | Dữ liệu | Mục đích | Cách tạo/dọn dẹp |
|---|---|---|---|
| TD-001 | Trip 100: START radius 100m, STOP radius 100m, END radius 100m; start PENDING | Happy path | Fixture trong test, không dùng production |
| TD-002 | Tọa độ cách tâm > radius và đúng radius | Boundary/outside | Fixture tính từ `GeoUtil` |
| TD-003 | A stopOrder 1 PENDING, B stopOrder 2 PENDING | Không skip | Fixture service test |
| TD-004 | Tất cả TripCheckIn CHECKED_IN | No pending/final state | Fixture service test |
| TD-005 | NaN, positive infinity, lat 91, lng 181 | Invalid input | Fixture unit test |
| TD-006 | Waypoint index 0 là START, index 1..n có station waypoint | Simulator traversal | Fixture `SimulationSession` |
| TD-007 | Foreign `CheckInEvent` với `tripId` khác currentTrip | UI isolation | Manual STOMP payload đã redact |

Không dùng API key, token, password hoặc dữ liệu production trong test data/artifact.

## Test Cases

### TC-001 — Check-in khi trong geofence và ghi timestamp

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001, AC-REQ-001-01, SPEC-001, BR-001, BR-002, BR-005`

**Mục tiêu:** Chứng minh transition đúng record, lưu `actualArrivalTime` từ fixed Clock.

**Precondition:** Next `TripCheckIn` là PENDING; Station có radius hợp lệ; repository query trả record.

**Input/Test data:** `TD-001`, vị trí đúng tâm station.

**Steps:**

1. Stub fixed `Clock` và repository query.
2. Gọi `checkAndProcessAutoCheckIn(tripId, station.latitude, station.longitude)`.
3. Capture record save.

**Expected result:**

- Trả về record; status `CHECKED_IN`; `actualArrivalTime` bằng fixed time.
- Save đúng một lần; event có station/trip/stopOrder/time tương ứng.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Evidence dự kiến:** `TEST`, JUnit output và source assertion.

### TC-002 — Boundary và outside radius

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001, REQ-005, AC-REQ-001-02, SPEC-001, BR-002`

**Mục tiêu:** Phân biệt `<= radius` với `> radius`.

**Precondition:** Next check-in PENDING, radius biết trước.

**Input/Test data:** `TD-002` với một điểm đúng biên và một điểm ngoài biên.

**Steps:**

1. Gọi service với điểm boundary.
2. Reset fixture/query cho điểm outside.
3. Verify state, save và messaging.

**Expected result:** Boundary check-in PASS; outside giữ PENDING, save/event bằng 0.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Evidence dự kiến:** `TEST`.

### TC-003 — Không bỏ qua trạm sau

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-002, AC-REQ-002-01, SPEC-002, BR-001`

**Mục tiêu:** Query only-next semantics.

**Precondition:** A order 1 PENDING, B order 2 PENDING; query trả A.

**Input/Test data:** `TD-003`, tọa độ B.

**Steps:** Gọi geofence tại B trước A.

**Expected result:** A vẫn PENDING, B không save/check-in, không event.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Evidence dự kiến:** `TEST`.

### TC-004 — Gọi lặp không tạo duplicate

**Loại:** `Unit`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-002, AC-REQ-002-02, SPEC-002, BR-003`

**Mục tiêu:** Verify save/event chỉ xảy ra cho transition PENDING đầu tiên.

**Precondition:** Lần đầu query trả A PENDING; lần sau không trả A PENDING hoặc trả record kế tiếp ở vị trí khác.

**Input/Test data:** `TD-001`, cùng tọa độ A gọi hai lần.

**Steps:** Gọi method hai lần với cùng trip và tọa độ.

**Expected result:** A chỉ save và phát event một lần; không có event lặp cho A; không gọi completion lặp.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Evidence dự kiến:** `TEST` với invocation count.

### TC-005 — START được kiểm tra ở waypoint index 0

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-003, AC-REQ-003-01, SPEC-003, BR-004`

**Mục tiêu:** Phát hiện regression hiện tại khi tick chỉ xét `currentIndex + 1`.

**Precondition:** Session index 0, waypoint 0 là START, START PENDING.

**Input/Test data:** `TD-006`.

**Steps:** Gọi `tickSingleSimulation(session)`.

**Expected result:** `GeofencingService` được gọi với tọa độ waypoint 0 trước waypoint tiếp theo; START có thể transition trong integration test thật.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/SimulatorServiceTest.java`

**Evidence dự kiến:** `TEST` invocation order/arguments.

### TC-006 — Nhảy nhiều waypoint vẫn kiểm tra từng điểm

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-003, AC-REQ-003-02, SPEC-003, BR-004`

**Mục tiêu:** Không bỏ sót station waypoint trung gian khi multiplier cao.

**Precondition:** Session nhảy từ index 0 đến 3; waypoint 1, 2, 3 có tọa độ test.

**Input/Test data:** `TD-006`.

**Steps:** Gọi `tickSingleSimulation` và capture invocation.

**Expected result:** Geofence được gọi theo thứ tự index 0 (nếu tick đầu), 1, 2, 3; không chỉ gọi index 3.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/SimulatorServiceTest.java`

**Evidence dự kiến:** `TEST`.

### TC-007 — Payload event sau save

**Loại:** `Unit | Realtime`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-004, AC-REQ-004-01, SPEC-004, BR-005, BR-006`

**Mục tiêu:** Payload đúng record và publish chỉ sau save.

**Precondition:** Service fixture trong geofence; fixed Clock.

**Steps:** Capture `save` và `convertAndSend`; kiểm tra argument/payload.

**Expected result:** Topic `/topic/checkins`, trip/station/stopOrder/checkInTime đúng; save được gọi trước event; outside không publish.

**Automation:** `Có` ở service; `Dự kiến` realtime live.

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`; manual artifact nếu cần.

**Evidence dự kiến:** `TEST` và `REALTIME/API`.

### TC-008 — Frontend nhận đúng Trip và bỏ qua foreign event

**Loại:** `Manual`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-004, AC-REQ-004-01/02, SPEC-004, BR-008`

**Mục tiêu:** Chứng minh toast/UI không bị event Trip khác ghi đè.

**Precondition:** Dashboard đang xem một `currentTrip`; frontend/backend WebSocket chạy.

**Steps:**

1. Gửi/quan sát event hợp lệ của currentTrip.
2. Gửi payload event đã redact với `tripId` khác.
3. Quan sát toast, timeline và map.

**Expected result:** Event hợp lệ tạo toast đúng trạm; foreign event không tạo toast và không đổi UI currentTrip.

**Automation:** `Không` — frontend chưa có test script.

**Test file dự kiến:** `Không áp dụng`; lưu screenshot/log trong artifacts.

**Evidence dự kiến:** `UI` + `REALTIME`.

### TC-009 — Invalid/no-pending không mutation và không làm dừng simulator

**Loại:** `Unit`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-005, AC-REQ-005-01, SPEC-005`

**Mục tiêu:** Kiểm tra NaN/infinity/range, station data lỗi và no pending.

**Precondition:** Fixture repository/messaging.

**Input/Test data:** `TD-004`, `TD-005`.

**Steps:** Gọi từng input lỗi/no-pending; trong simulator test, chạy session khác sau một no-op.

**Expected result:** `Optional.empty`, không save/event/completion; scheduler flow không bị exception làm mất session khác.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`, có thể cập nhật `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `TEST`.

### TC-010 — Trạm cuối completion dùng cùng timestamp

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-005, AC-REQ-005-02, SPEC-005, BR-005, BR-007`

**Mục tiêu:** Chứng minh final check-in save/event và gọi `TripService.completeTrip` cùng `checkInTime`.

**Precondition:** Trạm trước CHECKED_IN, END PENDING; fixed Clock; query sau save không còn pending.

**Steps:** Gọi geofence tại END; capture completion argument.

**Expected result:** END CHECKED_IN; completion gọi đúng một lần với fixed time; không tự cập nhật Trip ngoài `TripService`.

**Automation:** `Có`

**Test file dự kiến:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Evidence dự kiến:** `TEST`.

### TC-011 — Regression suite/build/lint

**Loại:** `Integration | Build | Lint | Type-check`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-001..REQ-005, SPEC-001..SPEC-005`

**Mục tiêu:** Phát hiện regression source và contract hiện có.

**Precondition:** Dependencies khả dụng, Node runtime tương thích.

**Steps:** Chạy backend test, frontend lint/type-check/build và `git diff --check`.

**Expected result:** Backend test exit 0; lint/build/type-check exit 0 (warning baseline phải ghi rõ); diff check exit 0.

**Automation:** `Có`

**Test file dự kiến:** Command output; không tạo frontend test framework.

**Evidence dự kiến:** `BUILD`, `LINT`, `TYPE_CHECK`, `TEST`.

## Ma trận tình huống cần xem xét

| Tình huống | Áp dụng? | Test Case | Lý do/Ghi chú |
|---|---|---|---|
| Happy path | Có | TC-001, TC-007 | Core check-in/event |
| Invalid input | Có | TC-009 | Không check-in giả |
| Boundary value | Có | TC-002 | `<= radius` |
| Duplicate request/event | Có | TC-004 | Idempotency tuần tự |
| Missing data/not found | Có | TC-009 | No pending/invalid station |
| Permission/authentication | Không | — | Không có API check-in mới |
| Concurrency/race condition | Một phần | TC-004; review risk | Không distributed lock trong scope |
| Timeout | Không | — | Không gọi external service |
| External service failure | Không | — | Không có external dependency |
| Database failure/rollback | Có | TC-007/TC-011 | Save/event và full suite; integration nếu môi trường cho phép |
| Reconnect/out-of-order event | Một phần | TC-008 | Foreign event isolation; replay/out-of-order broker ngoài scope |
| Regression | Có | TC-011 | Backend/frontend contract |

## Acceptance Test

| Acceptance Criteria | Test Case | Cách chạy | Evidence | Trạng thái Evidence |
|---|---|---|---|---|
| AC-REQ-001-01 | TC-001, TC-002 | JUnit service test | EVD-011 dự kiến, test output | INCONCLUSIVE |
| AC-REQ-001-02 | TC-002 | JUnit outside case | EVD-012 dự kiến | INCONCLUSIVE |
| AC-REQ-002-01 | TC-003 | JUnit order case | EVD-013 dự kiến | INCONCLUSIVE |
| AC-REQ-002-02 | TC-004 | JUnit invocation count | EVD-014 dự kiến | INCONCLUSIVE |
| AC-REQ-003-01 | TC-005 | JUnit simulator tick | EVD-015 dự kiến | INCONCLUSIVE |
| AC-REQ-003-02 | TC-006 | JUnit multi-waypoint | EVD-016 dự kiến | INCONCLUSIVE |
| AC-REQ-004-01 | TC-007, TC-008 | JUnit + manual STOMP/browser | EVD-017 dự kiến | INCONCLUSIVE |
| AC-REQ-004-02 | TC-008 | Manual foreign event | EVD-018 dự kiến | INCONCLUSIVE |
| AC-REQ-005-01 | TC-009 | JUnit invalid/no-pending | EVD-019 dự kiến | INCONCLUSIVE |
| AC-REQ-005-02 | TC-010 | JUnit final transition | EVD-020 dự kiến | INCONCLUSIVE |

## Regression Test

| Khu vực có nguy cơ ảnh hưởng | Test hiện có cần chạy | Lý do |
|---|---|---|
| Geofencing/completion | `GeofencingServiceTest`, `TripServiceTest` | State transition và completion path dùng chung |
| Simulator telemetry | `SimulatorServiceTest` | Thêm call ở tick có thể đổi invocation/terminal behavior |
| REST Trip/simulator | `TripControllerTest`, API smoke nếu có | Check-ins được trả từ Trip và simulator endpoint vẫn tương thích |
| Station/Route | `StationServiceTest`, `RouteServiceTest` | Không đổi trực tiếp nhưng là entity/repository liên quan |
| Frontend realtime/timeline | `npm run lint`, type-check, build, manual TC-008 | Event subscription/filter/UI state |

## Lệnh kiểm tra

| Command | Mục đích | Điều kiện PASS | Evidence output |
|---|---|---|---|
| `bash ./mvnw test` tại `vehiceltracking-backend` | Full backend test | exit code 0, không failure/error | `artifacts/mvn-test.log` |
| `npm run lint` tại `vehicletracking-frontend` | Lint frontend | exit code 0; warning baseline ghi rõ | `artifacts/frontend-lint.log` |
| `npx tsc --noEmit` tại `vehicletracking-frontend` | Type-check | exit code 0 | `artifacts/frontend-typecheck.log` |
| `node node_modules/vite/bin/vite.js build` với Node 24.16.0 | Production build | exit code 0 | `artifacts/frontend-build.log` |
| `git diff --check` tại repository root | Whitespace/diff hygiene | exit code 0, không output | `artifacts/diff-check.log` nếu cần |

## Quy tắc Evidence

Với TC-001..TC-010, Gemini phải ghi EVD thực tế có Claim, liên kết REQ/Spec/TC/Plan Step, source/command, expected, actual, trạng thái và artifact nếu có. Không ghi PASS khi test chưa chạy; không dùng source code hoặc screenshot để thay thế assertion backend.

## Tiêu chí dừng và xử lý lỗi test

- Dừng bàn giao nếu TC-001, TC-002, TC-003, TC-005 hoặc TC-010 thất bại.
- Không sửa test để khớp behavior khác Spec.
- Nếu Maven/network không chạy được, ghi `INCONCLUSIVE` kèm nguyên nhân và không đề nghị approve.
- Nếu UI manual không thể chạy, giữ `INCONCLUSIVE` cho AC realtime/UI.

## Definition of Done

- [x] Mọi Requirement/Acceptance Criteria có Test Case.
- [x] Happy path và edge/failure case liên quan được bao phủ.
- [x] Test Data và precondition tái lập được.
- [x] Command kiểm tra tồn tại hoặc được Plan bổ sung rõ ràng.
- [x] Acceptance và regression scope được xác định.
- [x] Evidence yêu cầu được mô tả và không chứa secret.
- [x] Không có Critical Test Case ở trạng thái FAIL hoặc Evidence INCONCLUSIVE khi đề nghị approve.
