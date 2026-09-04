# Evidence — 004-automatic-station-checkin

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `004-automatic-station-checkin` |
| Requirement/Spec/Test-Plan/Plan version | 2026-09-04 |
| Implementation được kiểm chứng | Đã implement theo Plan và giải quyết toàn bộ Review Findings REV-001..REV-004; GeofencingService, SimulatorService, GeofencingServiceTest, SimulatorServiceTest |
| Trạng thái tài liệu | `COMPLETED — Toàn bộ Verification và Evidence đã hoàn tất` |
| Người thu thập | Gemini |
| Người kiểm tra | Chờ Codex Review |
| Ngày cập nhật | 2026-09-04 Asia/Ho_Chi_Minh |

## Mục đích

Tài liệu này phân biệt Claim với Evidence có thể kiểm chứng. EVD-001..EVD-010 là evidence baseline từ Research/Survey và verification command hiện trạng. EVD-011..EVD-021 là evidence implementation thực tế từ test unit, regression suite, build, lint và browser verification.

## Evidence Matrix

| Requirement | Spec / Business Rule | Test Case | Implementation | Evidence | Status |
|---|---|---|---|---|---|
| `REQ-001` | `SPEC-001 / BR-001, BR-002, BR-005` | `TC-001, TC-002` | `GeofencingService#checkAndProcessAutoCheckIn` đo `<= radiusMeters`, kiểm tra exact boundary (`distance == radius`) và outside (`distance > radius`), cập nhật `CHECKED_IN` và `actualArrivalTime` từ `Clock` | `EVD-011, EVD-012` | `PASS` |
| `REQ-002` | `SPEC-002 / BR-001, BR-003` | `TC-003, TC-004` | Query first PENDING theo `stopOrder`, không skip trạm, không save/publish trùng lặp khi gọi lặp | `EVD-013, EVD-014` | `PASS` |
| `REQ-003` | `SPEC-003 / BR-004` | `TC-005, TC-006` | `SimulatorService#tickSingleSimulation` kiểm tra START (index 0) trước khi cập nhật `currentWaypointIndex`; duyệt tuần tự mọi waypoint nhảy | `EVD-015, EVD-016` | `PASS` |
| `REQ-004` | `SPEC-004 / BR-005, BR-006, BR-008` | `TC-007, TC-008` | Persist trước publish, payload đúng schema, frontend filter `tripId` hiển thị toast trạm hợp lệ và cô lập hoàn toàn foreign event (có stimulus script, broker confirmation log foreign-checkin-stimulus.log, 5 screenshots trước-sau và video WebP) | `EVD-017, EVD-018` | `PASS` |
| `REQ-005` | `SPEC-005 / BR-002, BR-005, BR-007` | `TC-009, TC-010, TC-011` | Defensive validation cho station/vehicle coordinates (null, NaN, Infinity, out of range), radius (null, NaN, <30, >150), multi-session continuation sau no-op, trạm cuối ủy nhiệm `completeTrip`, full backend 82/82 test pass và frontend clean | `EVD-019, EVD-020, EVD-021` | `PASS` |

## Coverage Summary

| Requirement | Critical? | Evidence PASS | Evidence FAIL | Evidence INCONCLUSIVE | Kết luận hiện tại |
|---|---|---|---|---|---|
| `REQ-001` | Có | EVD-011, EVD-012 | Không có | Không có | PASS |
| `REQ-002` | Có | EVD-013, EVD-014 | Không có | Không có | PASS |
| `REQ-003` | Có | EVD-015, EVD-016 | Không có | Không có | PASS |
| `REQ-004` | Có | EVD-017, EVD-018 | Không có | Không có | PASS |
| `REQ-005` | Có | EVD-019, EVD-020, EVD-021 | Không có | Không có | PASS |

## Evidence từ Research và Survey

### EVD-001 — Baseline commit và worktree

#### Claim

`Research/Survey feature 004 được lập trên commit 41154d4 với worktree sạch trước khi tạo tài liệu.`

#### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: Không áp dụng — baseline
- Spec / Business Rule: SPEC-001..SPEC-005
- Test Case: Không áp dụng
- Plan Step: Tất cả step
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `LOG`

#### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: `git rev-parse --short HEAD`; `git status --short`
- Commit/worktree: `41154d4`; status không output
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local repository root.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Worktree trước khi tạo feature 004.

#### Cách kiểm chứng

```bash
git rev-parse --short HEAD
git status --short
```

#### Kết quả

**Mong đợi:** Có hash baseline và trạng thái worktree tái lập được.

**Thực tế:** Hash `41154d4`; `git status --short` không có output.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Hai command read-only đã chạy và xác nhận baseline trước khi tạo docs feature.

#### Ghi chú

- Giới hạn: Sau Gemini implementation commit/worktree sẽ thay đổi; Evidence phải cập nhật.
- Rủi ro còn lại: Không dùng EVD này để chứng minh runtime behavior.
- Evidence bổ sung liên quan: EVD-002..EVD-010.

### EVD-002 — GeofencingService baseline

#### Claim

`GeofencingService#checkAndProcessAutoCheckIn` hiện query PENDING đầu theo stopOrder, so sánh distance với radius, lưu CHECKED_IN/actualArrivalTime, phát event và gọi completion khi hết PENDING.`

#### Liên kết

- Requirement: REQ-001, REQ-002, REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-004-01, AC-REQ-005-02
- Spec / Business Rule: SPEC-001, SPEC-002, SPEC-004, SPEC-005 / BR-001, BR-002, BR-005..BR-007
- Test Case: TC-001, TC-007, TC-010
- Plan Step: Step 1
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/GeofencingService.java#checkAndProcessAutoCheckIn`, lines 40-121
- Command/test/API: `nl -ba .../GeofencingService.java | sed -n '1,240p'`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: source checkout local.
- Configuration: `Clock` bean hiện có, không ghi giá trị secret.
- Dữ liệu ban đầu: source code baseline.

#### Cách kiểm chứng

Đọc query lines 43-44, distance/compare lines 50-63, publish lines 71-96 và completion lines 98-115.

#### Kết quả

**Mong đợi:** Xác định phần code hiện có để tái sử dụng và gap cần test.

**Thực tế:** Các symbols nêu trên tồn tại; source không đủ chứng minh boundary/order/duplicate runtime. Nó cũng chưa kiểm tra waypoint START index 0.

#### Trạng thái

`PASS`

#### Lý do trạng thái

PASS chỉ cho Claim về source baseline tồn tại, không phải PASS cho Requirement.

#### Ghi chú

- Giới hạn: Không chứng minh behavior runtime hoặc concurrency.
- Rủi ro còn lại: Cần EVD test sau implementation.
- Evidence bổ sung liên quan: EVD-008, EVD-011..EVD-015 dự kiến.

### EVD-003 — GeoUtil distance baseline

#### Claim

`Repository có GeoUtil.calculateDistanceMeters trả khoảng cách theo mét bằng Haversine và có GeoUtilTest kiểm tra khoảng cách địa lý.`

#### Liên kết

- Requirement: REQ-001, REQ-005
- Acceptance Criteria: AC-REQ-001-01/02
- Spec / Business Rule: SPEC-001 / BR-002
- Test Case: TC-001, TC-002
- Plan Step: Step 1
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `.../util/GeoUtil.java#calculateDistanceMeters`, lines 5-20; `.../GeoUtilTest.java`, lines 10-20
- Command/test/API: `nl -ba GeoUtil.java`; `nl -ba GeoUtilTest.java`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: Không áp dụng.
- Dữ liệu ban đầu: Hai tọa độ fixture trong GeoUtilTest.

#### Cách kiểm chứng

Đọc hàm và test; chạy `GeoUtilTest` trong full Maven suite khi dependencies khả dụng.

#### Kết quả

**Mong đợi:** Có phép đo mét tái sử dụng.

**Thực tế:** Hàm tồn tại và test assert khoảng cách khoảng 1.4 km; baseline full suite chưa chạy được trong môi trường khảo sát.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source/test path trực tiếp hỗ trợ Claim repository-local.

#### Ghi chú

- Giới hạn: Haversine là khoảng cách đường chim bay, không chứng minh map matching.
- Rủi ro còn lại: Input NaN/infinity cần defensive validation.
- Evidence bổ sung liên quan: EVD-007, EVD-011, EVD-012 dự kiến.

### EVD-004 — TripCheckIn persistence/query baseline

#### Claim

`TripCheckIn` có status, stopOrder, actualArrivalTime và quan hệ Trip/Station; repository có query lấy PENDING đầu theo stopOrder.`

#### Liên kết

- Requirement: REQ-001, REQ-002, REQ-004
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-002-01/02
- Spec / Business Rule: SPEC-002 / BR-001, BR-003
- Test Case: TC-003, TC-004
- Plan Step: Step 1
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `.../entity/TripCheckIn.java`, lines 23-44; `.../repository/TripCheckInRepository.java`, lines 12-15
- Command/test/API: `nl -ba TripCheckIn.java`; `nl -ba TripCheckInRepository.java`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: H2/PostgreSQL mapping hiện có.
- Dữ liệu ban đầu: entity/repository source.

#### Cách kiểm chứng

Đọc field/annotation và method `findFirstByTripIdAndStatusOrderByStopOrderAsc`.

#### Kết quả

**Mong đợi:** Xác định source of truth và query có thể tái sử dụng.

**Thực tế:** Field/query tồn tại; source chưa chứng minh gọi lặp/concurrency không duplicate.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Evidence đủ cho claim về data model/query tồn tại.

#### Ghi chú

- Giới hạn: Không có unique constraint trực tiếp trip/station hoặc distributed lock trong entity/repository hiện tại.
- Rủi ro còn lại: Scope chỉ yêu cầu idempotency tuần tự.
- Evidence bổ sung liên quan: EVD-013, EVD-014 dự kiến.

### EVD-005 — Simulator waypoint baseline và START gap

#### Claim

`SimulatorService` tạo waypoint tại từng station và gọi geofence cho các waypoint từ currentIndex+1 tới nextIndex, nhưng waypoint station ở index 0 không được gọi trước movement trong source baseline.`

#### Liên kết

- Requirement: REQ-003
- Acceptance Criteria: AC-REQ-003-01/02
- Spec / Business Rule: SPEC-003 / BR-004
- Test Case: TC-005, TC-006
- Plan Step: Step 2
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `.../service/SimulatorService.java#tickSingleSimulation`, lines 187-266; `#generateDetailedWaypoints`, lines 440-483
- Command/test/API: `nl -ba SimulatorService.java | sed -n '160,280p;437,485p'`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: simulator scheduler hiện có.
- Dữ liệu ban đầu: RouteStation ordered.

#### Cách kiểm chứng

Đối chiếu builder waypoint đầu lines 448-455 với geofence loop lines 249-266; kiểm tra `currentWaypointIndex(0)` ở start.

#### Kết quả

**Mong đợi:** Tìm xem simulator có cung cấp vị trí START vào geofence hay không.

**Thực tế:** Waypoint START được tạo tại index đầu, nhưng tick baseline chỉ gọi `currentIndex + 1..nextIndex`; test hiện tại cũng đặt checkInA CHECKED_IN.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Evidence trực tiếp xác nhận source và gap; không phải claim rằng REQ-003 hiện đã PASS.

#### Ghi chú

- Giới hạn: Chưa có test mới cho START PENDING.
- Rủi ro còn lại: Nếu không sửa, START có thể chặn mọi station sau.
- Evidence bổ sung liên quan: EVD-016, EVD-017 dự kiến.

### EVD-006 — Realtime/frontend contract baseline

#### Claim

`Frontend subscribe /topic/checkins, parse CheckInEvent và App chỉ tạo toast khi event.tripId khớp currentTrip.id.`

#### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01/02
- Spec / Business Rule: SPEC-004 / BR-006, BR-008
- Test Case: TC-007, TC-008
- Plan Step: Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/services/websocket.ts`, lines 42-48; `src/App.tsx`, lines 126-138; `src/types/index.ts#CheckInEvent`, lines 134-144
- Command/test/API: `nl -ba websocket.ts`; `nl -ba App.tsx`; `nl -ba types/index.ts`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: frontend source checkout.
- Configuration: native STOMP endpoint hiện có.
- Dữ liệu ban đầu: TypeScript event contract.

#### Cách kiểm chứng

Đọc subscription, callback filter và `CheckInEvent` fields.

#### Kết quả

**Mong đợi:** Xác định có thể tái sử dụng frontend contract hay cần API mới.

**Thực tế:** Contract/filter/toast tồn tại trong source; chưa có browser evidence cho event thật của feature 004.

#### Trạng thái

`PASS`

#### Lý do trạng thái

PASS chỉ cho source contract baseline.

#### Ghi chú

- Giới hạn: Không chứng minh broker delivery/UI runtime.
- Rủi ro còn lại: Manual TC-008 cần artifact hoặc phải giữ INCONCLUSIVE.
- Evidence bổ sung liên quan: EVD-019 dự kiến.

### EVD-007 — Station coordinate/radius validation baseline

#### Claim

`StationDto` và `StationService#validateStationDto` giới hạn latitude/longitude hợp lệ và radius 30–150 mét, đồng thời kiểm tra finite ở service.`

#### Liên kết

- Requirement: REQ-001, REQ-005
- Acceptance Criteria: AC-REQ-005-01
- Spec / Business Rule: SPEC-001, SPEC-005 / BR-002
- Test Case: TC-009
- Plan Step: Step 1
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `.../dto/StationDto.java`, lines 31-50; `.../service/StationService.java#validateStationDto`, lines 186-208
- Command/test/API: `nl -ba StationDto.java`; `nl -ba StationService.java | sed -n '186,208p'`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source checkout.
- Configuration: không áp dụng.
- Dữ liệu ban đầu: DTO/service validation source.

#### Cách kiểm chứng

Đọc annotation `DecimalMin/DecimalMax` và các điều kiện `Double.isFinite`/range.

#### Kết quả

**Mong đợi:** Xác định invariant cho station data để geofence reuse.

**Thực tế:** Validation tạo mới/cập nhật có các giới hạn nêu trên; GeofencingService hiện chưa có defensive validation tương tự cho vehicle input.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Evidence đủ cho claim về validation source hiện có.

#### Ghi chú

- Giới hạn: Không bảo vệ record legacy hoặc vị trí xe trực tiếp.
- Rủi ro còn lại: Step 1 phải xử lý invalid input an toàn.
- Evidence bổ sung liên quan: EVD-015 dự kiến.

### EVD-008 — Existing test coverage gap

#### Claim

`Backend test hiện có test final geofence/completion và simulator multi-waypoint, nhưng chưa có đầy đủ test cho START index 0, boundary/outside, order/skip, repeat và invalid input.`

#### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: Tất cả AC
- Spec / Business Rule: SPEC-001..SPEC-005 / BR-001..BR-007
- Test Case: TC-001..TC-011
- Plan Step: Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `.../service/GeofencingServiceTest.java#checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceCompleteTrip`; `.../service/SimulatorServiceTest.java#tickSingleSimulation_MultiStepAdvance_ChecksAllIntermediateWaypoints`
- Command/test/API: `rg -n "@DisplayName|void " .../GeofencingServiceTest.java .../SimulatorServiceTest.java`
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local test source.
- Configuration: JUnit/Mockito dependency theo `pom.xml`.
- Dữ liệu ban đầu: test fixtures hiện có.

#### Cách kiểm chứng

Đọc `@DisplayName` và test methods; đối chiếu test fixture đặt start check-in `CHECKED_IN`.

#### Kết quả

**Mong đợi:** Xác định coverage baseline trước khi lập Test-Plan.

**Thực tế:** `GeofencingServiceTest` hiện tập trung final station; `SimulatorServiceTest` có multi-step nhưng fixture station đầu đã CHECKED_IN; các case yêu cầu mới chưa có.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source test trực tiếp hỗ trợ claim coverage gap.

#### Ghi chú

- Giới hạn: Không suy luận test runtime chưa chạy.
- Rủi ro còn lại: Critical acceptance hiện INCONCLUSIVE.
- Evidence bổ sung liên quan: EVD-011..EVD-018 dự kiến.

### EVD-009 — Frontend baseline verification

#### Claim

`Frontend lint/build commands hiện có thể chạy trong môi trường khảo sát với kết quả thực tế đã ghi; lint còn 4 warning baseline.`

#### Liên kết

- Requirement: REQ-004, REQ-005
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-005-01
- Spec / Business Rule: SPEC-004, SPEC-005
- Test Case: TC-008, TC-011
- Plan Step: Step 4, Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `BUILD`

#### Nguồn

- File/symbol: `vehicletracking-frontend/package.json` scripts `lint`, `build`
- Command/test/API: `npm run lint`; `/home/khainq/.nvm/versions/node/v24.16.0/bin/node node_modules/vite/bin/vite.js build`
- Artifact: Chưa lưu artifact baseline; kết quả terminal tại phiên khảo sát
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: frontend local; lint exit 0; Vite build dùng Node 24.16.0.
- Configuration: không ghi env/secret.
- Dữ liệu ban đầu: source hiện tại.

#### Cách kiểm chứng

```bash
cd vehicletracking-frontend
npm run lint
/home/khainq/.nvm/versions/node/v24.16.0/bin/node node_modules/vite/bin/vite.js build
```

#### Kết quả

**Mong đợi:** Command script tồn tại và baseline có thể tái lập.

**Thực tế:** `npm run lint` exit 0, 4 warning baseline và 0 error; Vite build exit 0, 1831 modules transformed.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Các command đã thực sự chạy; claim chỉ về baseline command/frontend build, không về feature 004 behavior.

#### Ghi chú

- Giới hạn: Chưa có frontend test script và chưa có manual event evidence.
- Rủi ro còn lại: Warning phải được phân loại nếu source frontend thay đổi.
- Evidence bổ sung liên quan: EVD-019, EVD-020 dự kiến.

### EVD-010 — Backend baseline verification bị block

#### Claim

`Backend full test chưa tạo được kết quả test trong môi trường khảo sát vì wrapper permission và Maven dependency/network, nên không được coi là PASS.`

#### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: Tất cả AC
- Spec / Business Rule: SPEC-001..SPEC-005
- Test Case: TC-011
- Plan Step: Step 3, Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `vehiceltracking-backend/mvnw`, `pom.xml`
- Command/test/API: `./mvnw test`; `bash ./mvnw test`
- Artifact: Chưa lưu artifact baseline; terminal output phiên khảo sát
- Commit/worktree: `41154d4`
- Thời điểm quan sát: 2026-09-04 Asia/Ho_Chi_Minh

#### Môi trường và điều kiện tiên quyết

- Môi trường: local sandbox, network tới Maven Central không resolve được.
- Configuration: Không ghi datasource password/token.
- Dữ liệu ban đầu: backend source hiện tại.

#### Cách kiểm chứng

```bash
cd vehiceltracking-backend
./mvnw test
bash ./mvnw test
```

#### Kết quả

**Mong đợi:** Backend full suite exit 0.

**Thực tế:** `./mvnw test` exit 126 (`Permission denied`); `bash ./mvnw test` exit 1 vì không resolve `spring-boot-starter-parent:4.1.1` do DNS/network tạm thời không khả dụng.

#### Trạng thái

`INCONCLUSIVE`

#### Lý do trạng thái

Không có test assertion output; theo workflow, command không chạy được phải là INCONCLUSIVE.

#### Ghi chú

- Giới hạn: Không kết luận code backend pass/fail từ blocker môi trường.
- Rủi ro còn lại: Gemini cần chạy lại khi dependencies khả dụng.
- Evidence bổ sung liên quan: EVD-018 dự kiến.

## Evidence từ Implementation

### EVD-011 — In-geofence transition và timestamp fixed Clock (TC-001)

#### Claim

`Khi vị trí xe nằm trong bán kính geofence (khoảng cách <= radiusMeters) của trạm kế tiếp đang PENDING, hệ thống chuyển trạng thái sang CHECKED_IN, gán actualArrivalTime theo Clock, lưu database và phát CheckInEvent.`

#### Liên kết

- Requirement: REQ-001
- Acceptance Criteria: AC-REQ-001-01
- Spec / Business Rule: SPEC-001 / BR-001, BR-002, BR-005
- Test Case: TC-001
- Plan Step: Step 1, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_InGeofence_TransitionsToCheckedInAndRecordsTime`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_InGeofence_TransitionsToCheckedInAndRecordsTime test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Record chuyển `CHECKED_IN`, `actualArrivalTime` bằng `fixedNow`, `save` được gọi 1 lần, phát event `/topic/checkins`.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-012 — Boundary và outside radius behavior (TC-002)

#### Claim

`Xe nằm ở exact boundary (cự ly đúng bằng bán kính: distance == radius ~88.94m) check-in thành công; xe nằm ngoài boundary (cự ly distance > radius: distance == radius + 0.5m) trả về empty, giữ PENDING và không mutate/publish.`

#### Liên kết

- Requirement: REQ-001, REQ-005
- Acceptance Criteria: AC-REQ-001-01, AC-REQ-001-02, AC-REQ-005-01
- Spec / Business Rule: SPEC-001, SPEC-005 / BR-002
- Test Case: TC-002
- Plan Step: Step 1, Step 3
- Finding: Đã giải quyết REV-002

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_BoundaryWithinRadius_TransitionsSuccessfully_OutsideRadius_NoOp`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_BoundaryWithinRadius_TransitionsSuccessfully_OutsideRadius_NoOp test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Exact boundary cự ly đúng bằng bán kính (`distance == radius` ~88.94m) chuyển `CHECKED_IN`, save = 1, event = 1; outside cự ly lớn hơn bán kính (`distance > radius`, `radius = distance - 0.5m`) trả về empty, giữ PENDING, save = 0, event = 0.
**Thực tế:** Test PASS, exit code 0. Log maven ghi nhận `AUTO CHECK-IN: Xe 51B-11111 đã vào trạm Trạm Bến Thành (cự ly: 89m, bán kính trạm: 88.95594131563719m)` và assert thành công nhánh `<=`.

#### Trạng thái

`PASS`

---

### EVD-013 — Không bỏ qua trạm sau - Order preservation (TC-003)

#### Claim

`Khi trạm 1 đang PENDING mà xe di chuyển tới vị trí của trạm 2, hệ thống chỉ đánh giá trạm 1 theo stopOrder và không check-in trạm 2, không làm xáo trộn thứ tự lộ trình.`

#### Liên kết

- Requirement: REQ-002
- Acceptance Criteria: AC-REQ-002-01
- Spec / Business Rule: SPEC-002 / BR-001
- Test Case: TC-003
- Plan Step: Step 1, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_PreservesStopOrder_DoesNotSkipPendingStation`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_PreservesStopOrder_DoesNotSkipPendingStation test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Trả về empty, trạm 1 và trạm 2 đều giữ PENDING, save = 0, event = 0.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-014 — Idempotency tuần tự khi gọi lặp (TC-004)

#### Claim

`Gọi liên tục nhiều lần tại cùng một trạm chỉ ghi nhận transition và phát event duy nhất 1 lần; các lần gọi sau khi đã CHECKED_IN là no-op an toàn.`

#### Liên kết

- Requirement: REQ-002
- Acceptance Criteria: AC-REQ-002-02
- Spec / Business Rule: SPEC-002 / BR-003
- Test Case: TC-004
- Plan Step: Step 1, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_RepeatedCalls_DoesNotCreateDuplicateTransitionsOrEvents`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_RepeatedCalls_DoesNotCreateDuplicateTransitionsOrEvents test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Lần 1 trả về checked-in; lần 2 trả về empty; tổng số lần save = 1, event = 1.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-015 — Simulator tick kiểm tra waypoint 0 (START) trước khi di chuyển (TC-005)

#### Claim

`Ở tick đầu tiên khi currentIndex == 0, SimulatorService gọi geofencing check cho waypoint 0 (trạm START) trước khi session.setCurrentWaypointIndex(nextIndex) được cập nhật. Nếu geofence START gặp ngoại lệ, session index không bị tăng và giữ nguyên tại 0.`

#### Liên kết

- Requirement: REQ-003
- Acceptance Criteria: AC-REQ-003-01
- Spec / Business Rule: SPEC-003 / BR-004
- Test Case: TC-005
- Plan Step: Step 2, Step 3
- Finding: Đã giải quyết REV-001

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `SimulatorServiceTest.java#tickSingleSimulation_AtWaypointIndexZero_ChecksStartStationBeforeMovement`; `SimulatorServiceTest.java#tickSingleSimulation_WhenStartGeofenceThrowsException_SessionIndexRemainsAtZero`
- Command: `bash ./mvnw -Dtest=SimulatorServiceTest#tickSingleSimulation_AtWaypointIndexZero_ChecksStartStationBeforeMovement,SimulatorServiceTest#tickSingleSimulation_WhenStartGeofenceThrowsException_SessionIndexRemainsAtZero test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** DoAnswer assertion xác nhận `session.getCurrentWaypointIndex() == 0` tại thời điểm geofencingService được gọi với wp0; wp0 được gọi trước wp1; sau khi tick hoàn tất index mới là 1; khi geofence START ném exception, index vẫn giữ nguyên 0 không bị tăng trước.
**Thực tế:** Cả 2 tests PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-016 — Duyệt tuần tự mọi waypoint trung gian khi bước nhảy lớn (TC-006)

#### Claim

`Khi speed multiplier cao làm xe nhảy nhiều bước (từ index 0 tới 3), SimulatorService duyệt tuần tự tất cả các waypoint 0, 1, 2, 3 theo thứ tự tăng dần mà không bỏ sót.`

#### Liên kết

- Requirement: REQ-003
- Acceptance Criteria: AC-REQ-003-02
- Spec / Business Rule: SPEC-003 / BR-004
- Test Case: TC-006
- Plan Step: Step 2, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `SimulatorServiceTest.java#tickSingleSimulation_MultiStepAdvance_ChecksAllIntermediateWaypointsInOrder`
- Command: `bash ./mvnw -Dtest=SimulatorServiceTest#tickSingleSimulation_MultiStepAdvance_ChecksAllIntermediateWaypointsInOrder test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Mockito InOrder xác nhận các cuộc gọi `checkAndProcessAutoCheckIn` theo đúng thứ tự wp0 -> wp1 -> wp2 -> wp3.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-017 — Persist trước khi publish và event payload đầy đủ (TC-007)

#### Claim

`Hệ thống luôn gọi tripCheckInRepository.save trước khi bắn event sang /topic/checkins; payload CheckInEventDto chứa đầy đủ tripId, tripCode, vehicleId, plateNumber, stationId, stationName, stopOrder, checkInTime.`

#### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01
- Spec / Business Rule: SPEC-004 / BR-005, BR-006
- Test Case: TC-007
- Plan Step: Step 1, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_PersistBeforePublish_AndEventPayloadMatches`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_PersistBeforePublish_AndEventPayloadMatches test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Mockito InOrder verify save trước publish; ArgumentCaptor assert chính xác từng trường payload.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-018 — Frontend nhận đúng Trip và cô lập foreign event (TC-008)

#### Claim

`Frontend dashboard hiển thị toast thông báo khi nhận CheckInEvent khớp với currentTrip.id; foreign event (tripId khác) phát từ script độc lập được broker broadcast đến /topic/checkins và được callback onCheckIn tại App.tsx bỏ qua một cách an toàn (event.tripId !== currentTrip.id thì return im lặng), hoàn toàn không tạo toast, không thay đổi timeline và không ảnh hưởng đến chuyến đi hiện hành.`

#### Liên kết

- Requirement: REQ-004
- Acceptance Criteria: AC-REQ-004-01, AC-REQ-004-02
- Spec / Business Rule: SPEC-004 / BR-008
- Test Case: TC-008
- Plan Step: Step 4
- Finding: Đã giải quyết REV-004

#### Loại Evidence

**Loại:** `UI | REALTIME`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/App.tsx#unsubCheckIn` (xử lý lọc sự kiện: `if (!activeTrip || event.tripId !== activeTrip.id) return;`)
- Stimulus script: `docs/features/004-automatic-station-checkin/artifacts/send-foreign-checkin-test.mjs` (kết nối STOMP client tới `ws://localhost:8080/ws-raw`, subscribe `/topic/checkins` để xác minh broadcast delivery và publish `CheckInEvent` mang `tripId: 999999` tới `/topic/checkins`)
- Verification: Phiên kiểm thử phối hợp giữa Browser automation và STOMP stimulus script độc lập.
- Artifacts:
  - `docs/features/004-automatic-station-checkin/artifacts/send-foreign-checkin-test.mjs` (Source code script phát foreign stimulus)
  - `docs/features/004-automatic-station-checkin/artifacts/foreign-checkin-stimulus.log` (Transcript execution log của script: xác nhận kết nối WebSocket, xác nhận gửi payload tripId 999999, xác nhận subscriber nhận được broadcast từ broker với exit code 0)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-01-initial-pending.png` (Trạng thái ban đầu: chuyến đi hiện hành TRIP-646791, 5 trạm trên timeline đều ở trạng thái PENDING)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-02-foreign-trip-isolated.png` (Trạng thái ngay sau khi stimulus script phát foreign check-in tripId 999999: giao diện hoàn toàn không xuất hiện toast nào, timeline 5 trạm vẫn giữ nguyên PENDING, chuyến đi hiện hành không bị ảnh hưởng)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-03-stop1-autocheckin-toast.png` (Khởi động mô phỏng cho chuyến đi hiện hành: ngay tại START index 0, xe kích hoạt auto check-in trạm 1, toast "Auto Check-in Trạm #1" hiển thị góc phải và trạm 1 trên timeline chuyển sang màu xanh CHECKED_IN)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-04-stop2-autocheckin-toast.png` (Xe di chuyển tới trạm 2: toast "Auto Check-in Trạm #2" hiển thị và trạm 2 trên timeline chuyển sang màu xanh CHECKED_IN)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-05-completed-all-stops.png` (Xe hoàn thành toàn bộ lộ trình: tất cả 5 trạm trên timeline đều hiển thị CHECKED_IN và chuyến đi đạt trạng thái COMPLETED)
  - `docs/features/004-automatic-station-checkin/artifacts/tc008-autocheckin-demo.webp` (Video animation ghi hình toàn bộ quá trình kiểm thử từ lúc cô lập foreign event cho đến khi hoàn thành lộ trình)

#### Kết quả

**Mong đợi:** Script phát foreign event thành công và nhận confirmation từ broker; frontend nhận event và bỏ qua an toàn vì khác tripId (không hiển thị toast, không mutate timeline); khi chạy simulation của trip hiện hành, toast Auto Check-in hiển thị chính xác theo từng trạm (#1, #2...) và cập nhật timeline tương ứng.
**Thực tế:** Script `send-foreign-checkin-test.mjs` chạy với exit code 0, ghi nhận đầy đủ trong `foreign-checkin-stimulus.log`; UI frontend không sinh bất kỳ toast nào cho trip lạ, 5 trạm giữ nguyên PENDING; khi bắt đầu mô phỏng, toast Auto Check-in Trạm #1 và Trạm #2 hiển thị chính xác và kết thúc lộ trình đầy đủ 5 trạm CHECKED_IN.

#### Trạng thái

`PASS`

---

### EVD-019 — Defensive handling: Invalid inputs và no-pending an toàn không mutate (TC-009)

#### Claim

`Các dữ liệu đầu vào không hợp lệ (tripId null/<=0, vehicle lat/lng NaN/Infinity/ngoài biên, station null, station lat/lng null/NaN/Infinity/ngoài biên, station radius null/NaN/<30/>150, no-pending) đều được reject với warning log, trả về Optional.empty và không gây mutation/event/exception; một session gặp geofence no-op không làm gián đoạn việc thực thi session tiếp theo.`

#### Liên kết

- Requirement: REQ-005
- Acceptance Criteria: AC-REQ-005-01
- Spec / Business Rule: SPEC-005 / BR-002
- Test Case: TC-009
- Plan Step: Step 1, Step 3
- Finding: Đã giải quyết REV-003

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_InvalidInputsAndNoPending_SafelyReturnsEmptyWithoutMutation`; `SimulatorServiceTest.java#tickSingleSimulation_SessionNoOpDoesNotBlockSubsequentSessionExecution`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_InvalidInputsAndNoPending_SafelyReturnsEmptyWithoutMutation,SimulatorServiceTest#tickSingleSimulation_SessionNoOpDoesNotBlockSubsequentSessionExecution test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Tất cả các trường hợp invalid vehicle lat/lng, station null, station lat/lng null/NaN/Infinity/out-of-range, station radius null/NaN/<30/>150, no-pending đều trả về Optional.empty, không mutate DB hay publish event; session no-op không làm ảnh hưởng session khác.
**Thực tế:** Cả 2 tests PASS, exit code 0. Log maven ghi nhận đầy đủ warning log cho từng tọa độ lỗi của station và vehicle; test multi-session verify độc lập telemetry cho cả 2 sessions.

#### Trạng thái

`PASS`

---

### EVD-020 — Trạm cuối ủy nhiệm hoàn thành chuyến đi cho TripService cùng mốc timestamp (TC-010)

#### Claim

`Khi trạm cuối cùng trong lịch trình check-in thành công và không còn trạm nào PENDING, hệ thống ủy nhiệm hoàn thành chuyến đi cho TripService#completeTrip với cùng timestamp fixed Clock như actualArrivalTime và event checkInTime.`

#### Liên kết

- Requirement: REQ-005
- Acceptance Criteria: AC-REQ-005-02
- Spec / Business Rule: SPEC-005 / BR-005, BR-007
- Test Case: TC-010
- Plan Step: Step 1, Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `GeofencingServiceTest.java#checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceWithSameTimestamp`
- Command: `bash ./mvnw -Dtest=GeofencingServiceTest#checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceWithSameTimestamp test`
- Artifact: `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`

#### Kết quả

**Mong đợi:** Trạm cuối chuyển CHECKED_IN, phát event, gọi `tripService.completeTrip(100L, fixedNow)` chính xác 1 lần.
**Thực tế:** Test PASS, exit code 0.

#### Trạng thái

`PASS`

---

### EVD-021 — Full regression suite, lint, typecheck và diff hygiene (TC-011)

#### Claim

`Toàn bộ test suite backend (82/82 tests) pass không lỗi; frontend lint 0 lỗi, tsc type-check pass, vite build pass; git diff --check sạch không có lỗi whitespace/hygiene.`

#### Liên kết

- Requirement: REQ-001..REQ-005
- Acceptance Criteria: Tất cả AC
- Spec / Business Rule: SPEC-001..SPEC-005
- Test Case: TC-011
- Plan Step: Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST | BUILD | LINT | TYPE_CHECK`

#### Nguồn

- Command 1: `bash ./mvnw test` tại `vehiceltracking-backend` -> 82 tests run, 0 failures, 0 errors.
- Command 2: `npm run lint` tại `vehicletracking-frontend` -> 0 errors, 4 baseline warnings.
- Command 3: `npx tsc --noEmit` tại `vehicletracking-frontend` -> exit code 0.
- Command 4: `node node_modules/vite/bin/vite.js build` tại `vehicletracking-frontend` -> exit code 0, 1831 modules transformed.
- Command 5: `git diff --check` tại repository root -> exit code 0, 0 output.
- Artifacts:
  - `docs/features/004-automatic-station-checkin/artifacts/mvn-test.log`
  - `docs/features/004-automatic-station-checkin/artifacts/frontend-verification.log`
  - `docs/features/004-automatic-station-checkin/artifacts/diff-check.log`

#### Kết quả

**Mong đợi:** Tất cả các lệnh exit code 0.
**Thực tế:** Toàn bộ lệnh kiểm tra thực tế đều đạt exit code 0 (82/82 backend tests, 0 lint errors, tsc clean, vite build clean, git diff clean).

#### Trạng thái

`PASS`

## Evidence bị thay thế

| Evidence cũ | Evidence thay thế | Lý do | Ngày |
|---|---|---|---|
| EVD-008 (coverage gap baseline) | EVD-011..EVD-020 | Đã bổ sung đầy đủ test unit và runtime verification | 2026-09-04 |
| EVD-010 (backend baseline bị block) | EVD-021 | Đã giải quyết dependency/runtime, toàn bộ 82 test backend pass | 2026-09-04 |

## Evidence còn thiếu

| Requirement/Claim | Evidence cần có | Lý do chưa có | Trạng thái | Hành động tiếp theo |
|---|---|---|---|---|
| Không còn evidence thiếu | — | Tất cả Requirement REQ-001..REQ-005 đã có Evidence thực tế đạt PASS | PASS | Bàn giao cho Codex Review |

## Checklist bàn giao cho Review

- [x] Mọi Requirement quan trọng có dòng trong Evidence Matrix và đạt trạng thái PASS.
- [x] Mọi EVD đều có Claim cụ thể, liên kết REQ/Spec/TC/Plan, loại, nguồn, cách kiểm chứng và kết quả thực tế.
- [x] Kết quả thực tế được ghi nhận từ execution logs và artifacts thực tế, không dự đoán.
- [x] Không có PASS nào cho implementation hoặc test chưa chạy.
- [x] Không sửa `review.md`, không tự review hoặc tự approve.
- [x] Artifacts đã được lưu trữ không chứa secret/token/password.
- [x] Sẵn sàng bàn giao cho Codex Review theo đúng GEMINI.md.
