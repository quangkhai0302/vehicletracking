# Test-Plan — 005-realtime-vehicle-simulator

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Requirement/Spec version | 2026-09-04 |
| Trạng thái | `READY — chạy sau implementation` |
| Người viết | Codex |
| Người duyệt | Người dùng |
| Ngày cập nhật | 2026-09-04 |

## Mục tiêu kiểm thử

Chứng minh lifecycle/session, movement/persistence, reset, contract `simulationRunId + sequence`, two-topic STOMP publish và frontend guard hoạt động theo Spec. Build/lint không thay thế test state machine, REST error hoặc evidence realtime UI.

## Phạm vi kiểm thử

### Trong phạm vi

- Unit/service tests Simulator: start/control/multiplier, tick, terminal/reset, sequence và error isolation.
- Controller/API tests Simulator: response success typed và `ProblemDetail` 400/404/409.
- Realtime test `SimpMessagingTemplate` capture cho normal/terminal/two topic.
- Manual browser/STOMP stimulus để chứng minh guard foreign/run/stale và UX control/reconnect.
- Backend regression, frontend lint/type-check/build/diff check.

### Ngoài phạm vi

- Benchmark 1-second scheduler/load broker, GPS thật, traffic provider accuracy.
- Cross-instance race/leader election, replay persistence, browser E2E framework mới, authentication.

## Chiến lược kiểm thử

### Unit/service test

- Mở rộng `SimulatorServiceTest` theo JUnit 5/Mockito style hiện có.
- Dùng fixed `Clock`, fixture `Trip → Route → START/STOP/END → Vehicle`, `SimpMessagingTemplate` captor và repository mock.
- Nếu cần test `tickAllSimulations`, expose method package-private trong cùng package test (không public REST API) để không dùng sleep/reflection.

### Controller/API test

- Tạo `SimulatorControllerTest` theo Spring/MockMvc convention đã dùng cho Route/Station/Trip controller tests.
- Assert exact HTTP status, content type `application/problem+json`, `status/detail/instance`, và field response public; không assert inner `SimulationSession` JSON.

### Realtime/event test

- Capture hai call `SimpMessagingTemplate#convertAndSend` trong cùng tick and compare `tripId`, vehicle ID, run ID, sequence, timestamp field values.
- Test service không chứng minh broker/network actual; manual TC-011 dùng STOMP subscriber để bổ sung delivery evidence.

### UI/manual test

- Baseline không có frontend test runner (`package.json`); không thêm framework cho feature.
- Chạy local backend/frontend, browser, và STOMP artifact script không chứa token. Capture screenshots/video/captured broker log, mô tả precondition/expected/actual.
- Nếu môi trường không chạy được, evidence phải `INCONCLUSIVE`, không suy đoán PASS.

## Môi trường và điều kiện tiên quyết

| Thành phần | Yêu cầu | Cách chuẩn bị |
|---|---|---|
| Backend test | Java 26 theo `pom.xml`; Maven wrapper | `cd vehiceltracking-backend && ./mvnw clean test` |
| Backend local manual | Backend tại localhost 8080, profile/dữ liệu local có Trip route >=2 stops | Không ghi connection secret/log sensitive data |
| Frontend verification | Node version tương thích `package-lock.json` | `cd vehicletracking-frontend`; lint, `npx tsc --noEmit`, build |
| Browser/STOMP | Vite local và STOMP `/ws-raw`; Trip A, Trip B hoặc controlled stimulus | Không dùng/ghi credential; dừng process sau manual test |

## Test Data

| ID | Dữ liệu | Mục đích | Cách tạo/dọn dẹp |
|---|---|---|---|
| TD-001 | Trip A with route START → STOP → END, Vehicle V, ordered stations/coordinates, all check-ins PENDING | Lifecycle, tick, reset, terminal | Mockito fixture/H2 test data |
| TD-002 | Trip B/Vehicle W hợp lệ | Foreign session/telemetry và scheduler isolation | Mockito fixture/H2 test data |
| TD-003 | Session A no/one active incident, fixed `Clock` | Assert speed factor/ETA and timestamp deterministic | Mockito fixture |
| TD-004 | Payload telemetry A/run R sequence 7, 8; foreign Trip B; foreign run; malformed body | UI guard/realtime manual | STOMP artifact script/browser dev session |
| TD-005 | Session A throws in tick, B valid | Per-session catch behavior | Unit fixture; no actual scheduler sleep |
| TD-006 | Multiplier values `1,2,5,10,0,-1,10.1,NaN,Infinity` | Validation/retention | Unit/controller request fixture |

## Test Cases

### TC-001 — Start tạo session và contract command

**Loại:** `Unit/Service`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001`, `AC-REQ-001-01`, `SPEC-001`, `BR-001`, `BR-002`.

**Precondition:** TD-001; no session; route repository trả 3 stations ordered.

**Steps:**

1. Gọi Start Trip A.
2. Đọc public status/session response testable.
3. Gọi Start Trip A lần hai trước Reset.

**Expected result:** Lần 1 tạo run UUID valid, `RUNNING`, multiplier 1, index 0, last sequence 0. Lần 2 throw `SimulatorConflictException`, giữ identity/state session đầu.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-012`.

### TC-002 — REST response/error đúng trạng thái

**Loại:** `Integration/API`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001`, `AC-REQ-001-02`, `SPEC-001`, `BR-002`.

**Precondition:** Controller fixture/H2/mock appropriate theo convention repository.

**Steps:**

1. POST Start Trip hợp lệ; assert typed fields.
2. Gọi endpoint với Trip không có, route <2, duplicate Start và Pause IDLE.

**Expected result:** Success 200 có `message/status/tripId/simulationRunId`; missing=404, route invalid=400, invalid state=409; error là `application/problem+json`, không success map.

**Automation:** `Có`

**Test file dự kiến:** `controller/SimulatorControllerTest.java`.

**Evidence dự kiến:** `EVD-011`.

### TC-003 — Pause/Resume giữ state, UUID và sequence

**Loại:** `Unit/Service`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-001`, `REQ-003`, `AC-REQ-002-02`, `SPEC-001`, `BR-002`, `BR-003`.

**Steps:** Start; publish/advance one controlled tick; Pause; kiểm tra public status; Resume.

**Expected result:** Pause/Resume không tạo UUID mới, không reset index/last sequence/multiplier. Pause repeated và Resume state khác bị conflict.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-012`.

### TC-004 — Normal tick persist Vehicle và phát telemetry sequence

**Loại:** `Unit/Realtime`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-002`, `AC-REQ-002-01`, `SPEC-002`, `BR-003`, `BR-005`.

**Precondition:** TD-001/TD-003, session RUNNING, known waypoint and no terminal check-in.

**Steps:** Gọi một tick testable, capture Vehicle save và `SimpMessagingTemplate`.

**Expected result:** Index progresses bounded; Vehicle save has new waypoint, finite speed/heading, `IN_TRANSIT`; telemetry has ETA/check-in fields, fixed-clock timestamp, valid run UUID, `sequence=1`; normal tick tiếp theo sequence=2.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-013`.

### TC-005 — Pause không mutate/publish

**Loại:** `Unit/Service`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-002`, `AC-REQ-002-02`, `SPEC-002`, `BR-003`.

**Precondition:** Session paused at index k; repository/template clear invocations.

**Steps:** Execute scheduler loop/tick candidate for session.

**Expected result:** k, persisted Vehicle and `lastPublishedSequence` unchanged; no telemetry send/Vehicle save/geofence call.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-013`.

### TC-006 — Multiplier whitelist và invalid input không mutate

**Loại:** `Unit/API`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-003`, `AC-REQ-003-01`, `SPEC-003`, `BR-002`.

**Precondition:** TD-006; RUNNING và PAUSED session separately.

**Steps:** Apply 1/2/5/10, then 0/-1/10.1/NaN/Infinity; test both service and HTTP binding cases feasible.

**Expected result:** Chỉ 4 exact values success; success response returns applied value; each invalid request 400 and session retains prior multiplier. IDLE/COMPLETED valid value returns 409.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`, `controller/SimulatorControllerTest.java`.

**Evidence dự kiến:** `EVD-012`, `EVD-011`.

### TC-007 — Reset transactional state và không còn dead run

**Loại:** `Unit/Service`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-003`, `AC-REQ-003-02`, `SPEC-003`, `BR-004`.

**Precondition:** TD-001 with changed check-ins/Trip COMPLETED/end time/Vehicle intermediate state; session may be RUNNING, PAUSED and COMPLETED.

**Steps:** Call Reset; inspect repository saves/status; attempt tick old session reference and check service GET state; Start same Trip again.

**Expected result:** Check-ins PENDING/no actual; Trip RUNNING/end null; Vehicle coordinates START/IDLE/speed 0; map has no session; no service-driven old run publish; Start afterward succeeds with UUID different prior run. Reset IDLE returns conflict and no mutation.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-015`.

### TC-008 — Two topic payload cùng run/sequence

**Loại:** `Unit/Realtime`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-004`, `AC-REQ-004-02`, `SPEC-004`, `BR-005`.

**Precondition:** TD-001 normal and terminal controlled ticks.

**Steps:** Capture all template sends for a logical tick; group `/topic/telemetry` and `/topic/vehicle/{vehicleId}`.

**Expected result:** Đúng 2 sends/logical snapshot; all shared fields `tripId`, vehicle ID, run ID, sequence, timestamp are equal. Normal sequence increases; terminal uses next sequence and retains same UUID.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-014`.

### TC-009 — Frontend bỏ foreign/run cũ/sequence cũ

**Loại:** `Manual/UI/Realtime`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-004`, `AC-REQ-004-01`, `SPEC-004`, `BR-006`.

**Precondition:** Browser chọn Trip A/run R, đã render sequence 7; TD-004. Backend/local broker running.

**Steps:**

1. Record screenshot initial marker/timeline sequence 7.
2. Publish controlled payload Trip B, then Trip A/run khác, then Trip A/R sequence 7, then valid Trip A/R sequence 8 through STOMP artifact script and broker subscriber confirmation.
3. Observe UI after each stimulus; save screenshot/video/console evidence redacted.

**Expected result:** Ba payload invalid leave Map marker, speed, Timeline/status unchanged; sequence 8 alone updates UI. Script log confirms broker delivery; screenshot proves visible UI outcome.

**Automation:** `Không` — browser evidence; stimulus script may automate send only.

**Test file/artifact dự kiến:** `App.tsx`, `websocket.ts`, `docs/features/005-realtime-vehicle-simulator/artifacts/send-telemetry-stimulus.mjs`.

**Evidence dự kiến:** `EVD-016`.

### TC-010 — Terminal một lần và lỗi A không chặn session B

**Loại:** `Unit/Service`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-005`, `AC-REQ-005-01`, `SPEC-002`, `SPEC-005`, `BR-007`.

**Steps:**

1. Fixture final check-in all checked; tick twice.
2. Fixture active A throwing controlled exception, B valid; call testable `tickAllSimulations` once.

**Expected result:** A terminal emits exactly one COMPLETED/IDLE snapshot, later tick none. In second fixture error A is logged/caught and B `tickSingleSimulation`/telemetry still occurs in same loop.

**Automation:** `Có`

**Test file dự kiến:** `service/SimulatorServiceTest.java`.

**Evidence dự kiến:** `EVD-014`.

### TC-011 — Control error UX và stable connection lifecycle

**Loại:** `Manual/UI/API`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-005`, `AC-REQ-005-02`, `SPEC-005`, `BR-002`, `BR-006`.

**Steps:**

1. Start valid simulation and note one STOMP connection/subscription in browser Network/console.
2. Pause/Resume and confirm no extra disconnect/reconnect caused by `simStatus` change.
3. Trigger duplicate Start or invalid multiplier API from UI/controlled request; observe toast and UI state.
4. Reset success; verify UI clear/fallback then Start new UUID after reset.

**Expected result:** Error appears user-safe; no prior running/paused state overwritten on failure; connection/subscription count does not grow on state changes; reset does not show old telemetry and new Start produces new run identity.

**Automation:** `Không` — no UI runner baseline.

**Evidence dự kiến:** `EVD-017`.

### TC-012 — Malformed STOMP message không làm app crash

**Loại:** `Manual/Realtime`

**Mức ưu tiên:** `Medium`

**Liên kết:** `REQ-004`, `REQ-005`, `SPEC-004`, `SPEC-005`, `BR-006`.

**Steps:** Publish non-JSON/missing identity payload to `/topic/telemetry` in local test session, then valid next telemetry.

**Expected result:** App remains connected/rendered; bad payload is dropped with safe diagnostic; valid payload afterward accepted when guard matches.

**Automation:** `Không` — no frontend test runner.

**Evidence dự kiến:** `EVD-016` or `INCONCLUSIVE` if broker blocks invalid payload representation.

### TC-013 — Backend full regression

**Loại:** `Regression`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001..REQ-005`, `SPEC-006`.

**Steps:** Run full Maven test suite under Java 26.

**Expected result:** Exit 0; existing station/route/trip/geofence/ETA tests and tests added in this feature pass. Record exact tests/failures/errors/skips.

**Automation:** `Có`

**Evidence dự kiến:** `EVD-018`.

### TC-014 — Frontend lint

**Loại:** `Lint/Regression`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-004`, `REQ-005`, `SPEC-006`.

**Steps:** `npm run lint`.

**Expected result:** Exit 0; note baseline warnings separately and no new error.

**Automation:** `Có`

**Evidence dự kiến:** `EVD-019`.

### TC-015 — Frontend type-check/build

**Loại:** `Type check/Build`

**Mức ưu tiên:** `Critical`

**Liên kết:** `REQ-001..REQ-005`, `SPEC-006`.

**Steps:** `npx tsc --noEmit`, then `npm run build`.

**Expected result:** Both exit 0; TypeScript contracts compile across shared `App`, Map, Timeline and API.

**Automation:** `Có`

**Evidence dự kiến:** `EVD-020`.

### TC-016 — Diff hygiene

**Loại:** `Static/Regression`

**Mức ưu tiên:** `High`

**Liên kết:** `REQ-005`, `SPEC-006`.

**Steps:** Run `git diff --check` from repository root after all changes.

**Expected result:** Exit 0/no whitespace error or conflict marker. This is only hygiene, not behavior evidence.

**Automation:** `Có`

**Evidence dự kiến:** `EVD-020`.

## Ma trận tình huống cần xem xét

| Tình huống | Áp dụng? | Test Case | Ghi chú |
|---|---|---|---|
| Happy path | Có | TC-001, TC-004, TC-008 | Start, move, two topic payload. |
| Boundary value | Có | TC-006 | Exact whitelist/malformed numeric values. |
| Duplicate request/event | Có | TC-001, TC-002, TC-009 | Duplicate Start; stale sequence/run. |
| Missing data/not found | Có | TC-002 | Trip/session route/state validation. |
| Permission/authentication | Không | N/A | Repository has no auth boundary for this feature. |
| Concurrency/race condition | Có, limited | TC-001, TC-010 | Per-JVM map/session and scheduler isolation; multi-instance excluded. |
| External service failure | Không | N/A | No external service added. |
| Reconnect/out-of-order | Có | TC-009, TC-011, TC-012 | UI client guard/single effect; no replay claim. |
| Regression | Có | TC-013..TC-016 | Full backend/frontend checks. |

## Acceptance Test

| Acceptance Criteria | Test Case | Cách chạy | Evidence | Trạng thái Evidence |
|---|---|---|---|---|
| AC-REQ-001-01 | TC-001, TC-002 | Service + MockMvc | EVD-011, EVD-012 | INCONCLUSIVE |
| AC-REQ-001-02 | TC-002, TC-003 | MockMvc + service | EVD-011, EVD-012 | INCONCLUSIVE |
| AC-REQ-002-01 | TC-004, TC-008 | Service/template capture | EVD-013, EVD-014 | INCONCLUSIVE |
| AC-REQ-002-02 | TC-005 | Service | EVD-013 | INCONCLUSIVE |
| AC-REQ-003-01 | TC-006 | Service + MockMvc | EVD-011, EVD-012 | INCONCLUSIVE |
| AC-REQ-003-02 | TC-007, TC-011 | Service + manual UI | EVD-015, EVD-017 | INCONCLUSIVE |
| AC-REQ-004-01 | TC-009, TC-012 | STOMP stimulus + browser | EVD-016 | INCONCLUSIVE |
| AC-REQ-004-02 | TC-008 | Template capture | EVD-014 | INCONCLUSIVE |
| AC-REQ-005-01 | TC-010 | Service | EVD-014 | INCONCLUSIVE |
| AC-REQ-005-02 | TC-011 | Manual UI/API | EVD-017 | INCONCLUSIVE |

## Regression Test

| Khu vực có nguy cơ ảnh hưởng | Test hiện có cần chạy | Lý do |
|---|---|---|
| Trip/route/geofence/ETA + app context | `./mvnw clean test` | Simulator changes share entities/repositories and calls previous feature logic. |
| Controller error formatting | `SimulatorControllerTest` plus existing Route/Station controller suite | New scoped advice must not affect existing advice. |
| Shared frontend App/types | lint, type-check, build + TC-009/TC-011 | App owns station/route/incident features and all telemetry UI. |
| Whitespace/merge health | `git diff --check` | Hygiene only. |

## Lệnh kiểm tra

| Command | Working directory | Mục đích | Điều kiện PASS | Evidence output |
|---|---|---|---|---|
| `./mvnw clean test` | `vehiceltracking-backend` | Unit/API/full regression | Exit 0, record test count | `artifacts/mvn-clean-test.log`, EVD-018 |
| `npm run lint` | `vehicletracking-frontend` | Lint | Exit 0; distinguish baseline warning | `artifacts/frontend-verification.log`, EVD-019 |
| `npx tsc --noEmit` | `vehicletracking-frontend` | Type check | Exit 0 | `artifacts/frontend-verification.log`, EVD-020 |
| `npm run build` | `vehicletracking-frontend` | Production build | Exit 0 | `artifacts/frontend-verification.log`, EVD-020 |
| `git diff --check` | repository root | Whitespace guard | Exit 0/no output | `artifacts/diff-check.log`, EVD-020 |

## Quy tắc Evidence

Gemini phải ghi actual test count/exit code, command và commit/worktree vào `evidence.md`; lưu full output hoặc artifact redacted trong folder feature. Screenshot/video chỉ chứng minh UI quan sát được; broker subscriber log chỉ chứng minh delivery; cả hai không thay thế service/controller assertion. TC-009/TC-011/TC-012 không chạy được phải ghi `INCONCLUSIVE` với lý do.

## Definition of Done

- [x] Mọi Requirement/Acceptance Criteria có Test Case.
- [x] Happy path, invalid state, reset, terminal, stale message, malformed message và session isolation được cover.
- [x] Không dùng command frontend test không tồn tại.
- [x] Mỗi Requirement quan trọng có Evidence dự kiến và current status `INCONCLUSIVE` trước implementation.
- [x] Regression commands/artifacts có path và điều kiện pass rõ ràng.
