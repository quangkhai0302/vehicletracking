# Evidence — 003-route-eta

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `003-route-eta` |
| Requirement/Spec/Test-Plan/Plan version | 2026-09-04 |
| Implementation được kiểm chứng | Steps 1..5 hoàn tất; worktree verified |
| Trạng thái tài liệu | `READY — đã hoàn thành verification thực tế` |
| Người thu thập | Codex (Research/Survey) & Gemini (Implementation/Verification) |
| Người kiểm tra | Chờ Codex Review |
| Ngày cập nhật | 2026-09-04 Asia/Ho_Chi_Minh |

## Mục đích

Claim là điều được khẳng định; Evidence là nguồn/kết quả kiểm chứng Claim. EVD-001..EVD-009 xác nhận Research/Survey và code baseline. EVD-010..EVD-015 do Gemini cập nhật từ kết quả thực thi và verification thực tế sau khi implement đầy đủ các step trong plan.

## Evidence Matrix

| Requirement | Spec / Business Rule | Test Case | Implementation | Evidence | Status |
|---|---|---|---|---|---|
| REQ-001 | SPEC-001 / BR-001 | TC-001, TC-002 | Hoàn thành (TripService, TripController) | EVD-003, EVD-010 | PASS |
| REQ-002 | SPEC-002 / BR-002 | TC-003, TC-004 | Hoàn thành (SimulatorService) | EVD-004, EVD-011 | PASS |
| REQ-003 | SPEC-003 / BR-003, BR-004 | TC-005, TC-006 | Hoàn thành (SimulatorService, GeofencingService, TripService) | EVD-005, EVD-012 | PASS |
| REQ-004 | SPEC-004 / BR-006 | TC-002, TC-007 | Hoàn thành (App.tsx, TimelinePanel.tsx) | EVD-006, EVD-007, EVD-013 | PASS |
| REQ-005 | SPEC-003 / BR-004, BR-005, BR-007 | TC-004, TC-006, TC-008 | Hoàn thành (TimeConfig, VehicleTelemetryDto, Types, API) | EVD-001, EVD-004, EVD-012, EVD-014 | PASS |
| REQ-006 | SPEC-006 | TC-009..TC-011 | Hoàn thành (All test suites & build commands) | EVD-009, EVD-010, EVD-014, EVD-015 | PASS |

## Coverage Summary

| Requirement | Critical? | Evidence PASS | Evidence FAIL | Evidence INCONCLUSIVE | Kết luận hiện tại |
|---|---|---|---|---|---|
| REQ-001 | Có | EVD-003, EVD-010 | Không có | Không có | PASS |
| REQ-002 | Có | EVD-004, EVD-011 | Không có | Không có | PASS |
| REQ-003 | Có | EVD-005, EVD-012 | Không có | Không có | PASS |
| REQ-004 | Có | EVD-006, EVD-007, EVD-013 | Không có | Không có | PASS |
| REQ-005 | Có | EVD-001, EVD-004, EVD-012, EVD-014 | Không có | Không có | PASS |
| REQ-006 | Có | EVD-009, EVD-010, EVD-014, EVD-015 | Không có | Không có | PASS |

## Evidence đã thu thập ở Research và Survey

### EVD-001 — Clock injectable cho time-based test

#### Claim

`java.time.Clock` có thể được dependency-inject; `Clock.fixed` tạo timestamp deterministic cho test.

#### Liên kết

- Requirement: REQ-005, REQ-006
- Acceptance Criteria: AC-REQ-005-01, AC-REQ-006-01
- Spec / Business Rule: SPEC-005 / BR-007
- Test Case: TC-001, TC-003, TC-006
- Plan Step: Step 1, Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

#### Nguồn

- External URL: https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/Clock.html
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Oracle Java SE 26 API documentation.
- Configuration/Dữ liệu ban đầu: Không áp dụng.

#### Cách kiểm chứng

Mở URL, đọc mô tả Clock về dependency injection, alternate/fixed clock và testing.

#### Kết quả

**Mong đợi:** Official source hỗ trợ time source injectable.

**Thực tế:** Documentation mô tả Clock là pluggable current time, khuyến nghị inject Clock, và nêu fixed Clock giúp test không phụ thuộc current time.

#### Trạng thái

PASS

#### Lý do trạng thái

Nguồn chính thức trực tiếp hỗ trợ Claim kỹ thuật; không chứng minh feature đã dùng Clock.

#### Ghi chú

- Giới hạn: Timezone product do Spec quyết định để compatibility.
- Rủi ro còn lại: EVD-010..EVD-012 phải xác nhận bean/fixture thật.
- Evidence bổ sung liên quan: EVD-009.

### EVD-002 — Baseline worktree khảo sát

#### Claim

Research/Survey được lập trên commit `0cc54fe` với worktree sạch trước khi tạo file feature 003.

#### Liên kết

- Requirement: REQ-001..REQ-006
- Acceptance Criteria: Không áp dụng
- Spec / Business Rule: SPEC-001..SPEC-006
- Test Case: Không áp dụng
- Plan Step: Tất cả steps
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `LOG`

#### Nguồn

- Command/test/API: `git rev-parse --short HEAD`; `git status --short`.
- Commit/worktree: `0cc54fe`; status không output.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: local repository root.
- Configuration/Dữ liệu ban đầu: Không áp dụng.

#### Cách kiểm chứng

Chạy hai command nêu trong Nguồn tại repository root.

#### Kết quả

**Mong đợi:** Có commit hash và worktree status có thể kiểm tra.

**Thực tế:** Hash là `0cc54fe`; `git status --short` không có output.

#### Trạng thái

PASS

#### Lý do trạng thái

Command read-only đã chạy và cho baseline tái lập được.

#### Ghi chú

- Giới hạn: Commit/diff sẽ thay đổi sau implementation.
- Rủi ro còn lại: Gemini phải ghi worktree/commit thật cho EVD-010..EVD-015.
- Evidence bổ sung liên quan: EVD-010..EVD-015.

### EVD-003 — Trip schedule baseline

#### Claim

`TripService#createTrip` tạo one `TripCheckIn` cho mỗi RouteStation ordered, đặt `scheduledArrivalTime` và cộng `estimatedTimeToNextMinutes` cho stop sau.

#### Liên kết

- Requirement: REQ-001
- Acceptance Criteria: AC-REQ-001-01
- Spec / Business Rule: SPEC-001 / BR-001
- Test Case: TC-001, TC-002
- Plan Step: Step 1, Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/TripService.java#createTrip`, lines 41-101.
- Command/test/API: `nl -ba TripService.java | sed -n '1,240p'`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Dữ liệu ban đầu: `RouteStationRepository.findByRouteIdOrderByStopOrderAsc` result.

#### Cách kiểm chứng

Đọc query ordered ở line 49, `runningSchedule` lines 68-89 và save check-ins lines 91-92.

#### Kết quả

**Mong đợi:** Có schedule data baseline để tái sử dụng.

**Thực tế:** Source có query order, builder scheduled time và `plusSeconds` theo metric chặng.

#### Trạng thái

PASS

#### Lý do trạng thái

Evidence source xác nhận code baseline tồn tại.

#### Ghi chú

- Giới hạn: Không chứng minh runtime timestamp exact.
- Rủi ro còn lại: EVD-010 phải có fixed-Clock test/API result.
- Evidence bổ sung liên quan: EVD-010.

### EVD-004 — Baseline per-stop ETA và gap completion contract

#### Claim

`SimulatorService#calculateEtas` tạo ETA/actual per stop và builder phát `stationsEta`, nhưng `VehicleTelemetryDto` không có `tripStatus`, `etaSecondsToCompletion`, `estimatedCompletionTime`.

#### Liên kết

- Requirement: REQ-002, REQ-003, REQ-005
- Acceptance Criteria: AC-REQ-002-01, AC-REQ-002-02, AC-REQ-003-01
- Spec / Business Rule: SPEC-002, SPEC-003 / BR-002, BR-003, BR-005
- Test Case: TC-003, TC-004, TC-005
- Plan Step: Step 2, Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `SimulatorService#tickSingleSimulation` lines 264-299; `#calculateEtas` lines 304-355; `dto/VehicleTelemetryDto.java` lines 16-36.
- Command/test/API: `rg -n "estimatedCompletion|etaSecondsToCompletion|completionEta|Clock" vehiceltracking-backend/src/main/java vehicletracking-frontend/src`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Dữ liệu ban đầu: SimulationSession và ordered TripCheckIn.

#### Cách kiểm chứng

Đọc calculation/DTO; command search không tìm completion application field.

#### Kết quả

**Mong đợi:** Xác định reusable ETA baseline và contract gap.

**Thực tế:** Code emits `stationsEta` and target ETA; DTO/search không có ba field completion mới.

#### Trạng thái

PASS

#### Lý do trạng thái

Path/symbol/search trực tiếp hỗ trợ Claim.

#### Ghi chú

- Giới hạn: Không chứng minh ETA correctness runtime.
- Rủi ro còn lại: EVD-011/EVD-012 phải assert payload.
- Evidence bổ sung liên quan: EVD-011, EVD-012.

### EVD-005 — Completion auto/manual bị split

#### Claim

Geofencing final path set Trip completed/end time trực tiếp nhưng không update Vehicle; `TripService#completeTrip` set Vehicle `IDLE`/speed 0.

#### Liên kết

- Requirement: REQ-003, REQ-005
- Acceptance Criteria: AC-REQ-003-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-003 / BR-004
- Test Case: TC-005, TC-006
- Plan Step: Step 1, Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `GeofencingService#checkAndProcessAutoCheckIn` lines 96-115; `TripService#completeTrip` lines 104-118.
- Command/test/API: `rg -n -A 9 -B 4 "TripStatus.COMPLETED|setEndTime|setStatus\\(VehicleStatus.IDLE\\)" vehiceltracking-backend/src/main/java`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Dữ liệu ban đầu: final PENDING check-in.

#### Cách kiểm chứng

Đối chiếu direct save Trip in Geofence với Vehicle save in TripService.

#### Kết quả

**Mong đợi:** Có evidence cho shared completion decision.

**Thực tế:** Hai paths có side effect Vehicle khác nhau như Claim.

#### Trạng thái

PASS

#### Lý do trạng thái

Source code trực tiếp chứng minh observation baseline.

#### Ghi chú

- Giới hạn: Không tái hiện concurrent final transition.
- Rủi ro còn lại: EVD-012 critical.
- Evidence bổ sung liên quan: EVD-012.

### EVD-006 — Timeline baseline thiếu fallback/isolation

#### Claim

`TimelinePanel` chỉ dùng `telemetry?.stationsEta || []`; `App#onTelemetry` set incoming telemetry không compare `data.tripId` với `currentTrip`.

#### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01
- Spec / Business Rule: SPEC-004 / BR-006
- Test Case: TC-007, TC-008
- Plan Step: Step 3, Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/components/TimelinePanel.tsx` lines 11-12, 133-221; `App.tsx` lines 82-94.
- Command/test/API: source reads recorded in `survey.md`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Dữ liệu ban đầu: Trip with check-ins, null/different telemetry.

#### Cách kiểm chứng

Trace initializer and telemetry callback from App to Timeline props.

#### Kết quả

**Mong đợi:** Baseline UI gap is evidenced from code.

**Thực tế:** Null telemetry leads empty list; callback accepts any payload.

#### Trạng thái

PASS

#### Lý do trạng thái

Source statements are direct.

#### Ghi chú

- Giới hạn: Không thay manual visual proof.
- Rủi ro còn lại: EVD-013/EVD-014.
- Evidence bổ sung liên quan: EVD-013, EVD-014.

### EVD-007 — Existing Trip read và telemetry boundary

#### Claim

Backend đã có `GET /api/trips/{id}` và frontend đã subscribe `/topic/telemetry`; feature không cần REST endpoint hoặc topic mới.

#### Liên kết

- Requirement: REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-004 / BR-005, BR-006
- Test Case: TC-002, TC-007
- Plan Step: Step 2, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `TripController#getTripById` lines 24-27; `services/websocket.ts` lines 34-40,73-78; `WebSocketConfig.java` lines 14-30.
- Command/test/API: source reads recorded in `survey.md`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Configuration: Existing STOMP simple broker.

#### Cách kiểm chứng

Trace REST controller and producer/client subscription symbols.

#### Kết quả

**Mong đợi:** Existing contracts can be reused.

**Thực tế:** Symbols/endpoints/topic exist as described.

#### Trạng thái

PASS

#### Lý do trạng thái

Repository source supports Claim.

#### Ghi chú

- Giới hạn: Client helper/additive fields not implemented.
- Rủi ro còn lại: EVD-013/EVD-014.
- Evidence bổ sung liên quan: EVD-013, EVD-014.

### EVD-008 — Không có routing/traffic provider application

#### Claim

Repository không có application integration HERE/routing/traffic/directions/OSRM/Mapbox; không cần provider/secret mới cho feature.

#### Liên kết

- Requirement: REQ-002, REQ-005
- Acceptance Criteria: AC-REQ-002-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-002 / BR-002
- Test Case: TC-004
- Plan Step: Step 2
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- Command/test/API: `rg -n -i "here|routing|route api|traffic api|directions|osrm|mapbox" vehiceltracking-backend vehicletracking-frontend --glob '!**/node_modules/**' --glob '!**/dist/**'`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: local repository root.
- Configuration/Dữ liệu ban đầu: Không áp dụng.

#### Cách kiểm chứng

Run command and inspect matches; only Maven wrapper comment appears, not application integration.

#### Kết quả

**Mong đợi:** Không nhầm basemap với routing/traffic provider.

**Thực tế:** Không có match source/config application for the providers searched.

#### Trạng thái

PASS

#### Lý do trạng thái

Search scope covers backend/frontend application source/config.

#### Ghi chú

- Giới hạn: Cannot prove a future diff will not add provider.
- Rủi ro còn lại: Review checks dependency/diff.
- Evidence bổ sung liên quan: EVD-010..EVD-015.

### EVD-009 — Test/command baseline

#### Claim

Baseline chưa có TripService, SimulatorService, GeofencingService hoặc TripController test; frontend không có test script nhưng lint/build scripts tồn tại.

#### Liên kết

- Requirement: REQ-006
- Acceptance Criteria: AC-REQ-006-01
- Spec / Business Rule: SPEC-006 / BR-007
- Test Case: TC-001..TC-011
- Plan Step: Step 4, Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/test/java` list; `vehicletracking-frontend/package.json:scripts`; `AGENTS.md:180-200`.
- Command/test/API: `rg --files vehiceltracking-backend/src/test/java | sort`.
- Commit/worktree: `0cc54fe`.
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh.

#### Môi trường và điều kiện tiên quyết

- Môi trường: local checkout.
- Configuration: Java 26 per Maven property.

#### Cách kiểm chứng

Compare file list to Test-Plan target names and inspect package scripts.

#### Kết quả

**Mong đợi:** Xác định test mới và only valid frontend commands.

**Thực tế:** List contains Geo/Application/Route/Station tests, not ETA targets; package scripts only dev/build/lint/preview.

#### Trạng thái

PASS

#### Lý do trạng thái

Manifest/file list directly support Claim.

#### Ghi chú

- Giới hạn: Not a passing test result.
- Rủi ro còn lại: EVD-010..EVD-015 mandatory after implementation.
- Evidence bổ sung liên quan: EVD-010..EVD-015.

## Evidence implementation đã hoàn thành bởi Gemini

### EVD-010 — Backend schedule/API/full-suite verification

#### Claim

Implementation đáp ứng REQ-001: cumulative schedule và ordered GET Trip hoạt động chính xác; toàn bộ 70 unit/integration tests backend đều pass.

#### Liên kết

- Requirement: REQ-001, REQ-006
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-006-01
- Spec / Business Rule: SPEC-001, SPEC-006 / BR-001, BR-007
- Test Case: TC-001, TC-002, TC-009
- Plan Step: Step 1, Step 4, Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/TripServiceTest.java`, `controller/TripControllerTest.java`, `service/SimulatorServiceTest.java`
- Command/test/API: `export JAVA_HOME=~/.sdkman/candidates/java/current && export PATH=$JAVA_HOME/bin:$PATH && cd vehiceltracking-backend && bash mvnw clean test | tee ../docs/features/003-route-eta/artifacts/mvn-clean-test.log`
- Artifact: `docs/features/003-route-eta/artifacts/mvn-clean-test.log`
- Thời điểm quan sát: 2026-09-04 09:41:05 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26 (OpenJDK 26.0.1) backend local; H2 in-memory dev profile; fixed Clock test fixture.
- Configuration/Dữ liệu ban đầu: TD-001 fixture 3 trạm (start, stop, end) với khoảng cách và thời gian dự kiến.

#### Cách kiểm chứng

Chạy `bash mvnw clean test`, đối chiếu kết quả assertion của `TripServiceTest` và `TripControllerTest` với `TC-001`, `TC-002`.

#### Kết quả

**Mong đợi:** Exit code 0, toàn bộ test suite pass, schedule và API trả về dữ liệu đúng thứ tự và thời gian tích lũy.

**Thực tế:**
- Exit code: 0.
- `Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`.
- `TC-001` (`TripServiceTest#createTrip_GeneratesCumulativeScheduledArrivalTimesAndStartTime`): Kiểm tra stop 1 scheduledArrivalTime = T (10:00:00), stop 2 = T + 2m (10:02:00), stop 3 = T + 5m (10:05:00) với fixed Clock. PASS.
- `TC-002` (`TripControllerTest#getTripById_ReturnsOrderedCheckInsWithScheduledArrivalTime`): MockMvc gọi `GET /api/trips/{id}`, HTTP status 200, check-ins trả về đúng thứ tự stopOrder 1, 2, 3 và scheduledArrivalTime tăng dần. PASS.

#### Trạng thái

PASS

#### Lý do trạng thái

Toàn bộ test suite và các test case trọng tâm cho schedule và API đều thực thi thành công với exit code 0.

#### Ghi chú

- Log chi tiết được lưu tại `docs/features/003-route-eta/artifacts/mvn-clean-test.log`.
- Evidence bổ sung liên quan: EVD-003, EVD-011, EVD-012.

---

### EVD-011 — Dynamic station ETA verification

#### Claim

Implementation đáp ứng REQ-002: ETA từng trạm (pending/checked) được sắp xếp đúng thứ tự stopOrder, tích lũy khoảng cách, không âm/không NaN, phản ánh sự thay đổi vận tốc do sự cố giao thông và có tính tất định với nguồn thời gian Clock.

#### Liên kết

- Requirement: REQ-002, REQ-005
- Acceptance Criteria: AC-REQ-002-01, AC-REQ-002-02
- Spec / Business Rule: SPEC-002 / BR-002, BR-007
- Test Case: TC-003, TC-004
- Plan Step: Step 2, Step 4, Step 5
- Finding: REV-003

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/SimulatorServiceTest.java`
- Command/test/API: `bash mvnw clean test`
- Artifact: `docs/features/003-route-eta/artifacts/mvn-clean-test.log`
- Thời điểm quan sát: 2026-09-04 09:41:05 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26, fixed Clock (Asia/Ho_Chi_Minh), mock repositories.
- Configuration/Dữ liệu ban đầu: TD-002 (trạm 1 CHECKED_IN, trạm 2 & 3 PENDING), TD-003 (incident giảm 60% tốc độ).

#### Cách kiểm chứng

Chạy `SimulatorServiceTest` kiểm tra hàm `calculateEtas` và bản tin telemetry non-terminal:
1. So sánh trạm CHECKED_IN (distance=0, etaSeconds=0, giữ actualArrivalTime) và trạm PENDING (khoảng cách và ETA tích lũy tăng dần, time = now(clock) + etaSeconds).
2. So sánh ETA khi chạy bình thường (40 km/h) và khi qua vùng sự cố (16 km/h).
3. Kiểm tra bản tin telemetry non-terminal: xác minh `etaSecondsToCompletion` và `estimatedCompletionTime` khớp chính xác với ETA của trạm cuối cùng trong lịch trình.

#### Kết quả

**Mong đợi:** Assertion thành công, ETA không âm, không NaN; ETA khi có sự cố lớn hơn khi không có sự cố; completion ETA non-terminal suy dẫn chính xác từ trạm cuối.

**Thực tế:**
- `TC-003` (`SimulatorServiceTest#calculateEtas_CalculatesOrderedCumulativePendingEta_NoNaNOrNegative`): Trạm A có distance=0, ETA=0s; trạm B có distance > 0, ETA > 0; trạm C có distance > B, ETA > B. Không có giá trị NaN hay âm. PASS.
- `TC-004` (`SimulatorServiceTest#calculateEtas_IncidentSpeedReduction_IncreasesEta`): Khi tốc độ giảm từ 40 km/h xuống 16 km/h do sự cố, ETA tới trạm B và trạm C đều tăng tương ứng; thời gian dự kiến đến trạm muộn hơn so với bình thường. PASS.
- `REV-003 / TC-003 / TC-005` (`SimulatorServiceTest#tickSingleSimulation_NonTerminalTick_DerivesCompletionEtaFromFinalStop`): Bản tin telemetry khi đang chạy có `tripStatus = RUNNING`, `status = IN_TRANSIT`, `targetStationId = 2L`, và `etaSecondsToCompletion` khớp chính xác với `etaSeconds` của Trạm C (trạm cuối), `estimatedCompletionTime` khớp với `estimatedArrivalTime` của trạm cuối. PASS.

#### Trạng thái

PASS

#### Lý do trạng thái

Kiểm thử tự động thực thi và khẳng định tính chính xác của thuật toán tính ETA động, ảnh hưởng của sự cố giao thông, và việc suy dẫn completion ETA non-terminal từ trạm cuối.

#### Ghi chú

- Evidence bổ sung liên quan: EVD-004, EVD-010, EVD-012.

---

### EVD-012 — Completion và terminal telemetry verification

#### Claim

Khi xe check-in trạm cuối cùng, hệ thống cập nhật hoàn thành Trip/Vehicle một cách nguyên tử (atomic) và lũy đẳng (idempotent), đồng thời Simulator phát đúng một bản tin telemetry terminal với trạng thái hoàn thành.

#### Liên kết

- Requirement: REQ-003, REQ-005
- Acceptance Criteria: AC-REQ-003-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-003 / BR-003, BR-004, BR-005
- Test Case: TC-005, TC-006
- Plan Step: Step 1, Step 2, Step 4, Step 5
- Finding: REV-001, REV-003

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java`, `TripServiceTest.java`, `SimulatorServiceTest.java`
- Command/test/API: `bash mvnw clean test`
- Artifact: `docs/features/003-route-eta/artifacts/mvn-clean-test.log`
- Thời điểm quan sát: 2026-09-04 09:41:05 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Java 26, fixed Clock, Mockito test framework.
- Configuration/Dữ liệu ban đầu: TD-004 (xe tiếp cận trạm cuối cùng), TD-005 (xe nhảy bước lớn qua các waypoint).

#### Cách kiểm chứng

1. `GeofencingServiceTest`: Khi xe vào bán kính trạm cuối, xác minh trạm cuối được cập nhật CHECKED_IN và ủy nhiệm gọi `tripService.completeTrip(tripId, checkInTime)`.
2. `TripServiceTest`: Gọi `completeTrip` lần đầu tại T1 (Trip chuyển COMPLETED, endTime=T1, Vehicle IDLE/0). Gọi lại lần hai tại T2 > T1 (endTime vẫn là T1, không ghi đè, không lưu thừa).
3. `SimulatorServiceTest`:
   - Trong tick cuối cùng khi tất cả đã CHECKED_IN, SimulatorSession chuyển `completed = true`, phát bản tin telemetry với `status = IDLE`, `tripStatus = COMPLETED`, `etaSecondsToCompletion = 0`, `targetStationId = null`. Tick kế tiếp không phát thêm dữ liệu.
   - Khi xe chạm waypoint cuối nhưng còn trạm PENDING (REV-001), session KHÔNG kết thúc sớm, không phát terminal telemetry mà tiếp tục duy trì trạng thái `RUNNING`.
   - Khi tăng tốc độ (multiplier cao) nhảy qua nhiều waypoint trong một tick, hệ thống duyệt và kích hoạt geofence check cho tất cả các waypoint trung gian đã đi qua.

#### Kết quả

**Mong đợi:** Toàn bộ flow auto check-in -> completion -> terminal telemetry diễn ra nhất quán, lũy đẳng và an toàn trước các trường hợp nhảy bước hoặc pending check-in.

**Thực tế:**
- `GeofencingServiceTest#checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceCompleteTrip`: Gọi `tripService.completeTrip(100L, fixedNow)`. PASS.
- `TripServiceTest#completeTrip_IsIdempotentAndPreservesInitialCompletionTime`: Lần gọi thứ hai giữ nguyên endTime T1, không save lại. PASS.
- `SimulatorServiceTest#tickSingleSimulation_TerminalTick_EmitsTerminalTelemetryOnce`: Telemetry có `status = IDLE`, `tripStatus = COMPLETED`, `etaSecondsToCompletion = 0L`, `session.isCompleted() = true`. Tick sau không gọi thêm messagingTemplate. PASS.
- `REV-001 / TC-005` (`SimulatorServiceTest#tickSingleSimulation_ReachingEndWaypointWithPendingStops_DoesNotEmitTerminalTelemetry`): Khi xe bước tới waypoint cuối nhưng còn trạm PENDING, `session.isCompleted()` là `false`, telemetry phát ra có `tripStatus = RUNNING`, `status = IN_TRANSIT`, `etaSecondsToCompletion > 0`. PASS.
- `REV-001 / TC-005` (`SimulatorServiceTest#tickSingleSimulation_MultiStepAdvance_ChecksAllIntermediateWaypoints`): Nhảy từ waypoint 0 tới waypoint 3 kích hoạt `checkAndProcessAutoCheckIn` lần lượt cho waypoint 1, 2 và 3 đầy đủ. PASS.

#### Trạng thái

PASS

#### Lý do trạng thái

Cả năm khía cạnh (geofencing delegation, completion idempotency, terminal telemetry capture, pending at end waypoint protection, multi-step intermediate waypoint checking) đều được kiểm chứng độc lập bằng unit tests và đều đạt kết quả mong đợi.

#### Ghi chú

- Evidence bổ sung liên quan: EVD-005, EVD-010, EVD-011.

---

### EVD-013 — Manual Timeline fallback/live/final verification

#### Claim

Người dùng quan sát thấy: lịch trình dự kiến hiển thị trước khi chạy mô phỏng (fallback schedule), ETA động từng trạm và thời gian về đích cập nhật theo thời gian thực khi xe di chuyển, giao diện chốt trạng thái hoàn thành khi xe về trạm cuối, và kiểm chứng Trip Isolation: telemetry/check-in của Trip khác hoàn toàn bị bỏ qua, không làm sai lệch UI chuyến đi đang chọn (có đầy đủ script gửi, sender log STOMP, browser console log và screenshot chứng minh delivery và rejection thực tế).

#### Liên kết

- Requirement: REQ-004, REQ-006
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-006-01
- Spec / Business Rule: SPEC-004, SPEC-006 / BR-006
- Test Case: TC-007
- Plan Step: Step 3, Step 5
- Finding: REV-002, REV-003, REV-004, REV-005

#### Loại Evidence

**Loại:** `MANUAL`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/App.tsx`, `components/TimelinePanel.tsx`
- Command/test/API:
  - Script gửi STOMP độc lập: `export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH && node docs/features/003-route-eta/artifacts/send-foreign-telemetry.js | tee docs/features/003-route-eta/artifacts/tc007-sender-foreign-telemetry.log`
  - Browser console capture và audit tự động qua browser subagent tại `http://localhost:5173/`.
- Artifact:
  - Script kiểm thử ngoài: `docs/features/003-route-eta/artifacts/send-foreign-telemetry.js`
  - Log gửi STOMP: `docs/features/003-route-eta/artifacts/tc007-sender-foreign-telemetry.log`
  - Log nhận và bỏ qua tại Browser Console: `docs/features/003-route-eta/artifacts/tc007-browser-isolation.log`
  - `docs/features/003-route-eta/artifacts/tc007_01_fallback_schedule_before_start.png`
  - `docs/features/003-route-eta/artifacts/tc007_02_dynamic_eta_simulation_running.png`
  - `docs/features/003-route-eta/artifacts/tc007_03_terminal_completion_state.png`
  - `docs/features/003-route-eta/artifacts/tc007_04_trip_isolation_other_trip_ignored.png`
- Thời điểm quan sát: 2026-09-04 11:10:00 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Backend Spring Boot chạy trên port 8080; Frontend Vite dev server chạy trên port 5173.
- Dữ liệu ban đầu: Tuyến số 01 với 5 trạm dừng từ Bến xe Miền Đông đến Chợ Bến Thành; Trip khởi tạo ở trạng thái RUNNING, các check-in ở trạng thái PENDING.

#### Cách kiểm chứng

1. Truy cập `http://localhost:5173/`, chụp màn hình TimelinePanel trước khi nhấn Khởi Hành.
2. Chọn tốc độ 5x, nhấn "Khởi Hành", chờ xe di chuyển và chụp màn hình TimelinePanel hiển thị ETA động.
3. Chờ xe đến trạm cuối cùng (Chợ Bến Thành), chụp màn hình trạng thái hoàn thành chuyến đi.
4. Chạy script ngoài `docs/features/003-route-eta/artifacts/send-foreign-telemetry.js` kết nối STOMP tới `ws://localhost:8080/ws-raw`, gửi payload của Trip khác (`tripId: 99999`, biển số `99X-99999`, vận tốc `99 km/h`, góc quay `180°`). Kiểm tra output sender log, browser console log của dashboard, và chụp màn hình xác minh UI của Trip 1 không bị thay đổi.

#### Kết quả

**Mong đợi:** Giao diện chuyển đổi mượt mà giữa fallback schedule -> live ETA -> terminal completed; payload Trip khác được gửi thành công, dashboard nhận được và ghi log bỏ qua, UI của Trip hiện tại hoàn toàn không bị ảnh hưởng.

**Thực tế:**
1. **Fallback Schedule trước Khởi Hành (`tc007_01_fallback_schedule_before_start.png`):** Header hiển thị "LỊCH TRÌNH CHUYẾN ĐI", banner "LỊCH VỀ ĐÍCH: 09:34:43", tất cả 5 trạm đều hiển thị "Lịch trình dự kiến" với các mốc giờ 09:19:49, 09:23:43, 09:28:07, 09:31:43, 09:34:43. Không bị trắng hay trống bảng.
2. **Dynamic ETA khi đang chạy mô phỏng (`tc007_02_dynamic_eta_simulation_running.png`):** Header chuyển sang chấm xanh "TRẠNG THÁI XE TRỰC TUYẾN", đồng hồ vận tốc hiển thị 40 km/h, góc quay 201°, banner "DỰ KIẾN VỀ ĐÍCH: 09:34:43 • ETA HOÀN THÀNH: 15p 0s", trạm mục tiêu kế tiếp hiển thị cự ly ("Cách ...m") và ETA động ("ETA: ..."), trạm đã qua hiển thị dấu tick xanh "Đã check-in".
3. **Terminal Completion khi kết thúc (`tc007_03_terminal_completion_state.png`):** Xe dừng lại tại trạm cuối, vận tốc về 0 km/h, header hiển thị chấm xanh dương "CHUYẾN ĐI HOÀN THÀNH", banner xanh lá "ĐÃ VỀ ĐÍCH" với badge "HOÀN THÀNH", pháo hoa confetti kích hoạt, toàn bộ 5 trạm đều có dấu tick xanh "Đã check-in".
4. **Trip Isolation Delivery & Rejection (`tc007-sender-foreign-telemetry.log`, `tc007-browser-isolation.log`, `tc007_04_trip_isolation_other_trip_ignored.png`):**
   - **Sender Log:** Script `send-foreign-telemetry.js` kết nối STOMP broker tại `ws://localhost:8080/ws-raw`, nhận `CONNECTED`, gửi frame `SEND /topic/telemetry` chứa payload `tripId: 99999`, biển số `99X-99999`, vận tốc 99 km/h, và đóng kết nối sạch sẽ với exit code 0.
   - **Browser Console Log:** Dashboard nhận message realtime và bộ lọc `App.tsx` ghi nhận: `[REALTIME ISOLATION] Bỏ qua telemetry của Trip khác: payload.tripId=99999, activeTrip.id=1`, chứng minh 100% payload đã được delivery tới dashboard và bị filter bỏ qua chính xác.
   - **Screenshot UI:** Giao diện active trip (Trip 1) hoàn toàn độc lập và không đổi: biển số hiển thị vẫn là `51B-299.88`, vận tốc vẫn giữ nguyên `0 km/h`, góc quay `0°`, lịch trình các trạm giữ nguyên.

#### Trạng thái

PASS

#### Lý do trạng thái

Toàn bộ 4 kịch bản kiểm thử (fallback schedule, dynamic ETA, terminal completion, và Trip isolation) đều được kiểm chứng thực tế và có artifact đầy đủ (script, sender log, browser console log, screenshot độ phân giải cao), giải quyết triệt để REV-002, REV-003, REV-004 và REV-005.

#### Ghi chú

- Toàn bộ artifacts và script kiểm thử độc lập được lưu trữ trực tiếp trong thư mục artifacts của feature.
- Evidence bổ sung liên quan: EVD-010, EVD-011, EVD-012.

---

### EVD-014 — Frontend lint/type compatibility verification

#### Claim

Các bổ sung trường tùy chọn trong `VehicleTelemetry`, hàm `getTripById` và cập nhật logic Timeline/App tuân thủ đầy đủ TypeScript type check và linter, không phát sinh bất kỳ warning hay lỗi mới nào so với baseline.

#### Liên kết

- Requirement: REQ-005, REQ-006
- Acceptance Criteria: AC-REQ-005-01, AC-REQ-006-01
- Spec / Business Rule: SPEC-005, SPEC-006 / BR-005, BR-006
- Test Case: TC-008, TC-010
- Plan Step: Step 3, Step 5
- Finding: REV-003, REV-006

#### Loại Evidence

**Loại:** `TYPE_CHECK`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/types/index.ts`, `App.tsx`, `components/TimelinePanel.tsx`, `services/api.ts`
- Command/test/API: `export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH && cd vehicletracking-frontend && (npm run lint && npx tsc --noEmit && npm run build) 2>&1 | tee ../docs/features/003-route-eta/artifacts/frontend-verification.log`
- Artifact: `docs/features/003-route-eta/artifacts/frontend-verification.log`
- Thời điểm quan sát: 2026-09-04 11:10:27 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Node v24.16.0, npm 10.9.2, TypeScript 5.9.3.
- Configuration/Dữ liệu ban đầu: Source code frontend sạch, không chứa test hook trong production, logic matching telemetry được tính toán trực tiếp thay vì gọi setState trong effect.

#### Cách kiểm chứng

Chạy `(npm run lint && npx tsc --noEmit && npm run build) 2>&1` và lưu log đầy đủ. Kiểm tra mã thoát (exit code) và đối chiếu chi tiết từng warning với baseline.

#### Kết quả

**Mong đợi:** Exit code 0, log ghi lại đầy đủ output của cả lint, type-check và build, không có lỗi kiểu dữ liệu hoặc warning lint mới.

**Thực tế:**
- `npm run lint` (oxlint): 0 errors, đúng 4 warnings. Cả 4 warnings này đều là baseline có từ trước feature 003:
  1. `eslint(no-unused-vars)` tại `IncidentModal.tsx:26:18` (biến `setRadius` chưa dùng — baseline).
  2. `eslint(no-unused-vars)` tại `MapComponent.tsx:18:3` (tham số `route` chưa dùng — baseline).
  3. `react-hooks(exhaustive-deps)` tại `MapComponent.tsx:202:6` (thiếu `vehicleTelemetry` trong deps — baseline).
  4. `react(set-state-in-effect)` tại `App.tsx:83:5` (gọi `loadInitialData()` trong effect khởi tạo — baseline).
  *Warning mới từng xuất hiện ở `App.tsx:29` do gọi `setTelemetry` trong effect đã được loại bỏ triệt để sau khi chuyển sang derive `matchingTelemetry` trực tiếp trong quá trình render; do đó feature 003 phát sinh đúng 0 warning mới (giải quyết triệt để REV-006).*
- `npx tsc --noEmit`: Exit code 0, không phát hiện bất kỳ lỗi TypeScript nào.
- `npm run build` (vite build): Exit code 0, build thành công trong 397ms (dist/index.html 1.75 kB, CSS 5.08 kB, JS 442.21 kB).
- Toàn bộ output của 3 command đều được lưu đầy đủ trong `frontend-verification.log`.

#### Trạng thái

PASS

#### Lý do trạng thái

Quá trình type-check và lint hoàn tất với exit code 0, phân định chính xác 4 warning baseline và 0 warning mới, đảm bảo tính an toàn kiểu và tuân thủ linter.

#### Ghi chú

- Evidence bổ sung liên quan: EVD-013, EVD-015.

---

### EVD-015 — Frontend build và diff hygiene verification

#### Claim

Bản build production của frontend thành công trọn vẹn, diff source code trong git không có lỗi khoảng trắng (whitespace error) và toàn bộ file sửa đổi đều thuộc danh sách file đã phê duyệt trong Plan (đã hoàn trả websocket.ts về nguyên trạng, không có thay đổi ngoài phạm vi).

#### Liên kết

- Requirement: REQ-006
- Acceptance Criteria: AC-REQ-006-01
- Spec / Business Rule: SPEC-006
- Test Case: TC-011
- Plan Step: Step 3, Step 5
- Finding: REV-004

#### Loại Evidence

**Loại:** `BUILD`

#### Nguồn

- File/symbol: Toàn bộ git worktree của feature 003
- Command/test/API: `cd vehicletracking-frontend && npm run build` và `git diff --check`
- Artifact: `docs/features/003-route-eta/artifacts/frontend-verification.log`
- Thời điểm quan sát: 2026-09-04 11:00:45 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: Node v24.16.0, Vite v8.2.2, git CLI.
- Configuration/Dữ liệu ban đầu: Worktree hoàn chỉnh sau khi xử lý triệt để finding REV-004 (loại bỏ hook trong `websocket.ts`).

#### Cách kiểm chứng

Chạy `npm run build` kiểm tra Vite output. Chạy `git diff --check` kiểm tra định dạng diff. Chạy `git status --short` xác nhận chỉ các file thuộc Plan mới có thay đổi.

#### Kết quả

**Mong đợi:** `npm run build` exit code 0; `git diff --check` exit code 0, không có cảnh báo whitespace; danh sách file sửa đổi đúng 100% với file list của Plan.

**Thực tế:**
- `npm run build`: Exit code 0, built in 474ms.
- `git diff --check`: Exit code 0, không có bất kỳ dòng nào vi phạm định dạng hay lỗi khoảng trắng.
- `git status --short`: `vehicletracking-frontend/src/services/websocket.ts` đã được khôi phục nguyên trạng về baseline; chỉ các file trong Plan Step 1, 2, 3 được sửa đổi.

#### Trạng thái

PASS

#### Lý do trạng thái

Cả lệnh build production và lệnh kiểm tra diff hygiene đều hoàn thành sạch sẽ không có lỗi, danh sách file thay đổi hoàn toàn khớp với Plan.

#### Ghi chú

- Evidence bổ sung liên quan: EVD-010..EVD-014.

---

## Evidence bị thay thế

Không có.

## Evidence còn thiếu

Không có — Toàn bộ `EVD-010` đến `EVD-015` đã được thực thi và xác minh thực tế, chuyển trạng thái thành `PASS`.

## Checklist bàn giao cho Review

- [x] Mọi Requirement có Evidence Matrix row và đều đạt `PASS`.
- [x] Research source có URL thật; Survey Claim có path/command thật.
- [x] Không có secret/API key trong code, log hay tài liệu.
- [x] Không có PASS suy đoán cho implementation; tất cả đều có command output hoặc artifact thật.
- [x] Gemini đã cập nhật đầy đủ EVD-010..EVD-015 bằng actual result và artifacts.
- [ ] Codex kiểm tra diff/Evidence và chạy lại verification quan trọng trước khi đưa ra kết luận review.

