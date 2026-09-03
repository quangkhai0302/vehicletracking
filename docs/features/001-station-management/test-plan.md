# Test-Plan — Quản lý trạm đầu, trạm cuối và trạm dừng

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `001-station-management` |
| Requirement/Spec version | `2026-09-03` |
| Trạng thái | `READY` |
| Người viết | `Codex` |
| Người duyệt | `Developer/User` |
| Ngày cập nhật | `2026-09-03` |

## Mục tiêu kiểm thử

Chứng minh station CRUD đáp ứng REST contract, validation/normalization, delete integrity và hành vi UI/map. Rủi ro trọng tâm là duplicate sau normalize, update một phần khi lỗi, xóa station đang được route dùng, UI đóng form khi request fail, marker suy type sai và nội dung popup không an toàn.

## Phạm vi kiểm thử

### Trong phạm vi

- `REQ-001` đến `REQ-006`, các contract và `BR-001` đến `BR-006` trong `spec.md`.
- Backend unit/API/integration test với database test.
- Frontend lint/build và manual browser acceptance cho create/edit/delete/map.

### Ngoài phạm vi

- Routing/ETA/check-in/simulator/traffic/WebSocket.
- Load test quy mô production do chưa có SLA hoặc volume.
- Cross-browser matrix đầy đủ; manual acceptance dùng một Chromium hiện đại.

## Chiến lược kiểm thử

### Unit Test

- Đối tượng: `StationService`.
- Mục tiêu: normalize/duplicate, preserve identity, safe delete và exception status.
- Framework hiện có: JUnit/Mockito từ Spring Boot test dependencies.

### Integration Test

- Boundary: station controller/service/repositories với H2 test database.
- Dependency thật/mocked: Spring MVC/JPA/H2 thật; không có external service.
- Mục tiêu: serialization, Bean Validation, transaction, DB state và route reference.

### API Test

- Endpoint: toàn bộ `/api/stations` hiện có.
- Mục tiêu: status, payload, Problem Details và side effect database.
- Cách tự động hóa: MockMvc/integration tests trong backend.

### UI Test

- Flow/component: `StationModal`, `App` handlers và `MapComponent`.
- Mục tiêu: form prefill/edit, loading/error, confirmation, refresh, type marker và popup safety.
- Công cụ: manual browser + DevTools/ảnh; không thêm test framework ngoài Plan.

### Realtime/Event Test

Không áp dụng vì Spec không thêm event.

### End-to-End/Acceptance Test

- Luồng: tạo ba loại → sửa → thử xóa station in-use → xóa station độc lập → kiểm tra list/map.
- Môi trường: local với backend, frontend và database dev/test.

## Môi trường và điều kiện tiên quyết

| Thành phần | Yêu cầu | Cách chuẩn bị |
|---|---|---|
| Backend runtime | JDK 26 theo `pom.xml` | Chọn JDK 26; xác nhận `java -version`; chạy wrapper qua `bash mvnw` nếu file chưa executable |
| Frontend runtime | Node `^20.19.0 || >=22.12.0` theo Vite 8.2.2 | Chọn Node phù hợp; xác nhận `node --version` |
| Database automated | H2 isolated | Dùng test profile/transaction và fixture riêng |
| Database manual | Profile dev H2 hoặc PostgreSQL local | Khởi động theo cấu hình repository, không ghi secret vào Evidence |
| Frontend/Browser | Chromium hiện đại | Chạy Vite dev server và mở UI quản lý trạm |

Baseline 2026-09-03 chưa đáp ứng runtime: local JDK 17 (`EVD-011`) và Node 18.19.1 (`EVD-013`). Không được ghi test/build PASS cho đến khi chạy lại trên runtime đúng.

## Test Data

| ID | Dữ liệu | Mục đích | Cách tạo/dọn dẹp |
|---|---|---|---|
| `TD-001` | `TST-START`, `TST-STOP`, `TST-END`; tọa độ hợp lệ; radius 60 | Happy path ba type | Tạo qua API/fixture; transaction rollback hoặc delete sau test |
| `TD-002` | Code ` tst-dup ` và `TST-DUP` | Normalize/duplicate | Fixture riêng, rollback |
| `TD-003` | Blank/too-long strings, lat ±90/ngoài biên, lon ±180/ngoài biên, radius 30/150/ngoài biên, invalid enum | Boundary/invalid | Parameterized request, rollback |
| `TD-004` | Station được một `RouteStation` tham chiếu | Delete conflict | Tạo route/station fixture trong transaction, rollback |
| `TD-005` | `<img src=x onerror=window.__stationXss=1>` | Popup output safety | Tạo station test local, xóa sau manual test |

Không dùng dữ liệu production hoặc secret.

## Test Cases

### TC-001 — List và get station qua API

**Loại:** `API`
**Mức ưu tiên:** `High`
**Liên kết:** `REQ-001`, `AC-REQ-001-01`, `SPEC-001`

**Mục tiêu:** Chứng minh GET trả representation đầy đủ và 404 cho id thiếu.

**Precondition:** Có ít nhất hai station fixture khác type.
**Input/Test data:** `TD-001`.

**Steps:**

1. Gọi `GET /api/stations` và `GET /api/stations/{existingId}`.
2. Gọi `GET /api/stations/{missingId}`.

**Expected result:** 200 với đủ field/type cho hai request đầu; request cuối 404 Problem Details; không đổi DB.

**Automation:** `Có`
**Test file dự kiến:** `.../controller/StationControllerTest.java`
**Evidence dự kiến:** `EVD-102`, test output và response assertions.

### TC-002 — Hiển thị danh sách quản lý

**Loại:** `UI`
**Mức ưu tiên:** `High`
**Liên kết:** `REQ-001`, `AC-REQ-001-01`, `SPEC-001`

**Mục tiêu:** Chứng minh UI hiển thị mã, tên, loại, vị trí/địa chỉ và radius.

**Precondition:** Backend có `TD-001`; modal mở.
**Input/Test data:** `TD-001`.

**Steps:** Mở quản lý trạm và đối chiếu từng row với response GET.

**Expected result:** Mọi field yêu cầu xuất hiện đúng; không suy loại theo index.

**Automation:** `Không`
**Test file dự kiến:** Không áp dụng; manual browser.
**Evidence dự kiến:** `EVD-105`, screenshot đã loại dữ liệu nhạy cảm + steps.

### TC-003 — Tạo START, STOP và END

**Loại:** `API`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-002`, `AC-REQ-002-01`, `SPEC-002`, `BR-001`, `BR-003`

**Mục tiêu:** Chứng minh POST lưu đúng ba enum type và trả 201.

**Precondition:** Các code chưa tồn tại.
**Input/Test data:** `TD-001`.

**Steps:** Gửi ba POST hợp lệ; đọc response và DB.

**Expected result:** Mỗi response 201; code normalized; type/field đúng; ba record tồn tại.

**Automation:** `Có`
**Test file dự kiến:** `.../controller/StationControllerTest.java`
**Evidence dự kiến:** `EVD-102`.

### TC-004 — Chuẩn hóa và từ chối duplicate code

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-002`, `REQ-005`, `AC-REQ-002-02`, `SPEC-002`, `SPEC-005`, `BR-002`

**Mục tiêu:** Chứng minh lookup duplicate dùng code đã normalize.

**Precondition:** `TST-DUP` tồn tại.
**Input/Test data:** `TD-002`.

**Steps:** POST code cùng giá trị nhưng khác case/khoảng trắng; kiểm tra response và count.

**Expected result:** 409 Problem Details; count không đổi.

**Automation:** `Có`
**Test file dự kiến:** `StationServiceTest.java`, `StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-005 — Validation create/update

**Loại:** `API`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-005`, `AC-REQ-005-01`, `SPEC-005`, `BR-001`

**Mục tiêu:** Chứng minh backend chặn mọi trường invalid và chấp nhận boundary.

**Precondition:** DB test sạch cho prefix `TST-`.
**Input/Test data:** `TD-003`.

**Steps:** Gửi parameterized POST và PUT cho từng invalid/boundary value.

**Expected result:** Invalid trả 400 Problem Details và DB không đổi; lat ±90, lon ±180, radius 30/150 hợp lệ; enum lạ trả 400.

**Automation:** `Có`
**Test file dự kiến:** `.../controller/StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-006 — Update thành công và giữ identity

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-003`, `AC-REQ-003-01`, `SPEC-003`, `BR-001`–`BR-003`

**Mục tiêu:** Chứng minh PUT thay đổi toàn bộ field editable nhưng giữ id/createdAt.

**Precondition:** Một station tồn tại.
**Input/Test data:** Payload hợp lệ đổi code/name/type/coordinates/address/radius.

**Steps:** Lưu id/createdAt; PUT; GET lại và đọc DB.

**Expected result:** 200; field mới đúng/normalized; id/createdAt không đổi; chỉ một record.

**Automation:** `Có`
**Test file dự kiến:** `StationServiceTest.java`, `StationControllerTest.java`
**Evidence dự kiến:** `EVD-102`.

### TC-007 — Update duplicate không thay đổi một phần

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-003`, `REQ-005`, `AC-REQ-003-02`, `SPEC-003`, `BR-002`

**Mục tiêu:** Chứng minh 409 và atomicity khi code thuộc station khác.

**Precondition:** Hai station khác code.
**Input/Test data:** PUT station A bằng code normalized của B và đổi các field khác.

**Steps:** Gửi PUT; load lại A và B.

**Expected result:** 409; cả A/B giữ nguyên toàn bộ dữ liệu.

**Automation:** `Có`
**Test file dự kiến:** `StationServiceTest.java`, `StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-008 — Update id không tồn tại

**Loại:** `API`
**Mức ưu tiên:** `High`
**Liên kết:** `REQ-003`, `AC-REQ-003-02`, `SPEC-003`

**Mục tiêu:** Chứng minh missing resource trả 404.

**Precondition:** Id test không tồn tại.
**Input/Test data:** Payload hợp lệ.

**Steps:** PUT tới missing id.

**Expected result:** 404 Problem Details; DB không đổi.

**Automation:** `Có`
**Test file dự kiến:** `.../controller/StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-009 — Xóa station độc lập

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-004`, `AC-REQ-004-01`, `SPEC-004`, `BR-004`

**Mục tiêu:** Chứng minh DELETE unreferenced trả 204 và xóa đúng record.

**Precondition:** Station không có `RouteStation`.
**Input/Test data:** Station fixture riêng.

**Steps:** DELETE; GET lại và query repository.

**Expected result:** 204; GET 404; record không còn; record khác không đổi.

**Automation:** `Có`
**Test file dự kiến:** `StationServiceTest.java`, `StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-010 — Từ chối xóa station đang được route dùng

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-004`, `AC-REQ-004-02`, `SPEC-004`, `BR-004`

**Mục tiêu:** Chứng minh route integrity khi delete conflict.

**Precondition:** `TD-004`.
**Input/Test data:** Id station referenced.

**Steps:** DELETE; query station, route và route_station.

**Expected result:** 409 Problem Details; cả ba dữ liệu vẫn tồn tại và quan hệ không đổi.

**Automation:** `Có`
**Test file dự kiến:** `StationServiceTest.java`, `StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-011 — Xóa id không tồn tại

**Loại:** `API`
**Mức ưu tiên:** `High`
**Liên kết:** `REQ-004`, `SPEC-004`, `BR-004`

**Mục tiêu:** Chứng minh DELETE lần hai/missing id trả 404.

**Precondition:** Id không tồn tại.
**Input/Test data:** Missing id.

**Steps:** DELETE missing id.

**Expected result:** 404 Problem Details; không đổi DB.

**Automation:** `Có`
**Test file dự kiến:** `.../controller/StationControllerTest.java`
**Evidence dự kiến:** `EVD-103`.

### TC-012 — UI create/edit/delete và trạng thái lỗi

**Loại:** `E2E`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-002`–`REQ-006`, các AC create/update/delete/UI, `SPEC-006`, `BR-005`

**Mục tiêu:** Chứng minh flow người dùng và state chỉ đổi sau success.

**Precondition:** Frontend/backend chạy; có một station độc lập và một station in-use.
**Input/Test data:** `TD-001`, `TD-002`, `TD-004`.

**Steps:**

1. Tạo station từ map click và từ tọa độ form; quan sát loading/success.
2. Chọn edit, kiểm tra prefill, sửa mọi field và lưu.
3. Gửi duplicate để tạo lỗi; kiểm tra form giữ nguyên.
4. Bấm delete, hủy confirmation; sau đó xác nhận station in-use và station độc lập.

**Expected result:** Create/edit/delete hợp lệ cập nhật list/map; cancel không gọi API; conflict giữ dữ liệu/form và báo lỗi; nút bị disable khi request pending.

**Automation:** `Không`
**Test file dự kiến:** Không áp dụng; manual browser.
**Evidence dự kiến:** `EVD-105`, screenshots + Network status + steps/actual.

### TC-013 — Marker dựa trên stationType, không dựa trên index

**Loại:** `UI`
**Mức ưu tiên:** `High`
**Liên kết:** `REQ-006`, `AC-REQ-006-01`, `SPEC-006`, `BR-003`

**Mục tiêu:** Chứng minh render đúng type ở thứ tự bất kỳ.

**Precondition:** Danh sách theo thứ tự `STOP, END, START`.
**Input/Test data:** Ba station khác type.

**Steps:** Mở map, đối chiếu class/style/nhãn từng marker với response GET; sửa type và kiểm tra lại.

**Expected result:** Mỗi marker theo `stationType`; thay đổi type xuất hiện sau refresh; index không ảnh hưởng.

**Automation:** `Không`
**Test file dự kiến:** Không áp dụng; manual browser/DevTools.
**Evidence dự kiến:** `EVD-106`.

### TC-014 — Popup không thực thi HTML từ station

**Loại:** `UI`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-006`, `AC-REQ-006-02`, `SPEC-006`, `BR-006`

**Mục tiêu:** Chứng minh output encoding ở popup.

**Precondition:** `window.__stationXss` chưa tồn tại; station có `TD-005`.
**Input/Test data:** `TD-005` trong name/address/code phù hợp giới hạn.

**Steps:** Mở popup; kiểm tra DOM/text và biến sentinel/console.

**Expected result:** Chuỗi xuất hiện như text; không tạo element/event handler từ input; sentinel không đổi; CSP không phải hàng rào duy nhất.

**Automation:** `Không`
**Test file dự kiến:** Không áp dụng; manual browser/DevTools.
**Evidence dự kiến:** `EVD-106`, screenshot DOM + observation.

### TC-015 — Backend regression suite

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-001`–`REQ-005`, toàn bộ backend Spec.

**Mục tiêu:** Phát hiện regression backend và chạy tất cả station tests.

**Precondition:** JDK 26, dependencies sẵn có.
**Input/Test data:** Test fixtures.

**Steps:** Chạy `bash mvnw clean test`.

**Expected result:** Exit 0; tất cả tests chạy/pass; không có test bị skip ngoài chủ đích đã ghi.

**Automation:** `Có`
**Test file dự kiến:** Toàn bộ `src/test`.
**Evidence dự kiến:** `EVD-101`, Maven summary.

### TC-016 — Frontend lint và production build

**Loại:** `Integration`
**Mức ưu tiên:** `Critical`
**Liên kết:** `REQ-001`–`REQ-006`, `SPEC-006`

**Mục tiêu:** Chứng minh TypeScript/bundler chấp nhận thay đổi và lint không có lỗi mới.

**Precondition:** Node theo Vite engine, dependency đã cài.
**Input/Test data:** Source frontend.

**Steps:** Chạy `npm run lint`, sau đó `npm run build`.

**Expected result:** Cả hai exit 0; warning baseline được ghi riêng, không có warning/error mới do feature.

**Automation:** `Có`
**Test file dự kiến:** Không áp dụng; scripts trong `package.json`.
**Evidence dự kiến:** `EVD-104`, command output.

## Ma trận tình huống cần xem xét

| Tình huống | Áp dụng? | Test Case | Lý do/Ghi chú |
|---|---|---|---|
| Happy path | Có | `TC-001`, `TC-003`, `TC-006`, `TC-009`, `TC-012` | CRUD chính |
| Invalid input | Có | `TC-005`, `TC-012` | Backend invariant/UI error |
| Boundary value | Có | `TC-005` | Coordinates/radius/length |
| Duplicate request/event | Có | `TC-004`, `TC-007` | Normalized code; event không áp dụng |
| Missing data/not found | Có | `TC-001`, `TC-008`, `TC-011` | 404 contract |
| Permission/authentication | Không | Không áp dụng | Không đổi cơ chế auth trong feature |
| Concurrency/race condition | Có | `TC-004`, `TC-007`, `TC-010` | DB constraint/transaction là hàng rào; không load test |
| Timeout | Có | `TC-012` | Network failure/pending UI manual |
| External service failure | Không | Không áp dụng | Không có external service |
| Database failure/rollback | Có | `TC-007`, `TC-010` | Không partial update/delete |
| Reconnect/out-of-order event | Không | Không áp dụng | Không có event |
| Regression | Có | `TC-015`, `TC-016` | Hai module bị thay đổi |

## Acceptance Test

| Acceptance Criteria | Test Case | Cách chạy | Evidence | Trạng thái Evidence |
|---|---|---|---|---|
| `AC-REQ-001-01` | `TC-001`, `TC-002` | Backend API test + manual UI | `EVD-102`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-002-01` | `TC-003`, `TC-012` | API test + E2E | `EVD-102`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-002-02` | `TC-004`, `TC-005`, `TC-012` | API/integration + E2E | `EVD-103`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-003-01` | `TC-006`, `TC-012` | Integration + E2E | `EVD-102`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-003-02` | `TC-007`, `TC-008` | Integration/API | `EVD-103` | `INCONCLUSIVE` |
| `AC-REQ-004-01` | `TC-009`, `TC-012` | Integration + E2E | `EVD-103`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-004-02` | `TC-010`, `TC-012` | Integration + E2E | `EVD-103`, `EVD-105` | `INCONCLUSIVE` |
| `AC-REQ-005-01` | `TC-005` | Parameterized API test | `EVD-103` | `INCONCLUSIVE` |
| `AC-REQ-006-01` | `TC-012`, `TC-013` | E2E/manual map | `EVD-105`, `EVD-106` | `INCONCLUSIVE` |
| `AC-REQ-006-02` | `TC-014` | Manual DOM inspection | `EVD-106` | `INCONCLUSIVE` |

## Regression Test

| Khu vực có nguy cơ ảnh hưởng | Test hiện có cần chạy | Lý do |
|---|---|---|
| Backend toàn module | `bash mvnw clean test` | Entity/repository/service dùng chung với route/trip |
| Frontend toàn module | `npm run lint`, `npm run build` | App/MapComponent/api là shared files |
| Route data integrity | `TC-010` và existing backend tests | Delete station liên quan FK route |
| Map display | `TC-013`, `TC-014` | Marker/popup bị sửa |

## Lệnh kiểm tra

| Command | Mục đích | Điều kiện PASS | Evidence output |
|---|---|---|---|
| `java -version` | Xác nhận JDK | Hiển thị JDK 26 | Ghi trong `EVD-101` |
| `bash mvnw clean test` | Backend tests | Exit 0, tất cả test pass | `EVD-101` |
| `node --version` | Xác nhận Node | Khớp Vite engine | Ghi trong `EVD-104` |
| `npm run lint` | Frontend lint | Exit 0, không lỗi/warning mới | `EVD-104` |
| `npm run build` | Frontend build/type/bundle | Exit 0 | `EVD-104` |

Working directory tương ứng là `vehiceltracking-backend` hoặc `vehicletracking-frontend`. Không dùng `npm test` vì repository chưa có script đó.

## Quy tắc Evidence

Với mỗi Test Case critical, Gemini phải cập nhật record `EVD-101` đến `EVD-106` trong `evidence.md` bằng command/steps thực tế, runtime, expected, actual, exit code/status và artifact. Source diff không thay thế API/DB/UI evidence. Command không chạy được giữ `INCONCLUSIVE`; assertion failure là `FAIL`.

## Tiêu chí dừng và xử lý lỗi test

- Dừng bàn giao nếu `TC-003`–`TC-016` loại Critical thất bại hoặc còn `INCONCLUSIVE` khi xin review.
- Không sửa expected result để khớp implementation khác Spec.
- Không dùng baseline runtime mismatch để tuyên bố code pass/fail; đổi sang runtime đúng rồi chạy lại.
- Manual evidence phải ghi đủ dữ liệu test, bước và kết quả; screenshot đơn lẻ không chứng minh backend integrity.

## Definition of Done

- [x] Mọi Requirement/Acceptance Criteria có Test Case.
- [x] Happy path và edge/failure case liên quan được bao phủ.
- [x] Test Data và precondition tái lập được.
- [x] Command kiểm tra tồn tại hoặc được Plan bổ sung rõ ràng.
- [x] Acceptance và regression scope được xác định.
- [x] Evidence yêu cầu được mô tả và không chứa secret.
- [ ] Không có Critical Test Case ở trạng thái `FAIL` hoặc Evidence `INCONCLUSIVE` khi đề nghị approve — chờ implementation/verification.
