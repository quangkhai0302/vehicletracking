# Test-Plan — 003-route-eta

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `003-route-eta` |
| Requirement/Spec version | 2026-09-04 |
| Trạng thái | `READY — chạy sau implementation` |
| Người viết | Codex |
| Người duyệt | Người dùng |
| Ngày cập nhật | 2026-09-04 |

## Mục tiêu kiểm thử

Chứng minh lịch baseline, ETA dynamic, terminal completion và UI timeline có đúng data contract; phát hiện sai thứ tự stop, sai timestamp, ETA âm/không xác định, split completion state và telemetry của Trip khác. Compile/build không thay thế assertion service/realtime/manual evidence.

## Phạm vi kiểm thử

### Trong phạm vi

- Unit/service test Trip schedule/completion, geofence final check-in, simulator ETA/terminal telemetry bằng fixed Clock.
- Controller/API test read `TripDto` schedule nếu test fixture phù hợp.
- Lint/type-check/build và manual UI flow fallback/live/final state.

### Ngoài phạm vi

- Độ chính xác HERE/live traffic/GPS thật, load test broker, multi-trip UI selector và E2E automation framework mới.

## Chiến lược kiểm thử

### Unit Test

- Đối tượng: `TripService`, `SimulatorService`, `GeofencingService`.
- Mục tiêu: cumulative time, per-stop dynamic ETA, completion/Vehicle state/idempotency và telemetry fields.
- Framework hiện có: JUnit/Mockito qua Spring Boot test dependencies trong `pom.xml`.
- Dùng `Clock.fixed` và fixture station coordinates nhỏ/deterministic.

### Integration/API Test

- Boundary: `TripController` + H2/JPA, nếu bổ sung test controller theo convention `RouteControllerTest`.
- Mục tiêu: GET Trip serializes ordered check-ins và schedule đã lưu; không phải test real STOMP broker.

### Realtime/Event Test

- Topic: `/topic/telemetry`.
- Mục tiêu: mock `SimpMessagingTemplate`, capture terminal telemetry để assert `tripStatus`, completion ETA/time, vehicle status và no pending target.

### UI Test

- Không có frontend test runner tại baseline; không thêm framework trong feature.
- Chạy lint/type-check/build, sau đó manual browser flow với local backend/frontend và screenshot có timestamp.

## Môi trường và điều kiện tiên quyết

| Thành phần | Yêu cầu | Cách chuẩn bị |
|---|---|---|
| Backend | Java 26 theo `pom.xml` | Chạy `./mvnw clean test` tại `vehiceltracking-backend`. |
| Database test | H2 profile dev/test hiện có | Test fixture tự tạo Route/Trip/CheckIn hoặc context integration. |
| Frontend | Node version tương thích package lock | `npm run lint`, `npx tsc --noEmit`, `npm run build`. |
| Manual browser | Backend localhost 8080, frontend Vite 5173, dữ liệu START/STOP/END/Trip | Không ghi credential/API key vào artifacts. |

## Test Data

| ID | Dữ liệu | Mục đích | Cách tạo/dọn dẹp |
|---|---|---|---|
| TD-001 | Route START → STOP → END; times `2.0`, `3.0`, `0`; fixed T | Assert schedule 0/2/5 minutes. | Unit fixture/mock, không DB production. |
| TD-002 | Trip có START CHECKED_IN at A, STOP/END PENDING; fixed current position/speed | Assert actual vs cumulative dynamic ETA. | Unit fixture/mock. |
| TD-003 | Incident active giảm speed và same waypoint context | Assert ETA completion phản ánh effective speed. | Unit fixture/mock. |
| TD-004 | Final PENDING stop trong geofence | Assert auto completion/Vehicle terminal message. | Service fixture/mock. |
| TD-005 | Two Trip IDs with telemetry payload khác nhau | Assert frontend ignore Trip khác. | Manual/browser state hoặc component logic tối thiểu nếu không thêm framework. |

## Test Cases

### TC-001 — Tạo Trip sinh schedule cumulative và completion baseline

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001, AC-REQ-001-01, SPEC-001, BR-001, BR-007`.

**Mục tiêu:** Chứng minh schedule từng stop/stop cuối chính xác với fixed Clock.

**Precondition:** TD-001; RouteStation trả theo order 1,2,3.

**Steps:**

1. Gọi `TripService#createTrip` với fixed T.
2. Đọc/capture `TripCheckIn` đã save và `TripDto`.

**Expected result:** START schedule = T; STOP = T+2m; END = T+5m; check-ins ordered; Trip startTime = T.

**Automation:** `Có`

**Test file dự kiến:** `service/TripServiceTest.java`.

**Evidence dự kiến:** EVD-010 (TEST log/assertion).

### TC-002 — GET Trip giữ ordered schedule hiện có

**Loại:** `Integration/API`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-001, REQ-004, SPEC-001, BR-001, BR-006`.

**Mục tiêu:** Xác nhận `GET /api/trips/{id}` trả `checkIns` tăng dần và đủ scheduledArrivalTime để frontend fallback.

**Precondition:** Trip có ba check-ins với stop order insert không theo thứ tự.

**Steps:** Call GET detail.

**Expected result:** HTTP 200; check-ins 1..n, hours đúng persisted values, client không cần endpoint mới.

**Automation:** `Có`

**Test file dự kiến:** `controller/TripControllerTest.java`.

**Evidence dự kiến:** EVD-010.

### TC-003 — Tính ETA pending cumulative theo fixed time

**Loại:** `Unit`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-002, AC-REQ-002-01, SPEC-002, BR-002, BR-007`.

**Mục tiêu:** Kiểm tra count/order, non-negative cumulative distance/ETA và absolute estimated time.

**Precondition:** TD-002, speed effective known > 0.

**Steps:** Gọi phương thức calculation/one simulation step testable với current waypoint và fixed Clock.

**Expected result:** Một StationEta mỗi check-in; PENDING ETA/time tăng theo stop order; ETA final chính xác `round(distance/speed)`; không NaN/negative.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** EVD-011.

### TC-004 — Stop đã check-in và incident đổi ETA

**Loại:** `Unit`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-002, REQ-005, AC-REQ-002-02, SPEC-002, BR-002, BR-005`.

**Mục tiêu:** Không mất actual arrival và ETA dynamic phản ánh effective speed.

**Precondition:** TD-002/TD-003.

**Steps:**

1. Tính ETA có START CHECKED_IN at A.
2. Tính tick với/không incident tại cùng context.

**Expected result:** START `eta=0`, `distance=0`, time=A; pending ETA với incident giảm speed không sớm hơn trường hợp cùng vị trí/speed không incident; completion fields khớp final item.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** EVD-011.

### TC-005 — Final auto check-in chốt state và phát terminal telemetry

**Loại:** `Unit/Realtime`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-003, AC-REQ-003-01, SPEC-003, BR-003, BR-004`.

**Mục tiêu:** Chứng minh transition final đồng bộ DB/service/telemetry.

**Precondition:** TD-004, final station nằm trong geofence, `SimpMessagingTemplate` capture được.

**Steps:** Advance đúng một simulator tick xử lý final check-in.

**Expected result:** Final check-in actual time = fixed T; Trip COMPLETED/endTime=T; Vehicle IDLE/speed 0; terminal telemetry một lần có `status=IDLE`, `tripStatus=COMPLETED`, completion ETA 0/time T, all station ETA checked-in và target pending null.

**Automation:** `Có`

**Test file dự kiến:** `service/GeofencingServiceTest.java`, `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** EVD-011, EVD-012.

### TC-006 — Completion idempotent

**Loại:** `Unit`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-005, AC-REQ-005-01, SPEC-003, BR-004`.

**Mục tiêu:** Tránh overwrite endTime khi manual/retry completion.

**Precondition:** Trip đã COMPLETED tại T1, Vehicle IDLE.

**Steps:** Gọi completion lần hai tại T2 > T1.

**Expected result:** endTime vẫn T1, không tạo check-in/event duplicate, Vehicle remains IDLE/0.

**Automation:** `Có`

**Test file dự kiến:** `service/TripServiceTest.java`.

**Evidence dự kiến:** EVD-012.

### TC-007 — Timeline fallback, dynamic completion và isolation Trip

**Loại:** `Manual/UI`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-004, AC-REQ-004-01, SPEC-004, BR-006`.

**Mục tiêu:** Chứng minh dashboard không trống trước simulation và chỉ dùng event đúng trip.

**Precondition:** Browser local có Trip với check-ins; backend simulator chạy.

**Steps:**

1. Mở dashboard trước Start; lưu screenshot schedule/full completion time.
2. Start simulator; lưu screenshot live ETA mỗi stop/completion.
3. Trigger/quan sát final check-in; lưu screenshot terminal completed.
4. Nếu tạo được payload Trip khác trong local test state, xác nhận UI current Trip không đổi; nếu không, ghi INCONCLUSIVE và lý do.

**Expected result:** Fallback schedule đúng; dynamic values xuất hiện khi telemetry; final state/detail hiển thị completion; không leak telemetry Trip khác.

**Automation:** `Không` — baseline không có frontend test script.

**Test file dự kiến:** `vehicletracking-frontend/src/App.tsx`, `components/TimelinePanel.tsx`.

**Evidence dự kiến:** EVD-013 (ảnh timestamp/manual steps).

### TC-008 — Backward-compatible parsing telemetry

**Loại:** `Manual/Type check`

**Mức ưu tiên:** `Medium`

**Liên kết:** `REQ-005, AC-REQ-005-01, SPEC-003, BR-005`.

**Mục tiêu:** UI không crash khi fields completion chưa có và new types compile.

**Precondition:** Payload telemetry mô phỏng missing new fields hoặc frontend defensive branch.

**Steps:** Type-check và manual/dev inspect fallback.

**Expected result:** Existing station ETA vẫn render; summary uses safe fallback (`--:--`/không kết luận completed); no TypeScript error.

**Automation:** `Một phần`

**Test file dự kiến:** `types/index.ts`, `App.tsx`, `TimelinePanel.tsx`.

**Evidence dự kiến:** EVD-014.

### TC-009 — Regression backend suite

**Loại:** `Integration/Regression`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-006, AC-REQ-006-01, SPEC-006`.

**Steps:** Chạy `./mvnw clean test` bằng Java 26.

**Expected result:** Exit 0, toàn bộ test suite pass bao gồm Route/Station existing và test ETA mới.

**Automation:** `Có`

**Evidence dự kiến:** EVD-010.

### TC-010 — Lint frontend

**Loại:** `Lint`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-006, SPEC-006`.

**Steps:** `npm run lint`.

**Expected result:** Exit 0; warnings baseline nếu có được phân biệt với warning mới.

**Automation:** `Có`

**Evidence dự kiến:** EVD-014.

### TC-011 — Type-check/build frontend

**Loại:** `Type check/Build`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-006, SPEC-006`.

**Steps:** `npx tsc --noEmit`, `npm run build`.

**Expected result:** Cả hai exit 0.

**Automation:** `Có`

**Evidence dự kiến:** EVD-015.

## Ma trận tình huống cần xem xét

| Tình huống | Áp dụng? | Test Case | Lý do/Ghi chú |
|---|---|---|---|
| Happy path | Có | TC-001, TC-003, TC-005, TC-007 | Schedule, ETA, final state/UI. |
| Boundary value | Có | TC-003, TC-004 | ETA 0/check-in, final stop, speed finite. |
| Duplicate request/event | Có | TC-006 | Completion idempotency. |
| Missing data/not found | Có | TC-002 | Existing GET Trip contract. |
| Permission/authentication | Không | N/A | Repository feature has no auth boundary. |
| Concurrency/race condition | Có | TC-005, TC-006 | Final/retry must not overwrite endTime. |
| External service failure | Không | N/A | Không có provider ngoài scope. |
| Reconnect/out-of-order event | Một phần | TC-007, TC-008 | Latest snapshot/filter, không có replay broker. |
| Regression | Có | TC-009..TC-011 | Backend/full frontend checks. |

## Acceptance Test

| Acceptance Criteria | Test Case | Cách chạy | Evidence | Trạng thái Evidence |
|---|---|---|---|---|
| AC-REQ-001-01 | TC-001, TC-002 | Unit + controller integration | EVD-010 | INCONCLUSIVE |
| AC-REQ-002-01 | TC-003 | Simulator unit | EVD-011 | INCONCLUSIVE |
| AC-REQ-002-02 | TC-004 | Simulator unit | EVD-011 | INCONCLUSIVE |
| AC-REQ-003-01 | TC-005, TC-006 | Service/realtime capture | EVD-011, EVD-012 | INCONCLUSIVE |
| AC-REQ-004-01 | TC-007 | Manual browser | EVD-013 | INCONCLUSIVE |
| AC-REQ-005-01 | TC-006, TC-008 | Unit/type/manual | EVD-012, EVD-014 | INCONCLUSIVE |
| AC-REQ-006-01 | TC-009..TC-011 | Commands/artifacts | EVD-010, EVD-014, EVD-015 | INCONCLUSIVE |

## Regression Test

| Khu vực có nguy cơ ảnh hưởng | Test hiện có cần chạy | Lý do |
|---|---|---|
| Route/Station data + app context | Full `./mvnw clean test` | Clock/service wiring, JPA and DTO changes may affect context. |
| Existing STOMP telemetry consumer | Manual TC-007 + frontend build | Payload is additive but UI App/Map both consume telemetry. |
| Route management frontend | `npm run lint`, `npx tsc --noEmit`, `npm run build` | Types/App are shared with RouteModal/SimulatorPanel. |

## Lệnh kiểm tra

| Command | Working directory | Mục đích | Điều kiện PASS | Evidence output |
|---|---|---|---|---|
| `./mvnw clean test` | `vehiceltracking-backend` | Unit/integration regression | Exit 0, ETA tests + existing suite pass | EVD-010 artifact log |
| `npm run lint` | `vehicletracking-frontend` | Lint | Exit 0 | EVD-014 artifact log |
| `npx tsc --noEmit` | `vehicletracking-frontend` | Type check | Exit 0 | EVD-015 artifact log |
| `npm run build` | `vehicletracking-frontend` | Production build | Exit 0 | EVD-015 artifact log |
| `git diff --check` | repository root | Whitespace guard | No output/exit 0 | EVD-015 command result |

## Quy tắc Evidence

Gemini phải ghi actual test count/exit code vào `evidence.md`, lưu artifact đầy đủ trong `docs/features/003-route-eta/artifacts/` khi cần. Screenshot chứng minh UI, không thay thế TC-001..TC-006 backend/realtime test. Manual flow không chạy được phải là `INCONCLUSIVE`.

## Definition of Done

- [x] Mọi Requirement/Acceptance Criteria có Test Case.
- [x] Happy path, final completion, idempotency, event/UI isolation và regression được bao phủ.
- [x] Test Data/precondition có thể tái lập bằng fixed Clock/fixture.
- [x] Chỉ dùng command đã xác minh trong Survey/AGENTS.
- [x] Evidence dự kiến liên kết từng critical path.
