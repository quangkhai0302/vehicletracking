# Review — 004-automatic-station-checkin

## Thông tin review

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `004-automatic-station-checkin` |
| Reviewer | `Codex` |
| Implementer | `Gemini` |
| Thời điểm | 2026-09-04 14:35 Asia/Ho_Chi_Minh |
| Commit/diff được review | Baseline `41154d4`; worktree chưa commit gồm 4 file source/test backend và tài liệu/artifact feature 004 |
| Requirement/Spec/Test-Plan/Plan/Evidence version | 2026-09-04 |
| Lần review | 3 — re-review sau khi Gemini xử lý REV-004 |

## Kết luận

**Kết luận hiện tại:** `APPROVED`

**Lý do ngắn:** REV-001..REV-003 vẫn giữ trạng thái `RESOLVED`. REV-004 nay có transcript STOMP thực tế chứng minh handshake, publish payload `tripId=999999` và broker broadcast lại đúng payload; source `onCheckIn` bỏ qua event khác trip trước khi tạo toast; ảnh before/after và matching-flow nhất quán với hành vi này. `REQ-004 / AC-REQ-004-02` đã có đường Evidence đủ mạnh để PASS. Không phát hiện regression hoặc finding bắt buộc mới.

## Phạm vi và phương pháp review

- Đã đọc: `AGENTS.md`, `docs/workflow.md`, phản hồi bàn giao của Gemini và toàn bộ tám tài liệu trong `docs/features/004-automatic-station-checkin/`.
- Đã kiểm tra `git status --short`, toàn bộ `git diff` của `GeofencingService.java`, `SimulatorService.java`, `GeofencingServiceTest.java`, `SimulatorServiceTest.java`; không có source frontend, schema, dependency hoặc configuration ngoài Plan bị thay đổi.
- Đã đối chiếu Evidence Matrix và `EVD-001..EVD-021` với source/test/log/screenshot/WebP thực tế.
- Đã xem trực tiếp năm screenshot; WebP là animation thật với 113 frame. Artifact matching-trip cho thấy timeline/check-in/toast hoạt động, nhưng artifact foreign-trip không chứa bằng chứng payload đã được gửi.
- Đã tự chạy lại backend `clean test` bằng Java 26 ngoài sandbox vì Mockito/Byte Buddy cần attach agent; lần chạy re-review đạt 82/82 test pass. Đã tự chạy lại frontend lint, type-check, Vite build và `git diff --check`.
- Không sửa source code. File duy nhất Codex cập nhật là `review.md` này.

## Findings (lần 1 — trạng thái mới nhất nằm trong phần Re-review)

### REV-001 — Session đã được tăng index trước khi START được check-in

**Severity:** `HIGH`

**File:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/SimulatorService.java`

**Vị trí:** `tickSingleSimulation`, lines 246-257

**Requirement:** `REQ-003 / AC-REQ-003-01`

**Spec/Business Rule:** `SPEC-003 / BR-004`; Plan Step 2 mục 1

**Test:** `TC-005`

**Evidence:** `EVD-015` không hỗ trợ đầy đủ Claim tại line 764 của `evidence.md`

**Vấn đề:**

`session.setCurrentWaypointIndex(nextIndex)` được gọi trước `checkAndProcessAutoCheckIn` của `currentWp`. Điều này trái Plan (“gọi geofence cho currentWp trước khi tăng index”) và Claim EVD-015 (“trước khi tăng index di chuyển”). Test hiện chỉ dùng `InOrder` giữa lời gọi wp0 và wp1 nên vẫn pass dù index đã thay đổi trước cả hai lời gọi. Fixture của test cũng để `checkInA` ở `CHECKED_IN`, không đáp ứng precondition START PENDING trong TC-005.

**Cách tái hiện/Bằng chứng:**

1. Đọc `SimulatorService.java`: line 247 đặt index mới; lines 250-257 mới gọi geofence cho START.
2. Đọc `SimulatorServiceTest.java` lines 418-455: assertion chỉ kiểm tra thứ tự hai invocation của mock; không kiểm tra index tại thời điểm invocation.
3. Full suite vẫn pass vì test không quan sát sai lệch này.

**Ảnh hưởng:**

Ở luồng bình thường, wp0 vẫn được gửi trước wp1. Nhưng nếu check-in START ném exception, session đã tiến sang waypoint sau; tick kế tiếp không còn đi qua nhánh `currentIndex == 0`, nên START có thể không được retry và chặn thứ tự check-in còn lại. Đây cũng là sai lệch trực tiếp với thứ tự implementation đã được duyệt.

**Đề xuất:**

Di chuyển việc cập nhật `currentWaypointIndex` xuống sau khi geofence đã xử lý tuần tự `currentWp` và đoạn `(currentIndex, nextIndex]`. Cập nhật TC-005 để START là PENDING và dùng `Answer`/assertion phù hợp xác nhận index vẫn là 0 khi wp0 được gửi; nên thêm case geofence lỗi để chứng minh session không âm thầm bỏ START.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái sau re-review lần 2:** `RESOLVED` — xem EVD-015 và bảng trạng thái re-review.

### REV-002 — TC-002 không kiểm tra điểm đúng biên radius

**Severity:** `MEDIUM`

**File:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`

**Vị trí:** `checkAndProcessAutoCheckIn_BoundaryWithinRadius_TransitionsSuccessfully_OutsideRadius_NoOp`, lines 170-209

**Requirement:** `REQ-001 / AC-REQ-001-01, AC-REQ-001-02`

**Spec/Business Rule:** `SPEC-001 / BR-002 / EC-001`

**Test:** `TC-002`, test data `TD-002`

**Evidence:** `EVD-012`

**Vấn đề:**

Điểm được gọi là “boundary” thực tế cách tâm khoảng 88,9 m trong khi radius là 100 m. Assertion chỉ chứng minh một điểm bên trong (`distance < radius`), không chứng minh nhánh bằng đúng biên (`distance == radius`) mà TC-002/TD-002 yêu cầu. `EVD-012` vì vậy đang ghi PASS rộng hơn bằng chứng thực tế.

**Cách tái hiện/Bằng chứng:**

1. Test đặt `boundaryLat = station.latitude + 0.0008` và giữ radius 100 m.
2. Log Maven xác nhận cự ly được xử lý là 89 m.
3. Không có assertion `distance == radius`; source dùng `<=` nhưng test không bảo vệ toán tử này khỏi regression thành `<`.

**Ảnh hưởng:**

Một thay đổi từ `<=` sang `<` vẫn có thể làm toàn bộ test pass, trong khi vi phạm Acceptance Criteria “đúng biên geofence”.

**Đề xuất:**

Tính một khoảng cách fixture hợp lệ rồi đặt `station.radiusMeters` bằng chính giá trị khoảng cách đó, hoặc tạo dữ liệu tương đương có tolerance rõ ràng; verify điểm bằng biên check-in và điểm chỉ lớn hơn biên không check-in. Sau đó cập nhật EVD-012 theo command/artifact thực tế.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái sau re-review lần 2:** `RESOLVED` — xem EVD-012 và bảng trạng thái re-review.

### REV-003 — TC-009 và EVD-019 bỏ sót invalid station coordinate và session-continuation

**Severity:** `MEDIUM`

**File:** `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/GeofencingServiceTest.java`; `SimulatorServiceTest.java`; `docs/features/004-automatic-station-checkin/evidence.md`

**Vị trí:** `GeofencingServiceTest` lines 298-347; không có test tương ứng trong `SimulatorServiceTest`; `evidence.md` lines 910-942

**Requirement:** `REQ-005 / AC-REQ-005-01`

**Spec/Business Rule:** `SPEC-005 / BR-002 / EC-007`

**Test:** `TC-009`

**Evidence:** `EVD-019`

**Vấn đề:**

TC-009 yêu cầu kiểm tra station coordinate lỗi và chạy một session khác sau một geofence no-op. Test hiện có kiểm tra vehicle coordinate, station null, radius lỗi và no-pending, nhưng không tạo station latitude/longitude null, non-finite hoặc ngoài range; cũng không có simulator test với hai session/no-op. Dù source defensive validation có tồn tại, EVD-019 gắn toàn bộ TC-009 và ghi PASS cho các behavior chưa được chạy.

**Cách tái hiện/Bằng chứng:**

1. Đối chiếu Test-Plan TC-009 steps/expected result với test lines 298-347.
2. `rg` trong backend tests không tìm thấy case `tickAllSimulations` hoặc case session thứ hai sau no-op.
3. Full suite 80/80 chỉ xác nhận các assertions hiện có, không bổ sung assertions còn thiếu.

**Ảnh hưởng:**

REQ-005 còn INCONCLUSIVE đối với dữ liệu tọa độ trạm legacy lỗi và yêu cầu scheduler không làm mất session khác. Evidence Matrix hiện tạo cảm giác coverage đầy đủ sai thực tế.

**Đề xuất:**

Bổ sung invalid station latitude/longitude cases và test scheduler/session continuation đúng như TC-009. Không cần thêm abstraction production nếu có thể kiểm thử bằng fixture/reflection/package-private helper hiện có. Cập nhật EVD-019 chỉ PASS sau khi command thật chạy; nếu chưa thể test session flow, giữ phần đó INCONCLUSIVE.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái sau re-review lần 2:** `RESOLVED` — xem EVD-019 và bảng trạng thái re-review.

### REV-004 — Artifact không chứng minh foreign CheckInEvent đã được phát

**Severity:** `MEDIUM`

**File:** `docs/features/004-automatic-station-checkin/evidence.md`; `docs/features/004-automatic-station-checkin/artifacts/`

**Vị trí:** `EVD-018`, lines 868-906

**Requirement:** `REQ-004 / AC-REQ-004-02`

**Spec/Business Rule:** `SPEC-004 / BR-008`

**Test:** `TC-008`

**Evidence:** `EVD-006`, `EVD-018`

**Vấn đề:**

Source `App.tsx` có filter đúng, nhưng screenshot “foreign-trip-isolated” chỉ cho thấy UI không đổi; không có sender script, browser/STOMP log, payload, trip hiện hành hoặc timestamp chứng minh foreign event `tripId=999999` thực sự đã được phát trong khoảng before/after. Một ảnh UI không đổi mà không có bằng chứng stimulus không đủ chứng minh negative case. Mô tả artifact cũng không khớp hoàn toàn: `tc008-03-stop1-autocheckin-toast.png` không còn toast trong frame, còn `tc008-04-stop2-autocheckin-toast.png` hiển thị toast “Trạm #3”, không phải #2.

**Cách tái hiện/Bằng chứng:**

1. Xem trực tiếp năm PNG và animation 113 frame.
2. Tìm toàn bộ feature artifacts: không có log/script ghi payload foreign hoặc output của lần gửi.
3. EVD-018 chỉ tự khẳng định “Browser automation session với STOMP payload phát trực tiếp”, không ghi command/steps có thể tái lập.

**Ảnh hưởng:**

AC-REQ-004-02 là MUST nhưng vẫn INCONCLUSIVE ở runtime. Source review hỗ trợ Claim, song không thay thế manual realtime test đã định nghĩa trong Test-Plan.

**Đề xuất:**

Lưu artifact có thể đối chiếu: payload foreign đã redact, current trip ID, log xác nhận gửi/nhận, và ảnh/trạng thái trước-sau cùng một phiên. Có thể dùng script STOMP nhỏ trong `artifacts/` hoặc browser console transcript; không cần sửa frontend nếu verification pass. Sửa mô tả/tên artifact cho đúng nội dung thực tế và cập nhật EVD-018 trung thực.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái sau re-review lần 3:** `RESOLVED` — `foreign-checkin-stimulus.log`, source callback và artifacts UI đã được Codex đối chiếu.

## Kiểm tra Requirement (snapshot lần 1 — xem Traceability sau re-review để có kết quả hiện hành)

| Requirement/AC | Spec/Test | Implementation liên quan | Evidence | Kết quả |
|---|---|---|---|---|
| `REQ-001 / AC-REQ-001-01/02` | `SPEC-001; TC-001/002` | `GeofencingService#checkAndProcessAutoCheckIn` | EVD-011; EVD-012 chỉ chứng minh inside/outside, chưa exact boundary | `INCONCLUSIVE` |
| `REQ-002 / AC-REQ-002-01/02` | `SPEC-002; TC-003/004` | Query first PENDING, transition theo state | EVD-013, EVD-014; Codex rerun suite | `PASS` |
| `REQ-003 / AC-REQ-003-01/02` | `SPEC-003; TC-005/006` | `SimulatorService#tickSingleSimulation` | EVD-015 không chứng minh before-index-update; EVD-016 PASS | `FAIL` — REV-001 |
| `REQ-004 / AC-REQ-004-01/02` | `SPEC-004; TC-007/008` | save/event backend; filter `App.tsx` | EVD-017 PASS; EVD-018 foreign runtime chưa đủ | `INCONCLUSIVE` — REV-004 |
| `REQ-005 / AC-REQ-005-01/02` | `SPEC-005; TC-009/010/011` | defensive validation/completion | EVD-020/EVD-021 PASS; EVD-019 thiếu coverage | `INCONCLUSIVE` — REV-003 |

## Kiểm tra Spec (snapshot lần 1 — xem Traceability sau re-review để có kết quả hiện hành)

| Spec/BR/API/Event | Implementation | Evidence | Kết quả | Finding nếu có |
|---|---|---|---|---|
| `SPEC-001 / BR-001, BR-002` | Chọn first PENDING, validate, `distance <= radius` | EVD-011/012 | `INCONCLUSIVE` cho exact boundary | REV-002 |
| `SPEC-002 / BR-003` | Chỉ query PENDING; sequential repeat không save/event record cũ | EVD-013/014 | `PASS` | Không có |
| `SPEC-003 / BR-004` | Gọi wp0 rồi các waypoint tiếp theo, nhưng index được tăng trước wp0 | EVD-015/016 + source diff | `FAIL` | REV-001 |
| `SPEC-004 / BR-005, BR-006` | Save trước `/topic/checkins`, payload dùng fixed timestamp | EVD-017; Codex rerun | `PASS` | Không có |
| `SPEC-004 / BR-008` | Frontend source filter `event.tripId` đúng | EVD-006; manual foreign artifact thiếu stimulus | `INCONCLUSIVE` runtime | REV-004 |
| `SPEC-005 / BR-007` | Final transition gọi `TripService#completeTrip` cùng timestamp | EVD-020; Codex rerun | `PASS` | Không có |
| `SPEC-005` invalid/session flow | Service có defensive code; test chưa đủ matrix/session | EVD-019 | `INCONCLUSIVE` | REV-003 |
| API/schema/dependency compatibility | Không đổi API, schema, dependency hoặc config | diff/status | `PASS` | Không có |

## Kiểm tra Test-Plan (snapshot lần 1 — xem Traceability sau re-review để có kết quả hiện hành)

| Test Case | Test implementation | Evidence | Kết quả | Gap/Finding |
|---|---|---|---|---|
| `TC-001` | `...InGeofence_TransitionsToCheckedInAndRecordsTime` | EVD-011 + rerun | `PASS` | Không có |
| `TC-002` | `...BoundaryWithinRadius...OutsideRadius...` | EVD-012 + rerun | `INCONCLUSIVE` | Điểm “boundary” là 89 m/100 m — REV-002 |
| `TC-003` | `...PreservesStopOrder...` | EVD-013 + rerun | `PASS` | Không có |
| `TC-004` | `...RepeatedCalls...` | EVD-014 + rerun | `PASS` | Sequential scope đúng Spec |
| `TC-005` | `...AtWaypointIndexZero...` | EVD-015 + source diff | `FAIL` | Assertion không bắt index update trước call; fixture START không PENDING — REV-001 |
| `TC-006` | `...MultiStepAdvance...InOrder` | EVD-016 + rerun | `PASS` | Không có |
| `TC-007` | `...PersistBeforePublish_AndEventPayloadMatches` | EVD-017 + rerun | `PASS` | Source call order phù hợp; event-before-commit là giới hạn đã ghi trong Requirement |
| `TC-008` | Manual browser artifacts | EVD-018 | `INCONCLUSIVE` | Không chứng minh foreign payload đã gửi — REV-004 |
| `TC-009` | Invalid/no-pending service test | EVD-019 + rerun | `INCONCLUSIVE` | Thiếu invalid station coordinate và session continuation — REV-003 |
| `TC-010` | `...FinalStation_DelegatesToTripServiceWithSameTimestamp` | EVD-020 + rerun | `PASS` | Không có |
| `TC-011` | Full backend/frontend/diff commands | EVD-021 + Codex rerun | `PASS` | 80/80 backend; frontend 0 error/4 warning baseline |

## Kiểm tra Plan (snapshot lần 1 — xem Traceability sau re-review để có kết quả hiện hành)

| Plan step | Trạng thái | Sai lệch | Đã được chấp thuận? | Ảnh hưởng |
|---|---|---|---|---|
| Step 1 — Geofence transition | `PARTIAL` | Core implementation đúng; boundary/invalid matrix Evidence chưa đủ | Không | REQ-001/005 chưa đủ PASS |
| Step 2 — START/waypoint | `PARTIAL` | Index tăng trước START geofence, trái thứ tự Plan | Không | REQ-003 FAIL |
| Step 3 — Backend tests | `PARTIAL` | Suite pass nhưng TC-002/005/009 assertions/precondition thiếu | Không | Evidence overclaim |
| Step 4 — Realtime/UI | `PARTIAL` | Matching flow có artifact; foreign stimulus không tái lập được | Không | AC-REQ-004-02 INCONCLUSIVE |
| Step 5 — Verification/Evidence | `PARTIAL` | Commands pass; Evidence Matrix đánh PASS cho coverage chưa đủ | Không | Chưa thể approve |

## Kiểm tra Evidence (snapshot lần 1 — xem Traceability sau re-review để có kết quả hiện hành)

- [x] EVD-001..EVD-021 đều tồn tại và Matrix không tham chiếu ID không tồn tại.
- [x] Codex đã đối chiếu source, test, diff, logs và UI artifacts.
- [x] Backend/full frontend verification quan trọng đã được chạy lại độc lập khi có thể.
- [x] Research/Survey dùng source repository cụ thể; không phát hiện citation giả.
- [x] Không phát hiện secret/token/password trong diff hoặc artifact được review.
- [ ] EVD-012 chứng minh đúng điểm bằng radius.
- [ ] EVD-015 chứng minh START được xử lý trước khi tăng index.
- [ ] EVD-018 có stimulus/log realtime đủ tái lập foreign-event case.
- [ ] EVD-019 bao phủ đầy đủ TC-009.
- [ ] Evidence Matrix phản ánh PASS/INCONCLUSIVE đúng với evidence thực tế.
- [ ] Mọi Requirement MUST hết FAIL/INCONCLUSIVE trước khi approve.

## Kiểm tra chất lượng và kiến trúc (snapshot lần 1 — xem Re-review để có kết quả hiện hành)

- [ ] Correctness/business logic hoàn chỉnh: còn REV-001.
- [ ] Happy path và edge case đầy đủ: còn REV-002/REV-003.
- [x] Validation implementation có finite/range checks rõ ràng.
- [x] Không thêm endpoint, credential hoặc log secret.
- [x] Query/performance giữ scope nhỏ, không external call.
- [x] Sequential idempotency và `@Transactional` giữ đúng scope; distributed concurrency nằm ngoài feature.
- [x] REST/WebSocket payload không có breaking change.
- [ ] Frontend runtime isolation đủ evidence: còn REV-004; source filter/cleanup phù hợp.
- [x] Không có dependency, schema, configuration hay refactor ngoài Plan.
- [x] Constructor injection và cấu trúc service/test giữ convention repository.

## Kiểm tra Regression (snapshot lần 1 — lần chạy mới nhất nằm trong phần Re-review)

| Khu vực | Command/Test | Kết quả | Ghi chú |
|---|---|---|---|
| Backend clean full suite | Java 26 + `bash ./mvnw clean test` | `PASS` | Codex rerun: 80 test, 0 failure/error |
| Frontend lint | `npm run lint` | `PASS` | 0 error, 4 warning baseline; frontend source không đổi |
| Frontend type-check | `npx tsc --noEmit` | `PASS` | Exit 0 |
| Frontend production build | Node 24.16.0 + Vite build | `PASS` | 1831 modules transformed |
| Diff hygiene | `git diff --check` | `PASS` | Exit 0, không output |
| Diff scope | `git status --short`; `git diff --stat` | `PASS` | Chỉ 4 source/test backend theo Plan; feature docs/artifacts untracked |

## Các test đã xác minh (lần 1 — lần chạy mới nhất nằm trong phần Re-review)

| Command | Working directory | Exit code | Kết quả | Thời điểm |
|---|---|---:|---|---|
| `bash ./mvnw test` với Java mặc định 17 | `vehiceltracking-backend` | 1 | 0 test; stale class Java 26 không chạy trên Java 17 — blocker môi trường, không phải code failure | 2026-09-04 13:25 +07 |
| Java 26 + `bash ./mvnw clean test` trong sandbox | `vehiceltracking-backend` | 1 | Mockito/Byte Buddy không được self-attach trong sandbox — INCONCLUSIVE môi trường | 2026-09-04 13:26 +07 |
| Java 26 + `bash ./mvnw clean test` ngoài sandbox | `vehiceltracking-backend` | 0 | 80 test, 0 failures, 0 errors, BUILD SUCCESS | 2026-09-04 13:27 +07 |
| `npm run lint` | `vehicletracking-frontend` | 0 | 0 error, 4 warning baseline | 2026-09-04 13:28 +07 |
| `npx tsc --noEmit` | `vehicletracking-frontend` | 0 | Không output | 2026-09-04 13:28 +07 |
| Node 24.16.0 + `node node_modules/vite/bin/vite.js build` | `vehicletracking-frontend` | 0 | 1831 modules transformed | 2026-09-04 13:28 +07 |
| `git diff --check` | repository root | 0 | Không output | 2026-09-04 13:28 +07 |

## Phản hồi xử lý findings

Gemini đã báo xử lý toàn bộ finding. Trạng thái dưới đây là kết quả Codex xác minh độc lập, thay thế trạng thái tự báo của Gemini.

| Finding | File/thay đổi xử lý | Test đã chạy | Trạng thái do Gemini báo |
|---|---|---|---|
| REV-001 | `SimulatorService` xử lý START trước update index; TC-005 có state assertion và exception case | Gemini và Codex full suite 82/82 | `RESOLVED` |
| REV-002 | TC-002 dùng radius bằng exact distance và outside `+0.5m` | Gemini và Codex full suite 82/82 | `RESOLVED` |
| REV-003 | Bổ sung invalid station-coordinate và two-session no-op test | Gemini và Codex full suite 82/82 | `RESOLVED` |
| REV-004 | Thêm subscription confirmation, transcript STOMP, cập nhật EVD-018 và artifacts UI before/after | Codex đối chiếu log/script/source/ảnh; lần chạy mới không kết nối được vì backend đã dừng | `RESOLVED` |

## Review lại

### Lần 2 — 2026-09-04 14:15 Asia/Ho_Chi_Minh

#### Phạm vi xác minh lại

- Đã kiểm tra diff mới của toàn bộ bốn file backend/source-test, script STOMP và `evidence.md`; không có thay đổi frontend, dependency, schema, API hoặc cấu hình nằm ngoài Plan.
- Đã xem trực tiếp `tc008-02-foreign-trip-isolated.png` và đối chiếu với `App.tsx`, `WebSocketConfig.java`, script STOMP. Ảnh không chứa browser console hoặc payload; giao diện trong ảnh cũng đã hiển thị trạm #1 `CHECKED_IN`, trái mô tả “5 trạm PENDING”.
- Codex chạy lại `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=/home/khainq/.sdkman/candidates/java/26.0.1-amzn/bin:$PATH bash ./mvnw clean test`: 82 test, 0 failures, 0 errors, `BUILD SUCCESS`.
- Codex chạy lại `npm run lint` (0 error, 4 baseline warnings), `npx tsc --noEmit` (exit 0), Vite build bằng Node 24.16.0 (1831 modules) và `git diff --check` (exit 0, không output).

#### Trạng thái từng finding trước đó

| Finding | Kết quả xác minh | Evidence/nguồn Codex kiểm tra | Trạng thái |
|---|---|---|---|
| `REV-001` | `tickSingleSimulation` gọi geofence của `currentWp` ở index 0 trước `setCurrentWaypointIndex(nextIndex)`. TC-005 dùng `doAnswer` assert index vẫn là 0; case exception giữ index 0. | `SimulatorService.java:248-270`; `SimulatorServiceTest.java:418-499`; `EVD-015`; full suite 82/82 | `RESOLVED` |
| `REV-002` | Test đặt `radiusMeters` đúng bằng giá trị `GeoUtil.calculateDistanceMeters(...)`, sau đó kiểm tra case cách tâm lớn hơn 0.5m không mutate. Điều này sẽ fail nếu toán tử trong service đổi từ `<=` sang `<`. | `GeofencingServiceTest.java:168-224`; `GeofencingService.java:90`; `EVD-012`; full suite 82/82 | `RESOLVED` |
| `REV-003` | TC-009 đã bao phủ station latitude/longitude null, non-finite và ngoài range, đồng thời không mutate/publish. Test simulator gọi session thứ hai sau no-op và xác nhận hai telemetry độc lập. Đây đáp ứng flow no-op trong TC-009; `tickAllSimulations` khi geofence no-op không có exception vẫn tiếp tục vòng lặp. | `GeofencingServiceTest#checkAndProcessAutoCheckIn_InvalidInputsAndNoPending_SafelyReturnsEmptyWithoutMutation`; `SimulatorServiceTest.java:501-568`; `EVD-019`; full suite 82/82 | `RESOLVED` |
| `REV-004` | Có thêm script phát STOMP, nhưng không có transcript/output của lần chạy để chứng minh message đã tới broker/browser. Quan trọng hơn, script phát `/topic/checkins`, còn log `[REALTIME ISOLATION]` ở `App.tsx` chỉ thuộc callback telemetry; callback check-in return im lặng. PNG “foreign-trip-isolated” không có console/payload và nội dung ảnh không khớp mô tả Evidence. | `send-foreign-checkin-test.mjs:9-32`; `App.tsx:90-138`; `tc008-02-foreign-trip-isolated.png`; `EVD-018:872-904` | `OPEN` |

#### Finding còn bắt buộc xử lý

### REV-004 — Evidence của foreign CheckInEvent vẫn không đủ và có Claim sai

**Severity:** `MEDIUM`

**File:** `docs/features/004-automatic-station-checkin/evidence.md`; `docs/features/004-automatic-station-checkin/artifacts/send-foreign-checkin-test.mjs`; `docs/features/004-automatic-station-checkin/artifacts/tc008-02-foreign-trip-isolated.png`

**Vị trí:** `EVD-018`, lines 868-908; script lines 9-32; `App.tsx` lines 90-138

**Requirement:** `REQ-004 / AC-REQ-004-02`

**Spec/Business Rule:** `SPEC-004 / BR-008`

**Test:** `TC-008`

**Evidence:** `EVD-018`

**Vấn đề:**

Script mới là stimulus có thể tái lập về mặt source, nhưng không có output/log lưu lại cho thấy nó đã kết nối và publish thành công trong cùng phiên browser. `EVD-018` viết rằng browser ghi log `[REALTIME ISOLATION] Bỏ qua telemetry...`, nhưng script chỉ publish `CheckInEvent` đến `/topic/checkins`; log đó chỉ nằm trong `onTelemetry`, không nằm trong `onCheckIn`. Vì vậy Claim “console browser ghi nhận log bỏ qua” không được source hay ảnh hỗ trợ. PNG không hiển thị console/payload/current trip ID, và đồng thời đã hiển thị trạm #1 `CHECKED_IN`, nên không chứng minh trạng thái trước/sau foreign event là không đổi như mô tả.

**Ảnh hưởng:**

`EVD-018` không đủ để chuyển `AC-REQ-004-02` sang PASS. Không có Evidence không đồng nghĩa behavior chắc chắn sai, nhưng đây là Requirement MUST nên Codex không thể `APPROVED`.

**Đề xuất:**

Chạy lại TC-008 với artifact có thể đối chiếu trong cùng phiên: (1) log stdout của script kết nối/publish hoặc browser/STOMP receiver chứng minh payload `tripId` khác đã được nhận, (2) current trip ID, (3) ảnh/trạng thái trước và sau không có toast/timeline/map thay đổi. Hoặc bổ sung UI/unit test không cần browser rồi lưu command output. Sửa `EVD-018` để mô tả đúng check-in callback (không có telemetry log) và chỉ đặt `PASS` khi command/flow đã thực sự chạy.

**Bắt buộc xử lý trước review tiếp theo:** `Có`

**Trạng thái sau re-review lần 3:** `RESOLVED` — xem “Lần 3” trong phần Review lại.

#### Traceability sau re-review

| Requirement/AC | Spec/Test | Implementation | Evidence được xác minh | Kết quả |
|---|---|---|---|---|
| `REQ-001 / AC-REQ-001-01/02` | `SPEC-001; TC-001/002` | `GeofencingService#checkAndProcessAutoCheckIn` | `EVD-011`, `EVD-012`, backend 82/82 Codex rerun | `PASS` |
| `REQ-002 / AC-REQ-002-01/02` | `SPEC-002; TC-003/004` | Query first PENDING, state transition tuần tự | `EVD-013`, `EVD-014`, backend 82/82 Codex rerun | `PASS` |
| `REQ-003 / AC-REQ-003-01/02` | `SPEC-003; TC-005/006` | START trước index update; toàn bộ waypoint được duyệt | `EVD-015`, `EVD-016`, backend 82/82 Codex rerun | `PASS` |
| `REQ-004 / AC-REQ-004-01` | `SPEC-004; TC-007/008` | Save trước event, matching check-in tạo toast | `EVD-017`, matching UI artifact | `PASS` |
| `REQ-004 / AC-REQ-004-02` | `SPEC-004 / BR-008; TC-008` | `App.tsx` filter foreign check-in | Source filter có mặt, nhưng `EVD-018`/artifact không chứng minh stimulus-delivery và no-effect | `INCONCLUSIVE` — REV-004 |
| `REQ-005 / AC-REQ-005-01/02` | `SPEC-005; TC-009/010/011` | Defensive validation, no-op, completion | `EVD-019`, `EVD-020`, `EVD-021`, backend 82/82 Codex rerun | `PASS` |

#### Regression sau thay đổi xử lý findings

| Khu vực | Lệnh/nguồn | Kết quả |
|---|---|---|
| Backend clean suite | Java 26 + `bash ./mvnw clean test` | `PASS` — 82 tests, 0 failures/errors |
| Frontend lint | `npm run lint` | `PASS` — 0 error, 4 baseline warnings |
| Frontend type-check | `npx tsc --noEmit` | `PASS` |
| Frontend production build | Node 24.16.0 + `node node_modules/vite/bin/vite.js build` | `PASS` — 1831 modules |
| Diff hygiene | `git diff --check` | `PASS` — không output |

**Không phát hiện regression hoặc finding source-code mới** từ các thay đổi xử lý REV-001..003. Giới hạn scheduler xử lý exception của một session vẫn là rủi ro đã ghi ở review trước; không phải regression của no-op được yêu cầu ở TC-009.

### Lần 3 — 2026-09-04 14:35 Asia/Ho_Chi_Minh

#### Xác minh REV-004

- `foreign-checkin-stimulus.log:1-54` chứa handshake STOMP thành công tới `/ws-raw`, `SUBSCRIBE /topic/checkins`, `SEND` payload `tripId: 999999`, sau đó nhận frame `MESSAGE` từ broker có đúng payload và kết thúc bằng `[STOMP-DELIVERY-VERIFIED]`.
- Script hiện tại khớp transcript: `send-foreign-checkin-test.mjs` subscribe trước khi publish, chỉ exit 0 khi callback subscriber nhận message. Điều này đóng khoảng trống stimulus/delivery của review lần 2.
- `App.tsx:127-138` đã được đối chiếu: callback `onCheckIn` return trước `addToast` khi `event.tripId !== activeTrip.id`; không còn Claim sai về telemetry log trong `EVD-018`.
- Ảnh `tc008-01` và `tc008-02` đều thể hiện timeline 5 trạm PENDING, không có foreign toast; chúng là hai artifact khác nhau (SHA-256 khác nhau). Timestamps hiển thị trong các ảnh kế tiếp cho thấy thứ tự phù hợp: stimulus lúc 14:26:50, START check-in lúc 14:27:11, trạm 2 lúc 14:27:42, hoàn tất lúc 14:28:31. `tc008-03` hiển thị toast `Auto Check-in Trạm #1`; `tc008-04` và `tc008-05` thể hiện trạm 2 rồi toàn bộ 5 trạm đã CHECKED_IN.
- Codex đã thử chạy lại script bằng Node 24.16.0, nhưng backend local đã dừng nên WebSocket đóng trước CONNECT. Đây là giới hạn môi trường của lần review hiện tại, không phủ định transcript execution đã được lưu; source application không đổi kể từ lần chạy 82/82 ở review lần 2.

#### Trạng thái finding

| Finding | Requirement → Spec → Test → Evidence | Trạng thái |
|---|---|---|
| `REV-004` | `REQ-004 / AC-REQ-004-02 → SPEC-004 / BR-008 → TC-008 → EVD-018` | `RESOLVED` |

#### Traceability hiện hành

| Requirement/AC | Evidence đã kiểm tra | Kết quả |
|---|---|---|
| `REQ-001 / AC-REQ-001-01/02` | `EVD-011`, `EVD-012`, backend 82/82 | `PASS` |
| `REQ-002 / AC-REQ-002-01/02` | `EVD-013`, `EVD-014`, backend 82/82 | `PASS` |
| `REQ-003 / AC-REQ-003-01/02` | `EVD-015`, `EVD-016`, backend 82/82 | `PASS` |
| `REQ-004 / AC-REQ-004-01/02` | `EVD-017`, `EVD-018`, STOMP transcript, UI artifacts, source filter | `PASS` |
| `REQ-005 / AC-REQ-005-01/02` | `EVD-019`, `EVD-020`, `EVD-021`, backend 82/82 | `PASS` |

**Kết quả:** Không có Requirement MUST nào còn `FAIL` hoặc `INCONCLUSIVE`. Không có thay đổi source/dependency/schema/API mới sau verification ở review lần 2; `git diff --check` tiếp tục sạch.

## Vấn đề còn tồn tại

| Vấn đề | Severity/Rủi ro | Quyết định | Người xác nhận |
|---|---|---|---|
| Idempotency đồng thời/distributed chưa được bảo đảm | Ngoài scope feature, rủi ro thấp với simulator tuần tự | Giữ giới hạn đã ghi trong Spec; cần feature riêng khi nhận GPS đa nguồn | Requirement/Spec hiện tại |
| Event phát sau `save` nhưng trước transaction commit | Known limitation, rủi ro vừa | Chấp nhận trong scope theo Requirement; không thêm outbox/queue | Requirement hiện tại |
| 4 frontend lint warning baseline | Thấp, không do feature 004 | Không mở rộng scope | Codex |
| Project cần Java 26 nhưng shell mặc định là Java 17 | Rủi ro vận hành verification | Dùng Java 26 rõ ràng trong command/CI | Codex |

## Findings bắt buộc trước lần review tiếp theo

Không có. `REV-001..REV-004` đã được Codex xác minh là `RESOLVED`.

## Kết luận cuối cùng

`APPROVED`. `REV-001..REV-004` đều đã được xử lý, đối chiếu source và có Evidence kiểm chứng phù hợp. Đường truy vết `REQ-004 / AC-REQ-004-02 → SPEC-004 / BR-008 → TC-008 → EVD-018` nay có transcript STOMP cho stimulus/delivery, source guard cho xử lý foreign event, và UI artifacts before/after. Các Requirement MUST còn lại có evidence test/regression từ review lần 2; không phát hiện regression mới.

## Checklist đóng review

- [x] Mỗi finding có severity, vị trí, ảnh hưởng và đề xuất.
- [x] Requirement, Spec, Test-Plan và Plan đã được đối chiếu.
- [x] Evidence Matrix và Evidence quan trọng đã được kiểm tra độc lập.
- [x] Regression phù hợp đã chạy lại.
- [x] Findings bắt buộc đã được liệt kê.
- [x] Tất cả findings đã sửa được Codex xác minh lại.
- [x] Kết luận phản ánh đúng Evidence và vấn đề còn tồn tại.
- [x] Không còn Requirement quan trọng ở trạng thái FAIL/INCONCLUSIVE.
