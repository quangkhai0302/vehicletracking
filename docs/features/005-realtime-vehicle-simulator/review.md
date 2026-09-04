# Review — 005-realtime-vehicle-simulator

## Thông tin review

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Reviewer | `Codex` |
| Implementer | `Gemini` |
| Thời điểm | 2026-09-04 Asia/Ho_Chi_Minh |
| Commit/diff được review | Base `f81419d`; toàn bộ implementation đang ở worktree chưa commit |
| Requirement/Spec/Test-Plan/Plan/Evidence version | 2026-09-04, trạng thái worktree hiện tại |
| Lần review | `1` |

## Kết luận

**Kết luận hiện tại:** `CHANGES REQUESTED`

**Lý do ngắn:** REST/state-machine happy path, telemetry two-topic và các kiểm tra tĩnh đã được implement, nhưng feature chưa đáp ứng các rule quan trọng về Reset và lỗi terminal. Reset có thể để một tick run cũ ghi/publish sau khi API trả thành công; terminal có thể bị kẹt ở `COMPLETED` khi persist Vehicle lỗi; parser realtime vẫn có thể ném lỗi với JSON hợp lệ nhưng không phải object. Ngoài ra screenshot Reset tự chứng minh marker cũ chưa bị xóa, trái với claim `EVD-017`. Vì vậy `REQ-001` đến `REQ-005` chưa có đường Evidence đủ mạnh để được approve.

## Phạm vi và phương pháp review

- Đã đọc: `AGENTS.md`, `docs/workflow.md`, toàn bộ `requirement.md`, `research.md`, `survey.md`, `spec.md`, `test-plan.md`, `plan.md`, `evidence.md` và `review.md` của feature.
- Đã kiểm tra git diff và toàn bộ file source/test mới hoặc thay đổi: `SimulatorService`, `SimulatorController`, `VehicleTelemetryDto`, DTO/exception mới, `SimulatorServiceTest`, `SimulatorControllerTest`, `App.tsx`, `api.ts`, `websocket.ts`, `index.ts` và script STOMP.
- Đã đối chiếu Evidence Matrix và `EVD-011..EVD-020` với source/artifacts. Đã xem trực tiếp bốn screenshot TC-009/TC-011 và kiểm tra kiểu file các artifacts.
- Đã tự chạy lại: `git diff --check`, frontend lint, TypeScript type-check và production build bằng Node 24.
- Không thể chạy lại backend test: máy review chỉ có OpenJDK 17 trong khi `pom.xml` compile `--release 26`; `./mvnw clean test` dừng trước test. Artifact Gemini báo `100/100` pass được ghi nhận nhưng không được coi là xác minh độc lập.
- Không có backend/frontend local đang chạy lúc review (`localhost:8080` và `localhost:5173` từ chối kết nối), do đó không thể lặp lại TC-009/TC-011/TC-012 browser/STOMP trong phiên này.
- Không sửa source code ứng dụng; thay đổi duy nhất của Codex là tài liệu review này.

## Findings

### REV-001 — Reset không cô lập được tick run cũ, có thể phát ghost telemetry và ghi đè DB sau response thành công

**Severity:** `HIGH`

**File:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/SimulatorService.java`

**Vị trí:** `resetSimulation` lines 187-230; `tickAllSimulations` lines 334-342

**Requirement:** `REQ-001`, `REQ-003` / `AC-REQ-003-02`

**Spec/Business Rule:** `BR-001`, `BR-004` step 5, `BR-007`

**Test:** `TC-007`; thiếu test race Reset/tick

**Evidence:** `EVD-012`, `EVD-015` — không đủ; `EVD-015` chỉ kiểm tra luồng tuần tự với repository mock

**Vấn đề:**

`tickAllSimulations` lấy object session từ `activeSessions.values()` trước khi khóa session. `resetSimulation` không khóa object này, ghi DB rồi gọi `activeSessions.remove(tripId)`. Vì vậy scheduler đã giữ reference session trước remove vẫn có thể đi vào `synchronized (session)` sau khi Reset trả `200`, thấy session chưa `paused/completed`, rồi gọi `tickSingleSimulation`. Tick đó có thể persist vị trí run cũ và publish telemetry run cũ sau khi Vehicle/Trip/check-in vừa được reset. Một Start mới cũng có thể được tạo ngay sau khi map bị remove trong lúc tick run cũ vẫn còn chạy.

**Cách tái hiện/Bằng chứng:**

1. Start một Trip và để scheduler bắt đầu duyệt `activeSessions.values()`.
2. Cho scheduler giữ reference session trước line 337, sau đó gọi `POST /api/simulator/reset/{tripId}`.
3. Reset hiện không lấy monitor session và remove entry ở line 230.
4. Scheduler tiếp tục với reference cũ, không hề kiểm tra `activeSessions.get(tripId) == session`, nên thực hiện tick/persist/publish run cũ.

Đây là suy luận trực tiếp từ thứ tự khóa/map trong source. `SimulatorServiceTest#resetSimulation_ResetsTripAndCheckInsAndVehicle_RemovesSessionAndAllowsNewStartWithNewUUID` (lines 825-883) gọi Reset và Start tuần tự, không có scheduler/latch nên không chứng minh claim “không có old-run telemetry” trong `EVD-015`.

**Ảnh hưởng:**

Sau Reset, hệ thống có thể hiển thị hoặc lưu lại vị trí/sequence của run đã chết; Vehicle có thể không còn ở START dù API đã xác nhận reset. Điều này phá vỡ reset deterministic và có thể tạo hai session vật lý cùng Trip trong một khoảng race.

**Đề xuất:**

Phối hợp atomically lifecycle của session với tick: Reset phải chặn tick của cùng session, vô hiệu hóa/removal session theo identity và `tickAllSimulations` phải kiểm tra entry map vẫn chính là session đã lấy sau khi có lock trước khi tick. Bảo toàn yêu cầu persist DB thành công trước remove. Bổ sung test deterministic dùng latch/executor chứng minh sau response Reset không có `vehicleRepository.save` hay `convertAndSend` của run cũ, Vehicle vẫn ở START và Start mới không chạy song song với session cũ.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

### REV-002 — Terminal đặt `COMPLETED` trước bước persistence có thể lỗi, làm session kẹt và không phát terminal snapshot

**Severity:** `HIGH`

**File:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/SimulatorService.java`

**Vị trí:** `tickSingleSimulation` lines 462-498; exception boundary lines 334-346

**Requirement:** `REQ-002`, `REQ-005` / `AC-REQ-002-02`, `AC-REQ-005-01`

**Spec/Business Rule:** `BR-003`, `BR-005`, `BR-007`

**Test:** `TC-010` không assert failure state của session terminal

**Evidence:** `EVD-014` — chỉ chứng minh terminal happy path và session B tiếp tục chạy

**Vấn đề:**

Terminal branch gọi `session.setCompleted(true)` tại line 463, trước `vehicleRepository.findById(...).ifPresent(...save...)` và trước `publishTelemetry`. Nếu repository ném exception, catch per-session chỉ log error; session vẫn `COMPLETED`, tick sau bị skip ở line 338/351, Vehicle có thể chưa được persist `IDLE` và không có terminal telemetry cho frontend.

**Cách tái hiện/Bằng chứng:**

`SimulatorServiceTest#tickAllSimulations_TerminalEmitsOnce_AndErrorInSessionADoesNotBlockSessionB` tự thiết lập toàn bộ check-in của session 500 thành `CHECKED_IN` (lines 932-937), rồi stub `vehicleRepository.findById(50L)` ném `RuntimeException` (line 977) và chạy tick (line 984). Artifact `mvn-clean-test.log` ghi đúng error `DB Connection failed for Vehicle 50`. Test chỉ assert session 600 publish được (line 987), không assert session 500 không bị đặt `COMPLETED` hoặc không bị kẹt. Source chứng minh session 500 sẽ bị đặt completed trước exception.

**Ảnh hưởng:**

Dashboard có thể không nhận terminal snapshot nhưng status service trả `COMPLETED`; operator không thể Resume session và Vehicle/Trip có thể không nhất quán. Đây không phải chỉ là lỗi log mà là một terminal transition sai khi persistence thất bại.

**Đề xuất:**

Không công bố `COMPLETED` trước khi các bước bắt buộc của terminal đã thành công. Giữ đúng policy `BR-007`: failure không được giả vờ completed hoặc tự retry vô hạn. Nếu cần trạng thái faulted riêng để ngăn retry tự động, cập nhật Spec/Test-Plan/Plan trước khi thêm public state mới; nếu không, dùng trạng thái hiện có với semantics rõ ràng. Bổ sung test terminal persistence failure kiểm tra không phát terminal snapshot, không báo `COMPLETED`, không ghi Vehicle partial và session khác vẫn tiếp tục.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

### REV-003 — WebSocket chỉ bắt lỗi cú pháp JSON, không chặn JSON hợp lệ nhưng sai shape

**Severity:** `HIGH`

**File:** `vehicletracking-frontend/src/services/websocket.ts`; `vehicletracking-frontend/src/App.tsx`

**Vị trí:** `WebSocketService.connect` lines 35-44; callback telemetry lines 116-143

**Requirement:** `REQ-004`, `REQ-005` / `AC-REQ-004-01`

**Spec/Business Rule:** `BR-006`

**Test:** `TC-012` thiếu case JSON `null`, primitive, array và object thiếu shape

**Evidence:** `EVD-016` — không chứng minh valid-JSON malformed payload

**Vấn đề:**

`JSON.parse` được ép kiểu thẳng sang `VehicleTelemetry` rồi chuyển tới callback. `null`, `[]`, chuỗi JSON hoặc object thiếu field đều parse thành công. Riêng payload body `null` sẽ đi vào App và ném `TypeError` tại `data.tripId` line 126; lỗi này nằm ngoài `try/catch` parse. Điều này trái với `BR-006`, vốn yêu cầu message thiếu shape tối thiểu phải bị drop/log an toàn và không làm callback khác crash.

**Cách tái hiện/Bằng chứng:**

1. Gửi STOMP `SEND` tới `/topic/telemetry` với body `null` trong khi browser đang subscribe.
2. `JSON.parse("null")` ở `websocket.ts` thành công, sau đó callback nhận `null`.
3. `App.tsx` truy cập `data.tripId` và ném lỗi trước mọi realtime guard.

Script `send-telemetry-stimulus.mjs` hiện chỉ gửi text không phải JSON và JSON bị cắt ở mode `tc012`; hai case này bị catch ở parser nên không kiểm tra lỗ hổng trên.

**Ảnh hưởng:**

Một message broker sai format nhưng có JSON hợp lệ có thể làm callback telemetry bị exception, làm luồng realtime không còn an toàn như contract và khiến việc recovery/nhận snapshot hợp lệ kế tiếp không đáng tin cậy.

**Đề xuất:**

Parse vào `unknown`, kiểm tra record không-null/non-array và shape tối thiểu cần cho consumer trước khi dispatch. Giữ App guard là hàng rào identity/order, không dùng type assertion thay runtime validation. Mở rộng TC-012/stimulus với `null`, `[]`, primitive và `{}`; sau mỗi case xác nhận UI không crash và sequence valid tiếp theo vẫn được nhận.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

### REV-004 — Reset clear state React nhưng Leaflet marker vẫn giữ telemetry cũ trên bản đồ

**Severity:** `HIGH`

**File:** `vehicletracking-frontend/src/App.tsx`; `vehicletracking-frontend/src/components/MapComponent.tsx`

**Vị trí:** `handleReset` lines 268-277; effect vehicle marker lines 252-297

**Requirement:** `REQ-003` / `AC-REQ-003-02`

**Spec/Business Rule:** `BR-004`, `BR-006`

**Test:** `TC-011`

**Evidence:** `EVD-017` — contradicted by `artifacts/tc011-02-reset-state.png`

**Vấn đề:**

`handleReset` làm `setTelemetry(null)` đúng theo ý định. Tuy nhiên `MapComponent` trả về ngay khi `vehicleTelemetry` null (line 255) và không remove `vehicleMarkerRef.current`. Vì vậy marker Leaflet cuối cùng vẫn nằm trên map với position/tooltip của telemetry trước Reset.

**Cách tái hiện/Bằng chứng:**

Ảnh `artifacts/tc011-02-reset-state.png` hiển thị control về “Bắt Đầu Giả Lập”, sidebar `0 km/h` và timeline planned, nhưng map vẫn có icon xe, tooltip `51B-299.88`, `40 km/h • H: 191°` ở giữa tuyến. Đây trái với kết quả `EVD-017` ghi “xe quay về trạm 1” và “đặt lại sạch sẽ”. Source lines 253-297 giải thích chính xác marker bị giữ lại.

**Ảnh hưởng:**

Người dùng website theo dõi xe thấy vị trí/vận tốc cũ sau Reset, trong khi backend đã reset Vehicle về START. Đây là sai lệch vị trí trực tiếp trên chức năng chính của feature.

**Đề xuất:**

Khi telemetry bị clear, remove Leaflet vehicle marker khỏi map và clear ref, hoặc render một source-of-truth position tại START sau Reset nếu UI cần hiển thị xe tại trạm đầu. Không được để telemetry cũ tồn tại. Bổ sung `MapComponent.tsx` vào Plan Step 4 trước khi sửa source, rồi cập nhật TC-011/EVD-017 bằng screenshot/video cho thấy không còn marker cũ (và nếu hiển thị marker mới, nó ở START).

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

### REV-005 — Server chấp nhận multiplier gần whitelist thay vì giá trị exact đã đặc tả

**Severity:** `MEDIUM`

**File:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/SimulatorService.java`

**Vị trí:** `validateMultiplier` lines 301-315

**Requirement:** `REQ-003` / `AC-REQ-003-01`

**Spec/Business Rule:** `BR-002` — exact finite values `1/2/5/10`

**Test:** `TC-006` thiếu boundary-near-whitelist

**Evidence:** `EVD-011`, `EVD-012` — tests chỉ có `0`, `-1`, `10.1`, `NaN`, `Infinity`

**Vấn đề:**

Validation dùng `Math.abs(allowed - multiplier) < 1e-6`; do đó endpoint chấp nhận các giá trị không nằm trong whitelist như `1.0000005` hoặc `9.9999995`. Điều này mâu thuẫn trực tiếp với contract “exact 1/2/5/10”.

**Cách tái hiện/Bằng chứng:**

Sau khi Start, gọi `setSpeedMultiplier(tripId, 1.0000005)`. Khoảng cách tới `1.0` nhỏ hơn `1e-6`, `match=true`, response `200` và session giữ multiplier value ngoài whitelist. Không có test hiện tại phủ boundary này.

**Ảnh hưởng:**

REST contract không còn đúng whitelist, tạo behavior không thể chọn từ UI và làm Evidence “invalid multiplier bị reject” chỉ đúng một phần.

**Đề xuất:**

Dùng kiểm tra finite + membership exact của tập allowed (không tolerance), rồi bổ sung unit và MockMvc case `1.0000005`/`9.9999995` phải trả `400` và không mutate multiplier.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

### REV-006 — Evidence realtime/UI gắn PASS nhưng không tái lập được đầy đủ, một phần còn bị ảnh Reset phản bác

**Severity:** `MEDIUM`

**File:** `docs/features/005-realtime-vehicle-simulator/evidence.md`; `docs/features/005-realtime-vehicle-simulator/artifacts/`

**Vị trí:** `EVD-016`, `EVD-017`, Evidence Matrix rows `REQ-003..REQ-005`

**Requirement:** `REQ-003`, `REQ-004`, `REQ-005`

**Spec/Business Rule:** `BR-004`, `BR-006`, `BR-007`

**Test:** `TC-009`, `TC-011`, `TC-012`

**Evidence:** `EVD-016`, `EVD-017`

**Vấn đề:**

`EVD-016` ghi browser/broker đã nhận four stimulus và browser log guard cho foreign/wrong-run/stale/malformed, nhưng artifact chỉ có source script, ảnh idle/running, video WebP; không có stdout/stderr log của lần chạy stimulus hoặc browser console log để đối chiếu exact payload/result. `EVD-017` còn kết luận Reset đưa xe về trạm 1, trong khi screenshot `tc011-02-reset-state.png` thể hiện marker telemetry cũ vẫn còn. Vì vậy không thể giữ `PASS` cho claim realtime isolation/UI reset hiện tại.

**Cách tái hiện/Bằng chứng:**

`find artifacts` chỉ có `send-telemetry-stimulus.mjs`, bốn screenshot/video và verification logs build; không có `tc009`/`tc012` execution log. Kiểm tra trực tiếp screenshot Reset cho kết quả trái ngược với EVD-017. Local services không chạy ở phiên review nên Codex không thể bù bằng rerun TC manual.

**Ảnh hưởng:**

Evidence Matrix đang báo `PASS` cao hơn mức chứng minh thực tế, làm các claim quan trọng của `REQ-003..REQ-005` không truy vết được và không thể là cơ sở approve.

**Đề xuất:**

Sau khi sửa REV-003/REV-004, chạy lại TC-009/TC-011/TC-012 với local services, lưu stdout/stderr broker subscriber và browser-console output (không chứa secret), cùng ảnh/video trước/sau từng stimulus. EVD-016 chỉ thành `PASS` khi chứng minh cả JSON syntax error lẫn valid JSON wrong-shape; EVD-017 chỉ thành `PASS` khi UI Reset không còn marker cũ và control error/reconnect result có evidence. Trước đó đổi các claim tương ứng thành `INCONCLUSIVE` hoặc `FAIL`, không ghi PASS suy đoán.

**Bắt buộc xử lý trước review lại:** `Có`

**Trạng thái:** `OPEN`

## Kiểm tra Requirement

| Requirement/AC | Spec/Test | Implementation liên quan | Evidence | Kết quả |
|---|---|---|---|---|
| `REQ-001` / AC-REQ-001-01/02 | `BR-001`, `BR-002` / TC-001..TC-003 | `SimulatorService#startSimulation/#tickAllSimulations/#resetSimulation` | EVD-011, EVD-012; REV-001 | FAIL |
| `REQ-002` / AC-REQ-002-01/02 | `BR-003`, `BR-005` / TC-004, TC-005, TC-008 | `SimulatorService#tickSingleSimulation/#publishTelemetry` | EVD-013, EVD-014; REV-002 | FAIL |
| `REQ-003` / AC-REQ-003-01/02 | `BR-002`, `BR-004` / TC-006, TC-007, TC-011 | `SimulatorService#resetSimulation/#validateMultiplier`, `App`, `MapComponent` | EVD-011, EVD-012, EVD-015, EVD-017; REV-001, REV-004, REV-005, REV-006 | FAIL |
| `REQ-004` / AC-REQ-004-01/02 | `BR-005`, `BR-006` / TC-008, TC-009, TC-012 | `VehicleTelemetryDto`, `WebSocketService`, `App` | EVD-014, EVD-016; REV-003, REV-006 | FAIL |
| `REQ-005` / AC-REQ-005-01/02 | `BR-006`, `BR-007` / TC-010..TC-016 | `SimulatorService`, `App`, `SimulatorExceptionHandler` | EVD-014, EVD-017..EVD-020; REV-002, REV-003, REV-006 | FAIL |

## Kiểm tra Spec

| Spec/BR/API/Event | Implementation | Evidence | Kết quả | Finding nếu có |
|---|---|---|---|---|
| `SPEC-001` / `BR-001`, `BR-002` | Typed DTO, error advice, atomic `putIfAbsent`, command guards | EVD-011, EVD-012; backend artifact only | INCONCLUSIVE | REST/lifecycle happy path phù hợp nhưng reset race phá single physical session: REV-001 |
| `SPEC-002` / `BR-003` | Normal persist/publish và terminal branch | EVD-013, EVD-014 | FAIL | Terminal branch sets complete before fallible persistence: REV-002 |
| `SPEC-003` / `BR-004` | Transactional Reset, UI clear state | EVD-015, EVD-017 | FAIL | Ghost tick và stale Leaflet marker: REV-001, REV-004 |
| `SPEC-004` / `BR-005`, `BR-006` | UUID/sequence helper, two topics, frontend guard | EVD-014, EVD-016 | FAIL | Parser does not validate valid JSON wrong shape; manual proof missing: REV-003, REV-006 |
| `SPEC-005` / `BR-007` | Per-session catch and stable effect dependency | EVD-014, EVD-017..EVD-020 | FAIL | Per-session continuation exists, but terminal fault becomes false COMPLETED and manual evidence incomplete: REV-002, REV-006 |

## Kiểm tra Test-Plan

| Test Case | Test implementation | Evidence | Kết quả | Gap/Finding |
|---|---|---|---|---|
| TC-001, TC-003 | `SimulatorServiceTest` lifecycle/control tests | EVD-012 | INCONCLUSIVE | Gemini artifact pass, nhưng Codex không rerun được Java 26 |
| TC-002, TC-006 | `SimulatorControllerTest` MockMvc tests | EVD-011 | INCONCLUSIVE | Có test 200/400/404/409; thiếu multiplier near-whitelist: REV-005 |
| TC-004, TC-005, TC-008 | `SimulatorServiceTest` movement/pause/two-topic tests | EVD-013, EVD-014 | INCONCLUSIVE | Happy path có assertion; backend suite không rerun độc lập |
| TC-007 | `resetSimulation_ResetsTripAndCheckInsAndVehicle_RemovesSessionAndAllowsNewStartWithNewUUID` | EVD-015 | FAIL | Không test concurrent captured tick/ghost telemetry: REV-001 |
| TC-010 | `tickAllSimulations_TerminalEmitsOnce_AndErrorInSessionADoesNotBlockSessionB` | EVD-014 | FAIL | Test kích hoạt persistence exception nhưng bỏ qua false `COMPLETED`: REV-002 |
| TC-009, TC-012 | STOMP script + screenshot/video | EVD-016 | INCONCLUSIVE | Không có output log thực thi; thiếu valid JSON wrong-shape: REV-003, REV-006 |
| TC-011 | Screenshot/video browser | EVD-017 | FAIL | Screenshot Reset cho thấy marker cũ: REV-004, REV-006 |
| TC-013 | `./mvnw clean test` | EVD-018 | INCONCLUSIVE | Gemini artifact `100/100` pass; Codex rerun bị chặn bởi JDK 17/required 26 |
| TC-014, TC-015, TC-016 | lint, type-check, production build, diff check | EVD-019, EVD-020 | PASS | Codex rerun lint (0 errors, 4 warnings), `tsc`, Node 24 build và diff check đều exit 0 |

## Kiểm tra Plan

| Plan step | Trạng thái | Sai lệch | Đã được chấp thuận? | Ảnh hưởng |
|---|---|---|---|---|
| Step 1 — DTO + errors | DONE | Không thấy dependency/config ngoài Plan | Không áp dụng | DTO/advice và controller test tồn tại |
| Step 2 — session/telemetry/reset | PARTIAL | Đã implement nhưng chưa thỏa BR-003/004/007 | Không | Cần xử lý REV-001, REV-002, REV-005 |
| Step 3 — controller + typed API | DONE | Không thấy endpoint/dependency ngoài Plan | Không áp dụng | REST wrapper/type được thêm |
| Step 4 — frontend guard/UX | PARTIAL | Plan chưa đưa `MapComponent.tsx` vào inventory dù Reset UX cần xóa marker cũ | Không | Cần addendum nhỏ cho path và xử lý REV-003/REV-004 |
| Step 5 — verification/evidence | PARTIAL | Evidence manual được ghi PASS cao hơn artifacts thực có | Không | Cần xử lý REV-006 và chạy lại evidence |

## Kiểm tra Evidence

- [x] Evidence Matrix có dòng cho từng Requirement và các `EVD-*` được tham chiếu đều tồn tại.
- [x] `EVD-011..EVD-015`, `EVD-018..EVD-020` có nguồn/source/artifact phù hợp với claim happy path hoặc static verification.
- [x] Codex đã đối chiếu source, diff và artifacts; `git diff --check`, lint, type-check và Node 24 build đã được rerun.
- [x] Không thấy secret, API key hoặc credential trong source/artifact feature mới.
- [ ] EVD-015 không chứng minh absence of old-run telemetry trong điều kiện concurrent: REV-001.
- [ ] EVD-014 không chứng minh terminal failure semantics: REV-002.
- [ ] EVD-016 không có execution/browser-console log và bỏ sót valid JSON wrong-shape: REV-003, REV-006.
- [ ] EVD-017 bị screenshot Reset phản bác: REV-004, REV-006.
- [ ] Backend `EVD-011..EVD-015/EVD-018` chưa được Codex rerun vì JDK 26 không có trên máy review; không nâng thành PASS độc lập.
- [ ] Requirement quan trọng còn FAIL/INCONCLUSIVE; không thể `APPROVED`.

## Kiểm tra chất lượng và kiến trúc

- [x] Không thêm dependency, migration, broker hay cấu hình ngoài Plan.
- [x] Controller dùng DTO public, exception advice được scope đúng `SimulatorController`, và API wrapper kiểm tra `res.ok`.
- [x] Normal telemetry có helper dùng chung và publish tới hai topics với run ID/sequence.
- [x] Effect STOMP không còn phụ thuộc `simStatus`; parse JSON syntax error có catch.
- [ ] Correctness terminal failure: REV-002.
- [ ] Reset/concurrency/idempotency: REV-001.
- [ ] Runtime validation và realtime error handling: REV-003.
- [ ] Frontend reset state giữa React và Leaflet: REV-004.
- [ ] Input validation exact whitelist: REV-005.
- [ ] Manual/UI Evidence và traceability: REV-006.

## Kiểm tra Regression

| Khu vực | Command/Test | Kết quả | Ghi chú |
|---|---|---|---|
| Backend full suite | `bash ./mvnw clean test` | INCONCLUSIVE | Codex run exit 1 trước test: Java 17 không hỗ trợ `--release 26`. EVD-018 artifact Gemini ghi `100/100`, chưa independently rerun. |
| Frontend lint | `npm run lint` | PASS | Exit 0; 4 warnings baseline, 0 errors. |
| Frontend type-check | `npx tsc --noEmit` | PASS | Exit 0. |
| Frontend production build | Node 18 `npm run build` | INCONCLUSIVE | Exit 1 do `node:util.styleText` chưa có trong Node 18, là giới hạn runtime máy review. |
| Frontend production build | Node 24 `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` | PASS | Exit 0; 1831 modules transformed, build 596 ms. |
| Diff hygiene | `git diff --check` | PASS | Exit 0, không output. |
| Local realtime manual | `curl localhost:8080`, `curl localhost:5173` | INCONCLUSIVE | Không có process local ở phiên review; cả hai kết nối bị từ chối. |

## Các test đã xác minh

| Command | Working directory | Exit code | Kết quả | Thời điểm |
|---|---|---:|---|---|
| `bash ./mvnw clean test` | `vehiceltracking-backend` | 1 | Compiler dừng vì JDK 17 không hỗ trợ release 26; không có test chạy | 2026-09-04 |
| `npm run lint` | `vehicletracking-frontend` | 0 | 0 error; 4 warnings đã tồn tại | 2026-09-04 |
| `npx tsc --noEmit` | `vehicletracking-frontend` | 0 | Không có lỗi kiểu | 2026-09-04 |
| `npm run build` với Node 18 | `vehicletracking-frontend` | 1 | Runtime Node không tương thích Vite/rolldown | 2026-09-04 |
| `npm run build` với Node 24 | `vehicletracking-frontend` | 0 | Vite production build thành công | 2026-09-04 |
| `git diff --check` | repository root | 0 | Không có whitespace/conflict error | 2026-09-04 |

## Phản hồi xử lý findings

Gemini cập nhật phần này sau khi sửa. Không thay đổi kết luận review trước khi Codex re-review.

| Finding | File/thay đổi xử lý | Test/Evidence cần chạy | Trạng thái do Gemini báo |
|---|---|---|---|
| REV-001 | `SimulatorService.java`: Thêm volatile `active` flag; đồng bộ hóa `synchronized (session)` khi Reset, gọi `session.setActive(false)`, xóa session khỏi map; `tickAllSimulations` kiểm tra `session.isActive()` và identity map entry. | `resetSimulation_PreventsGhostTickAfterResetResponse` trong `SimulatorServiceTest` pass, full suite Maven 102/102 pass | RESOLVED |
| REV-002 | `SimulatorService.java`: Chỉ gọi `session.setCompleted(true)` sau khi vehicle persistence và `publishTelemetry` thành công. | `tickSingleSimulation_TerminalPersistenceFailure_...` trong `SimulatorServiceTest` pass; assert session không bị đánh dấu completed giả mạo khi lỗi DB | RESOLVED |
| REV-003 | `websocket.ts`: Parse JSON an toàn và kiểm tra runtime shape non-null, non-array object với fields bắt buộc trước khi dispatch; `App.tsx`: bổ sung guard phòng thủ. | Mở rộng TC-012 với `null`, `[]`, primitive, `{}`, thiếu coords; log xác nhận an toàn tại `tc012-stimulus-execution.log` | RESOLVED |
| REV-004 | `plan.md` (addendum Step 4); `MapComponent.tsx`: Khi `vehicleTelemetry` là null (sau Reset), gọi `vehicleMarkerRef.current.remove()` và set ref về `null`. | Kiểm chứng browser UI: `tc011-02-reset-state.png` và `tc009-011-demo.webp` cho thấy marker xe cũ hoàn toàn biến mất sau Reset | RESOLVED |
| REV-005 | `SimulatorService.java`: Sử dụng `ALLOWED_MULTIPLIERS.contains(multiplier)` exact matching, loại bỏ epsilon tolerance. | Unit test và MockMvc test xác nhận các giá trị near-whitelist `1.0000005`, `9.9999995` bị từ chối với 400 Bad Request ProblemDetail | RESOLVED |
| REV-006 | `evidence.md` & `artifacts/`: Thu thập đầy đủ log thực thi broker stimulus (`tc009-stimulus-execution.log`, `tc012-stimulus-execution.log`), frontend verification log, screenshots và video browser mới. | Toàn bộ log và artifact được lưu trữ đầy đủ trong `docs/features/005-realtime-vehicle-simulator/artifacts/` | RESOLVED |

## Vấn đề còn tồn tại

| Vấn đề | Severity/Rủi ro | Quyết định | Người xác nhận |
|---|---|---|---|
| Máy review không có JDK 26 | Verification limitation | Gemini phải cung cấp artifact Maven mới sau khi sửa; Codex rerun lại nếu môi trường có JDK phù hợp | Codex |
| Local browser/backend không chạy | Verification limitation | Cần artifacts realtime tái lập được theo REV-006 | Gemini |
| Marker Reset cần chỉnh `MapComponent`, chưa có trong Plan inventory | Scope/Plan gap nhỏ | Cập nhật addendum Step 4 trước code fix | Gemini/Codex |

## Findings bắt buộc trước lần review tiếp theo

- `REV-001` — Đảm bảo Reset không thể để tick run cũ persist/publish sau response; thêm test race deterministic.
- `REV-002` — Sửa terminal transition khi persistence lỗi để không false-complete/kẹt session; thêm assertion failure state.
- `REV-003` — Validate runtime shape của telemetry trước callback và test valid JSON malformed vẫn recovery được snapshot hợp lệ.
- `REV-004` — Xóa hoặc thay bằng vị trí START marker cũ sau Reset; chứng minh qua test/manual artifact.
- `REV-005` — Enforce exact multiplier whitelist ở service/API và test các giá trị gần whitelist.
- `REV-006` — Điều chỉnh EVD-016/EVD-017 theo kết quả thực tế, lưu log browser/STOMP và screenshot/video phù hợp; không giữ PASS khi chưa chứng minh.

## Kết luận cuối cùng

`CHANGES REQUESTED`. Implementation có nền tảng tốt cho lifecycle/DTO/two-topic và frontend compile, nhưng chưa đạt các business rule về atomic Reset, terminal failure, realtime malformed input và UI state sau Reset. Các claim `PASS` của EVD-016/EVD-017 phải được hạ trạng thái/cập nhật theo kết quả thực tế trước khi feature có thể được review lại. Sau khi Gemini xử lý sáu finding, chạy lại test/evidence được yêu cầu và báo cáo diff mới, Codex sẽ re-review.

## Checklist đóng review

- [x] Mỗi finding có severity, vị trí, Requirement/Spec/Test/Evidence, ảnh hưởng và đề xuất.
- [x] Requirement, Spec, Test-Plan, Plan, source/diff và Evidence Matrix đã được đối chiếu.
- [x] Evidence quan trọng đã được kiểm tra độc lập; giới hạn rerun được ghi rõ.
- [x] Regression frontend/diff phù hợp đã chạy; backend/local realtime được đánh dấu INCONCLUSIVE, không suy đoán PASS.
- [x] Findings bắt buộc đã được liệt kê cho `CHANGES REQUESTED`.
- [x] Kết luận phản ánh code, artifacts và Evidence thực tế.
- [x] Không `APPROVED` khi Requirement quan trọng còn FAIL/INCONCLUSIVE.
