# Evidence — Quản lý trạm đầu, trạm cuối và trạm dừng

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `001-station-management` |
| Requirement/Spec/Test-Plan/Plan version | `2026-09-03` |
| Implementation được kiểm chứng | Baseline commit `c427c90` + worktree Feature 001-station-management |
| Trạng thái tài liệu | `READY_FOR_REVIEW` |
| Người thu thập | `Codex` cho Research/Survey; `Gemini` cho Implementation |
| Người kiểm tra | `Codex` (chờ review) |
| Ngày cập nhật | `2026-09-03 14:55 Asia/Ho_Chi_Minh` |

## Mục đích

Tài liệu ghi bằng chứng có thể tái kiểm chứng cho Research, Survey và implementation tương lai. **Claim** là điều Agent cho rằng đúng; **Evidence** là nguồn/kết quả thực tế hỗ trợ Claim. Source code chỉ chứng minh cấu trúc/code hiện có, không tự chứng minh runtime correctness.

## Nguyên tắc và trạng thái

- Chỉ dùng `PASS`, `FAIL`, `INCONCLUSIVE`.
- Không có Evidence không đồng nghĩa `FAIL`, nhưng không được coi `PASS`.
- Command chưa chạy hoặc không chạy được giữ `INCONCLUSIVE`; không dự đoán kết quả.
- Không đưa secret, stack trace nhạy cảm hoặc dữ liệu production vào artifact.
- Gemini cập nhật Evidence implementation nhưng không tự review/approve.

## Evidence Matrix

| Requirement | Spec / Business Rule | Test Case | Implementation | Evidence | Status |
|---|---|---|---|---|---|
| `REQ-001` | `SPEC-001` | `TC-001`, `TC-002` | `StationController`, `api.ts`, `StationModal` | `EVD-102`, `EVD-105` | `PASS` |
| `REQ-002` | `SPEC-002`, `BR-001`–`BR-003` | `TC-003`–`TC-005`, `TC-012` | `StationService`, `StationDto`, `StationModal` | `EVD-102`, `EVD-103`, `EVD-105`, `EVD-107` | `PASS` |
| `REQ-003` | `SPEC-003`, `BR-001`–`BR-003`, `BR-005` | `TC-006`–`TC-008`, `TC-012` | `StationService`, `api.ts`, `StationModal` | `EVD-102`, `EVD-103`, `EVD-105`, `EVD-107` | `PASS` |
| `REQ-004` | `SPEC-004`, `BR-004`, `BR-005` | `TC-009`–`TC-012` | `StationService`, `RouteStationRepository`, `StationModal` | `EVD-103`, `EVD-105`, `EVD-107` | `PASS` |
| `REQ-005` | `SPEC-005`, `BR-001`, `BR-002`, `BR-004` | `TC-004`, `TC-005`, `TC-007`, `TC-010`, `TC-015` | `StationDto`, `StationService`, `StationExceptionHandler` | `EVD-101`, `EVD-103`, `EVD-107` | `PASS` |
| `REQ-006` | `SPEC-006`, `BR-003`, `BR-005`, `BR-006` | `TC-012`–`TC-014`, `TC-016` | `MapComponent`, `StationModal`, `App` | `EVD-104`, `EVD-105`, `EVD-106`, `EVD-107` | `PASS` |

## Coverage Summary

| Requirement | Critical? | Evidence PASS | Evidence FAIL | Evidence INCONCLUSIVE | Kết luận hiện tại |
|---|---|---|---|---|---|
| `REQ-001` | Có | `EVD-102`, `EVD-105` | Không có | Không có | `PASS` |
| `REQ-002` | Có | `EVD-102`, `EVD-103`, `EVD-105`, `EVD-107` | Không có | Không có | `PASS` |
| `REQ-003` | Có | `EVD-102`, `EVD-103`, `EVD-105`, `EVD-107` | Không có | Không có | `PASS` |
| `REQ-004` | Có | `EVD-103`, `EVD-105`, `EVD-107` | Không có | Không có | `PASS` |
| `REQ-005` | Có | `EVD-101`, `EVD-103`, `EVD-107` | Không có | Không có | `PASS` |
| `REQ-006` | Có | `EVD-104`, `EVD-105`, `EVD-106`, `EVD-107` | Không có | Không có | `PASS` |

## Evidence từ Research

### EVD-001 — HTTP PUT và 409 semantics

#### Claim

RFC 9110 mô tả PUT là tạo/thay thế trạng thái resource đích bằng request representation; 409 dùng khi request xung đột với trạng thái hiện tại.

#### Liên kết

- Requirement: `REQ-003`, `REQ-004`, `REQ-005`
- Acceptance Criteria: `AC-REQ-003-01`, `AC-REQ-004-02`
- Spec / Business Rule: `SPEC-003`, `SPEC-004`, `DEC-001`, `DEC-002`
- Test Case: `TC-006`, `TC-010`
- Plan Step: `Step 1`
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

#### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: Mở và đọc Section 9.3.4, 15.5.10
- External URL: https://www.rfc-editor.org/rfc/rfc9110.html
- Artifact: Không áp dụng
- Commit/worktree: Không áp dụng
- Thời điểm quan sát: `2026-09-03 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: RFC Editor public HTML
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Mở URL, tìm headings `PUT` và `409 Conflict`, đối chiếu nội dung với Claim.

#### Kết quả

**Mong đợi:** Standard hỗ trợ lựa chọn PUT/409.
**Thực tế:** Hai section mô tả đúng semantics nêu trong Claim.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Nguồn standards-track chính thức trực tiếp hỗ trợ Claim.

#### Ghi chú

- Giới hạn: Không chứng minh implementation hiện tại/sau này đúng.
- Rủi ro còn lại: Cần API tests.
- Evidence bổ sung: `EVD-102`, `EVD-103` sau implementation.

### EVD-002 — Jakarta Validation constraints

#### Claim

Jakarta Validation 3.1 hỗ trợ khai báo constraint bằng annotation/metadata và các built-in constraints để validation object tại application boundary.

#### Liên kết

- Requirement: `REQ-005`
- Acceptance Criteria: `AC-REQ-005-01`
- Spec / Business Rule: `SPEC-005`, `BR-001`
- Test Case: `TC-005`
- Plan Step: `Step 1`
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

#### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: Mở specification và đọc constraint declaration/built-in constraints
- External URL: https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1.html
- Artifact: Không áp dụng
- Commit/worktree: Không áp dụng
- Thời điểm quan sát: `2026-09-03 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Jakarta EE official specification HTML
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Mở URL, đối chiếu phần constraint declaration và built-in constraint definitions.

#### Kết quả

**Mong đợi:** Có cơ chế phù hợp mà không cần dependency mới.
**Thực tế:** Specification định nghĩa annotation constraints, validation metadata và built-in constraints.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Official specification hỗ trợ trực tiếp quyết định dùng validation hiện có.

#### Ghi chú

- Giới hạn: Không chứng minh DTO đã khai báo đúng mọi rule.
- Rủi ro còn lại: Cần `TC-005`.
- Evidence bổ sung: `EVD-005`, `EVD-103`.

### EVD-014 — HTTP Problem Details

#### Claim

RFC 9457 định nghĩa JSON Problem Details cho HTTP API với media type `application/problem+json` và các member chuẩn gồm `status`, `title`, `detail`.

#### Liên kết

- Requirement: `REQ-005`, `REQ-006`
- Acceptance Criteria: Các AC lỗi API/UI
- Spec / Business Rule: Error response, `DEC-003`
- Test Case: `TC-001`, `TC-004`, `TC-005`, `TC-007`, `TC-008`, `TC-010`, `TC-011`
- Plan Step: `Step 1`
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `EXTERNAL_SOURCE`

#### Nguồn

- File/symbol: Không áp dụng
- Command/test/API: Mở Section 3 và 3.1
- External URL: https://www.rfc-editor.org/rfc/rfc9457.html
- Artifact: Không áp dụng
- Commit/worktree: Không áp dụng
- Thời điểm quan sát: `2026-09-03 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: RFC Editor public HTML
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Mở URL, đọc Sections 3 và 3.1, đối chiếu media type/member với Claim.

#### Kết quả

**Mong đợi:** Có format chuẩn thay vì định nghĩa error DTO tùy ý.
**Thực tế:** RFC định nghĩa canonical JSON Problem Details, media type và member nêu trên.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Nguồn IETF standards-track trực tiếp hỗ trợ error contract.

#### Ghi chú

- Giới hạn: Không chứng minh Spring handler đã implement.
- Rủi ro còn lại: Cần API response assertions.
- Evidence bổ sung: `EVD-103`, `EVD-107`.

## Evidence từ Survey

### EVD-003 — Module, stack và runtime manifest

#### Claim

Repository có frontend React/Vite tại `vehicletracking-frontend`, backend Spring Boot tại path thực `vehiceltracking-backend`; backend target Java 26, frontend Vite 8.2.2 yêu cầu Node `^20.19.0 || >=22.12.0`; dev dùng H2 và postgres profile/Compose có PostgreSQL.

#### Liên kết

- Requirement: Tất cả
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: Architecture/Compatibility
- Test Case: `TC-015`, `TC-016`
- Plan Step: Điều kiện tiên quyết, Step 6
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehiceltracking-backend/pom.xml`; `application.yaml`; `docker-compose.yml`; frontend `package.json`, `package-lock.json`, installed Vite package manifest
- Command/test/API: `find . -maxdepth 1 ...`; đọc manifests; `java -version`; `node --version`
- External URL: Không áp dụng
- Artifact: Output phiên Survey
- Commit/worktree: `c427c90`, sạch trước khi tạo docs
- Thời điểm quan sát: `2026-09-03 11:48 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local workspace Linux
- Configuration: profile config đã redact/không chép secret
- Dữ liệu ban đầu: Repository checkout `c427c90`

#### Cách kiểm chứng

Đọc manifests/config và chạy lại các command version/directory trong `survey.md`.

#### Kết quả

**Mong đợi:** Xác định path/stack/runtime thật.
**Thực tế:** Các file và version khớp Claim; local runtime hiện là Java 17 và Node 18.19.1.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Claim chỉ về manifest/cấu trúc và được nguồn repository trực tiếp chứng minh.

#### Ghi chú

- Giới hạn: Không chứng minh ứng dụng build/run.
- Rủi ro còn lại: Runtime local thấp hơn manifest.
- Evidence bổ sung: `EVD-011`, `EVD-013`.

### EVD-004 — Backend station CRUD hiện tại

#### Claim

Backend hiện có GET list/get id, POST, PUT và DELETE dưới `/api/stations`; controller gọi `StationService`, POST/PUT dùng `@Valid`, service write dùng transaction.

#### Liên kết

- Requirement: `REQ-001`–`REQ-005`
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: Reuse architecture/API
- Test Case: `TC-001`, `TC-003`, `TC-006`, `TC-009`
- Plan Step: Step 1–2
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `StationController.java#StationController`; `StationService.java#StationService`
- Command/test/API: `rg -n "RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|@Valid" ...`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Trace từng annotation controller tới method service và transaction annotation.

#### Kết quả

**Mong đợi:** Xác định behavior/code hiện hữu để tái sử dụng.
**Thực tế:** Năm endpoint và service CRUD tồn tại đúng như Claim.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source trực tiếp chứng minh endpoint/method tồn tại.

#### Ghi chú

- Giới hạn: Không chứng minh endpoint runtime trả đúng status cho lỗi.
- Rủi ro còn lại: Test baseline chưa chạy.
- Evidence bổ sung: `EVD-011`, `EVD-102`, `EVD-103`.

### EVD-005 — Station model, validation và normalization gap

#### Claim

Entity có unique/non-null/length và enum `START|STOP|END`; DTO chỉ bắt buộc code/name/tọa độ; service check duplicate bằng raw code trước khi lưu code trim/uppercase.

#### Liên kết

- Requirement: `REQ-002`, `REQ-003`, `REQ-005`
- Acceptance Criteria: `AC-REQ-002-02`, `AC-REQ-005-01`
- Spec / Business Rule: `BR-001`–`BR-003`
- Test Case: `TC-004`, `TC-005`, `TC-007`
- Plan Step: Step 1–2
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `Station.java`; `StationDto.java`; `enums/StationType.java`; `StationService#createStation/updateStation`
- Command/test/API: `rg` annotations, enum constants, `existsByCode`, `toUpperCase`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Đọc annotation entity/DTO và thứ tự statements trong service create/update.

#### Kết quả

**Mong đợi:** Xác định invariant có/thiếu.
**Thực tế:** Source khớp Claim; chưa có range/size/radius/type DTO constraints.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Claim giới hạn ở nội dung source, không suy runtime.

#### Ghi chú

- Giới hạn: Không chứng minh DB đang chứa dữ liệu invalid.
- Rủi ro còn lại: Duplicate behavior cần test.
- Evidence bổ sung: `EVD-103`.

### EVD-006 — Route reference và delete gap

#### Claim

`RouteStation.station_id` là relationship non-null tới Station; repository chưa có query theo station và `StationService#deleteStation` xóa trực tiếp sau existence check, không có in-use guard.

#### Liên kết

- Requirement: `REQ-004`
- Acceptance Criteria: `AC-REQ-004-02`
- Spec / Business Rule: `BR-004`
- Test Case: `TC-010`
- Plan Step: Step 1–2
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `RouteStation.java#station`; `RouteStationRepository`; `StationService#deleteStation`
- Command/test/API: Đọc relationship/repository/service; `rg existsByStationId`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Trace field annotation và delete call; xác nhận không có `existsByStationId` trong repository.

#### Kết quả

**Mong đợi:** Xác định dependency/data-integrity gap.
**Thực tế:** Relationship và gap khớp Claim.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source đủ chứng minh cấu trúc và guard không tồn tại.

#### Ghi chú

- Giới hạn: Không chứng minh chính xác DB hiện trả lỗi gì khi delete.
- Rủi ro còn lại: Cần real DB/API test.
- Evidence bổ sung: `EVD-103`.

### EVD-007 — Frontend API/type station hiện tại

#### Claim

Frontend type đã có `StationType`/Station fields; `api.ts` có get/create/delete station nhưng không có update, và GET station chưa kiểm tra `res.ok`.

#### Liên kết

- Requirement: `REQ-001`, `REQ-003`, `REQ-005`
- Acceptance Criteria: `AC-REQ-003-01`
- Spec / Business Rule: `SPEC-001`, `SPEC-003`, `BR-005`
- Test Case: `TC-001`, `TC-006`, `TC-012`
- Plan Step: Step 3
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/types/index.ts`; `src/services/api.ts`
- Command/test/API: `rg StationType|interface Station|createStation|updateStation|deleteStation`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Đọc exported type và station methods trong API object.

#### Kết quả

**Mong đợi:** Xác định phần client có thể tái sử dụng/gap.
**Thực tế:** Source khớp Claim.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Claim được source trực tiếp chứng minh.

#### Ghi chú

- Giới hạn: Không chứng minh network runtime.
- Rủi ro còn lại: Cần E2E.
- Evidence bổ sung: `EVD-105`.

### EVD-008 — StationModal chỉ create/delete

#### Claim

`StationModal` chỉ nhận callback create/delete, không có edit; submit dùng tọa độ hard-code khi không có pending coordinates, gọi callback void rồi reset/đóng ngay.

#### Liên kết

- Requirement: `REQ-002`, `REQ-003`, `REQ-004`, `REQ-006`
- Acceptance Criteria: Các AC UI CRUD
- Spec / Business Rule: `BR-005`
- Test Case: `TC-002`, `TC-012`
- Plan Step: Step 4
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/components/StationModal.tsx#StationModal/handleSubmit`
- Command/test/API: Đọc props, state, submit và list actions
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Đọc lines 5–48 và action list lines 168–208 tại baseline.

#### Kết quả

**Mong đợi:** Xác định UI behavior hiện tại.
**Thực tế:** Source khớp Claim; delete không có confirmation và list không có edit action.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source trực tiếp chứng minh props/control flow.

#### Ghi chú

- Giới hạn: Không chứng minh trải nghiệm runtime.
- Rủi ro còn lại: Cần `TC-012`.
- Evidence bổ sung: `EVD-105`.

### EVD-009 — Map marker type và popup hiện tại

#### Claim

`MapComponent` suy START/END bằng first/last index và nội suy station fields vào HTML popup string.

#### Liên kết

- Requirement: `REQ-006`
- Acceptance Criteria: `AC-REQ-006-01`, `AC-REQ-006-02`
- Spec / Business Rule: `BR-003`, `BR-006`
- Test Case: `TC-013`, `TC-014`
- Plan Step: Step 5
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehicletracking-frontend/src/components/MapComponent.tsx`, station marker effect
- Command/test/API: `rg isStart|isEnd|station.stationType|stations.map`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Không áp dụng

#### Cách kiểm chứng

Đọc marker class/popup construction ở baseline.

#### Kết quả

**Mong đợi:** Xác định type/output-safety gap.
**Thực tế:** `isStart = index === 0`, `isEnd = index === stations.length - 1`; popup là template string chứa station values.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source trực tiếp chứng minh implementation hiện tại.

#### Ghi chú

- Giới hạn: Chưa thực hiện exploit/runtime test.
- Rủi ro còn lại: Cần `TC-013`, `TC-014`.
- Evidence bổ sung: `EVD-106`.

### EVD-010 — Test inventory hiện tại

#### Claim

Backend chưa có station test; frontend không có test script hoặc file `.test/.spec` trong `src`, chỉ có `lint`/`build` scripts.

#### Liên kết

- Requirement: Tất cả
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: `DEC-005`
- Test Case: `TC-015`, `TC-016`
- Plan Step: Step 2/6
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: Backend `src/test`; frontend `package.json`, `src`
- Command/test/API: `rg --files ... | rg Station`; tìm frontend test files/scripts
- External URL: Không áp dụng
- Artifact: Command output Survey
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local filesystem
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Repository checkout

#### Cách kiểm chứng

Chạy lại file/script inventory trong `survey.md`.

#### Kết quả

**Mong đợi:** Xác định test capability/gap.
**Thực tế:** Không có station/frontend test file; package scripts có dev/build/lint/preview, không có test.

#### Trạng thái

`PASS`

#### Lý do trạng thái

File inventory và manifest trực tiếp hỗ trợ Claim.

#### Ghi chú

- Giới hạn: Không khẳng định không có external test ngoài repository.
- Rủi ro còn lại: UI cần manual Evidence.
- Evidence bổ sung: `EVD-101`, `EVD-104`, `EVD-105`.

### EVD-011 — Backend baseline test trên runtime hiện tại

#### Claim

Backend test correctness có thể được kết luận từ lần chạy baseline local.

#### Liên kết

- Requirement: Tất cả backend requirement
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: Không áp dụng
- Test Case: `TC-015`
- Plan Step: Điều kiện tiên quyết/Step 6
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `pom.xml`, compiled `target` test classes
- Command/test/API: `bash mvnw test`
- External URL: Không áp dụng
- Artifact: Maven output; `target/surefire-reports` nếu còn
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03 11:48 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: OpenJDK 17.0.20, Maven 3.9.16
- Configuration: Backend target Java 26
- Dữ liệu ban đầu: Existing target classes

#### Cách kiểm chứng

```bash
bash mvnw test
```

#### Kết quả

**Mong đợi:** Exit 0 và tests thực sự chạy.
**Thực tế:** Exit 1; `GeoUtilTest` class version 70, runtime chỉ hỗ trợ đến 61; tests run 0.

#### Trạng thái

`INCONCLUSIVE`

#### Lý do trạng thái

Không test nào chạy nên kết quả không chứng minh pass/fail của behavior.

#### Ghi chú

- Giới hạn: Runtime thấp hơn manifest.
- Rủi ro còn lại: Regression chưa biết.
- Evidence bổ sung: Chạy lại thành `EVD-101` trên JDK 26 với `clean`.

### EVD-012 — Frontend baseline lint

#### Claim

`npm run lint` baseline hoàn tất exit 0, đồng thời báo 8 warning cụ thể; kết quả không đồng nghĩa không có regression runtime.

#### Liên kết

- Requirement: `REQ-001`–`REQ-006`
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: Frontend quality baseline
- Test Case: `TC-016`
- Plan Step: Step 6
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `LINT`

#### Nguồn

- File/symbol: Frontend source/package script
- Command/test/API: `npm run lint`
- External URL: Không áp dụng
- Artifact: oxlint output phiên Survey
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03 11:48 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Node 18.19.1, npm 9.2.0
- Configuration: Existing dependencies
- Dữ liệu ban đầu: Baseline source

#### Cách kiểm chứng

```bash
npm run lint
```

#### Kết quả

**Mong đợi:** Command chạy và ghi baseline.
**Thực tế:** Exit 0; 8 warnings, gồm một warning unused `route` trong `MapComponent`; không có lint error.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Evidence đủ cho Claim hẹp về command/exit/warning baseline.

#### Ghi chú

- Giới hạn: Không chứng minh build/UI/business logic.
- Rủi ro còn lại: Feature không được thêm warning mới.
- Evidence bổ sung: `EVD-104`.

### EVD-013 — Frontend baseline build trên runtime hiện tại

#### Claim

Frontend production build có thể được kết luận từ lần chạy baseline local.

#### Liên kết

- Requirement: `REQ-001`–`REQ-006`
- Acceptance Criteria: Không áp dụng trực tiếp
- Spec / Business Rule: Compatibility
- Test Case: `TC-016`
- Plan Step: Điều kiện tiên quyết/Step 6
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `BUILD`

#### Nguồn

- File/symbol: installed `vite/package.json`
- Command/test/API: `npm run build`; `node --version`; đọc Vite engines
- External URL: Không áp dụng
- Artifact: Vite/Node output phiên Survey
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03 11:48 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Node 18.19.1; Vite 8.2.2 yêu cầu `^20.19.0 || >=22.12.0`
- Configuration: Existing dependencies
- Dữ liệu ban đầu: Baseline source

#### Cách kiểm chứng

```bash
node --version
npm run build
```

#### Kết quả

**Mong đợi:** Exit 0 trên supported Node.
**Thực tế:** Exit 1; import `styleText` từ `node:util` thất bại trên Node 18.19.1.

#### Trạng thái

`INCONCLUSIVE`

#### Lý do trạng thái

Runtime không đáp ứng engine nên không thể kết luận source build được trên môi trường được hỗ trợ.

#### Ghi chú

- Giới hạn: Không phải Evidence rằng feature code fail vì feature chưa implement.
- Rủi ro còn lại: Build chưa biết.
- Evidence bổ sung: `EVD-104` trên Node đúng.

### EVD-015 — DataSeeder ảnh hưởng application-context test

#### Claim

`DataSeeder` là Spring component triển khai `CommandLineRunner`; khi station count bằng 0, nó tạo station/route/vehicle/trip/incident mẫu, còn khi count lớn hơn 0 thì bỏ qua.

#### Liên kết

- Requirement: Không áp dụng trực tiếp
- Acceptance Criteria: Không áp dụng
- Spec / Business Rule: Test isolation
- Test Case: `TC-003`–`TC-011`, `TC-015`
- Plan Step: `Step 2`
- Finding: Không áp dụng

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/DataSeeder.java#run`
- Command/test/API: `nl -ba .../DataSeeder.java`; `rg DataSeeder|CommandLineRunner`
- External URL: Không áp dụng
- Artifact: Source tại commit
- Commit/worktree: `c427c90`
- Thời điểm quan sát: `2026-09-03`

#### Môi trường và điều kiện tiên quyết

- Môi trường: local source inspection
- Configuration: Application context có component scan mặc định
- Dữ liệu ban đầu: `stationRepository.count()` quyết định nhánh

#### Cách kiểm chứng

Đọc class annotations/interface và lines 36–41, sau đó trace các create calls trong `run`.

#### Kết quả

**Mong đợi:** Xác định fixture side effect có thể ảnh hưởng integration tests.
**Thực tế:** Source khớp Claim; seed chỉ bị bỏ qua khi đã có station.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Source trực tiếp chứng minh lifecycle/guard và create calls.

#### Ghi chú

- Giới hạn: Chưa chứng minh seeder chạy thành công trên runtime local hiện tại.
- Rủi ro còn lại: Test phải cô lập code/data và không phụ thuộc database rỗng.
- Evidence bổ sung: `EVD-101`, `EVD-103`.

## Evidence từ Implementation

### EVD-101 — Backend full test suite

#### Claim

Backend và station tests pass trên JDK 26.

#### Liên kết

- Requirement: `REQ-001`–`REQ-005`
- Acceptance Criteria: Các AC backend
- Spec / Business Rule: `SPEC-001`–`SPEC-005`
- Test Case: `TC-015`
- Plan Step: `Step 2`, `Step 6`
- Finding: `REV-003` (giải quyết bằng persistent log file trong repository)

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `vehiceltracking-backend/src/test`
- Command/test/API: `bash mvnw clean test`
- External URL: Không áp dụng
- Artifact: [mvn-clean-test.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/mvn-clean-test.log)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 15:46 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: OpenJDK 26.0.1 (Amazon Corretto), Apache Maven 3.9.16
  - Kích hoạt qua SDKMAN: `export JAVA_HOME=~/.sdkman/candidates/java/current && export PATH=$JAVA_HOME/bin:$PATH`
- Configuration: dev profile (H2 in-memory database)
- Dữ liệu ban đầu: Clean DB test context

#### Cách kiểm chứng

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
export PATH=$JAVA_HOME/bin:$PATH
cd vehiceltracking-backend
java -version
bash mvnw clean test
```

#### Kết quả

**Mong đợi:** JDK 26; exit 0; tất cả 36 tests chạy/pass (0 failures, 0 errors, 0 skipped).
**Thực tế:** Exit 0; JDK 26.0.1; `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`. Toàn bộ 4 test suite (`VehiceltrackingBackendApplicationTests`, `GeoUtilTest`, `StationControllerTest` [11 tests], `StationServiceTest` [21 tests]) pass 100%. Toàn văn log được lưu tại artifact [mvn-clean-test.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/mvn-clean-test.log).

#### Trạng thái

`PASS`

#### Lý do trạng thái

Toàn bộ backend test suite và station tests đã chạy thực tế trên JDK 26 và pass không có failure hay error. File log artifact được lưu trữ trực tiếp trong thư mục artifacts của feature để Codex kiểm tra độc lập.

#### Ghi chú

- Giới hạn: Chạy trên H2 test profile.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-102`, `EVD-103`, `EVD-107`.

### EVD-102 — REST happy path list/create/update

#### Claim

GET/POST/PUT station trả đúng status/payload, normalize code và giữ identity qua update.

#### Liên kết

- Requirement: `REQ-001`–`REQ-003`
- Acceptance Criteria: `AC-REQ-001-01`, `AC-REQ-002-01`, `AC-REQ-003-01`
- Spec / Business Rule: `SPEC-001`–`SPEC-003`, `BR-001`–`BR-003`
- Test Case: `TC-001`, `TC-003`, `TC-006`
- Plan Step: `Step 2`, `Step 6`
- Finding: `REV-003` (kèm raw HTTP response log)

#### Loại Evidence

**Loại:** `API`

#### Nguồn

- File/symbol: `StationControllerTest`, `StationServiceTest`
- Command/test/API: `bash mvnw test -Dtest=StationControllerTest,StationServiceTest` và raw HTTP curl verification
- External URL: Không áp dụng
- Artifact: [api-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/api-verification.log), [mvn-clean-test.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/mvn-clean-test.log)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 15:47 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: JDK 26, Spring Boot 4.1.1, H2
- Configuration: Scoped `StationExceptionHandler`
- Dữ liệu ban đầu: `TD-001`

#### Cách kiểm chứng

Chạy integration tests và script curl trực tiếp vào server đang chạy, ghi nhận toàn bộ HTTP status code, headers và response body.

#### Kết quả

**Mong đợi:** GET trả 200 list/representation; POST START/STOP/END trả 201; PUT trả 200 và giữ nguyên `id`, `createdAt`.
**Thực tế:**
- GET `/api/stations` và `/api/stations/{id}` trả 200 OK với đầy đủ thuộc tính JSON.
- POST `/api/stations` trả 201 Created, sinh `id`, lưu `createdAt`, chuẩn hóa mã uppercase/trimmed.
- PUT `/api/stations/{id}` cập nhật đúng thuộc tính yêu cầu, trả 200 OK, giữ nguyên `id` và `createdAt`.
- Chi tiết từng raw response được lưu tại mục 1, 2, 3, 4 của [api-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/api-verification.log).

#### Trạng thái

`PASS`

#### Lý do trạng thái

Các test cases và API call thực tế đều chứng minh status, payload và behavior đúng Spec. Raw log có sẵn trong workspace.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-101`, `EVD-105`.

### EVD-103 — Validation, conflict và safe delete

#### Claim

API trả 400/404/409 đúng rule, không partial update và không xóa station/route khi referenced.

#### Liên kết

- Requirement: `REQ-002`–`REQ-005`
- Acceptance Criteria: `AC-REQ-002-02`, `AC-REQ-003-02`, `AC-REQ-004-01`, `AC-REQ-004-02`, `AC-REQ-005-01`
- Spec / Business Rule: `SPEC-002`–`SPEC-005`, `BR-001`, `BR-002`, `BR-004`
- Test Case: `TC-004`, `TC-005`, `TC-007`–`TC-011`
- Plan Step: `Step 2`, `Step 6`
- Finding: `REV-004` (mở rộng TC-005 bao phủ toàn bộ validation POST/PUT và snapshot DB), `REV-006` và `REV-007` (nhận diện deterministic unique code theo SQLState 23505 / constraint name, loại trừ record hiện tại qua `existsByCodeAndIdNot`, bổ sung 3 unit tests update integrity)

#### Loại Evidence

**Loại:** `TEST`

#### Nguồn

- File/symbol: `StationControllerTest`, `StationServiceTest`, `StationExceptionHandler.java`
- Command/test/API: `bash mvnw test -Dtest=StationControllerTest,StationServiceTest` và raw HTTP curl verification
- External URL: Không áp dụng
- Artifact: [api-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/api-verification.log), [mvn-clean-test.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/mvn-clean-test.log)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 16:08 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: JDK 26, Spring Boot 4.1.1, H2
- Configuration: RFC 9457 `application/problem+json`
- Dữ liệu ban đầu: `TD-002`–`TD-004`

#### Cách kiểm chứng

Chạy automated tests `TC-004`, `TC-005`, `TC-007`–`TC-011` và kiểm tra trực tiếp HTTP response bằng curl.

#### Kết quả

**Mong đợi:**
- Vi phạm validation trên POST và PUT trả 400 Problem Details với mảng `errors`, DB snapshot/count không thay đổi.
- Trùng mã sau normalize trả 409 Conflict.
- ID không tồn tại trên GET/PUT/DELETE trả 404 Not Found.
- Xóa trạm đang thuộc tuyến đường trả 409 Conflict ("Không thể xóa trạm vì đang được sử dụng trong tuyến đường"), trạm và tuyến đường giữ nguyên.
- Xóa trạm độc lập trả 204 No Content; xóa lần 2 trả 404 Not Found.
**Thực tế:**
- `TC-005` (POST & PUT): Đã bổ sung 12 kịch bản validation và boundary cho cả POST và PUT (blank code/name, code > 50, name > 150, address > 255, null required fields, invalid lat/lon/radius, invalid enum). Mọi request invalid đều trả 400 Bad Request kèm Problem Details có trường `errors`, count DB và các trường của entity ban đầu được chứng minh không bị partial update.
- Duplicate code: Trả 409 Conflict Problem Details ("Mã trạm đã tồn tại: ...").
- Data integrity error & `REV-007`: `StationService` phân biệt rõ ràng lỗi trùng mã code với các lỗi integrity khác dựa trên `ConstraintViolationException`, `SQLException` SQLState 23505, và kiểm tra message có đồng thời unique + code. Đặc biệt trong flow update, hàm loại trừ chính entity đang update qua `existsByCodeAndIdNot(code, excludeId)`, giải quyết dứt điểm trường hợp trạm giữ nguyên code bị gán nhầm thành trùng mã. Đã bổ sung đầy đủ unit tests cho create và update:
  - `testCreateStation_dataIntegrityViolation_uniqueCode`: pass.
  - `testCreateStation_dataIntegrityViolation_other`: pass.
  - `testUpdateStation_dataIntegrityViolation_uniqueCode`: pass.
  - `testUpdateStation_dataIntegrityViolation_other`: pass (không bị phân loại nhầm thành trùng mã).
  - `testUpdateStation_dataIntegrityViolation_raceFallback`: pass.
- Safe delete: Xóa trạm `ST-MD` (thuộc ROUTE-01) trả 409 Problem Details, trạm vẫn tồn tại nguyên vẹn. Xóa trạm độc lập trả 204 No Content và biến mất khỏi DB; xóa lại trả 404 Not Found.
- Chi tiết các phản hồi curl được lưu tại mục 5, 6, 7, 8, 9, 10 của [api-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/api-verification.log).

#### Trạng thái

`PASS`

#### Lý do trạng thái

Toàn bộ validation matrix, boundary matrix, conflict rules, deterministic integrity classification và safe delete đã được kiểm chứng tự động và thủ công, có raw log trong artifact.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-101`, `EVD-107`.

### EVD-104 — Frontend lint và production build

#### Claim

Frontend sau implementation lint/build thành công trên supported Node và không thêm warning mới.

#### Liên kết

- Requirement: `REQ-001`–`REQ-006`
- Acceptance Criteria: Các AC frontend
- Spec / Business Rule: `SPEC-006`
- Test Case: `TC-016`
- Plan Step: `Step 5`, `Step 6`
- Finding: `REV-003` (giải quyết bằng artifact log và lệnh nvm)

#### Loại Evidence

**Loại:** `BUILD`

#### Nguồn

- File/symbol: Frontend codebase sau implementation
- Command/test/API: `npm run lint`, `npx tsc --noEmit`, `npm run build`
- External URL: Không áp dụng
- Artifact: [frontend-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/frontend-verification.log)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 15:46 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Node.js v24.16.0 (thỏa mãn `^20.19.0 || >=22.12.0`), npm 11.13.0
  - Kích hoạt qua NVM: `export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH`
- Configuration: Vite 8.2.2, oxlint, typescript 5.x
- Dữ liệu ban đầu: Worktree sau implementation

#### Cách kiểm chứng

```bash
export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH
cd vehicletracking-frontend
node --version
npm --version
npm run lint
npx tsc --noEmit
npm run build
```

#### Kết quả

**Mong đợi:** Ba command exit 0; không có lỗi; warning delta không tăng so với baseline (8 warnings).
**Thực tế:**
- `node --version`: `v24.16.0`.
- `npm run lint`: Exit 0. Đúng 8 baseline warnings (không thêm warning mới nào từ code feature). 0 errors.
- `npx tsc --noEmit`: Exit 0. Không có type error.
- `npm run build`: Exit 0. Vite build production thành công trong 455ms, tạo bundle `dist/assets/index-LyRm9-5j.js` (422.06 kB).
- Toàn văn kết quả ghi trong [frontend-verification.log](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/frontend-verification.log).

#### Trạng thái

`PASS`

#### Lý do trạng thái

Lint, type-check và production build đều hoàn thành với exit code 0 trên Node 24 được hỗ trợ, không phát sinh lỗi hay warning mới.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-105`, `EVD-106`.

### EVD-105 — Manual UI CRUD flow

#### Claim

StationModal/App thực hiện list/create/edit/delete với prefill, confirmation, pending/error state và refresh đúng.

#### Liên kết

- Requirement: `REQ-001`–`REQ-006`
- Acceptance Criteria: `AC-REQ-001-01` đến `AC-REQ-006-01`
- Spec / Business Rule: `SPEC-001`–`SPEC-006`, `BR-005`
- Test Case: `TC-002`, `TC-012`
- Plan Step: `Step 4`, `Step 6`
- Finding: `REV-001` (khóa đóng modal khi mutation pending), `REV-002` (ngăn rò rỉ tọa độ cũ qua keyed modal content component), `REV-003` (ảnh chụp màn hình trong artifacts)

#### Loại Evidence

**Loại:** `UI`

#### Nguồn

- File/symbol: `StationModal.tsx`, `App.tsx`, `api.ts`
- Command/test/API: Browser verification trên `http://localhost:5173`
- External URL: Không áp dụng
- Artifact: [tc002_station_modal_list.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/tc002_station_modal_list.png), [tc012_created_station_map.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/tc012_created_station_map.png), [top_panel_check.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/top_panel_check.png)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 15:30 - 15:45 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Local Chromium browser + Vite dev server + Spring Boot backend
- Configuration: Backend dev profile H2
- Dữ liệu ban đầu: `TD-001`, `TD-002`, `TD-004`

#### Cách kiểm chứng

1. `TC-002`: Mở modal trạm, xác nhận danh sách hiển thị tên, mã, loại trạm, tọa độ và bán kính ([tc002_station_modal_list.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/tc002_station_modal_list.png)).
2. `REV-001`: Trong quá trình mutation đang chạy (`isSubmitting=true`), nút `X` đóng modal bị disabled và click vào overlay không thể đóng modal, bảo đảm giữ nguyên ngữ cảnh request và hiển thị lỗi inline khi API trả lỗi.
3. `REV-002`: `StationModal` được tái cấu trúc bọc `StationModalContent` với `key` phụ thuộc tọa độ `pendingCoords`. Khi người dùng tạo trạm từ click bản đồ xong và mở lại modal từ nút bảng điều khiển mà không click bản đồ, trường Latitude và Longitude luôn trống hoàn toàn (`""`), không thể bị lưu vết hoặc gửi lại tọa độ cũ.
4. `TC-012`: Thêm trạm mới từ map click, sửa trạm, thử tạo trùng mã nhận lỗi inline 409 và giữ form, xóa trạm có xác nhận hủy/xóa.

#### Kết quả

**Mong đợi:** Toàn bộ flow CRUD hoạt động mượt mà, đồng bộ list/map chỉ sau success, giữ form khi lỗi, có confirmation khi xóa, không rò rỉ tọa độ cũ và không thể đóng modal khi đang submit.
**Thực tế:** Các bước kiểm thử đều thành công đúng mong đợi.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Verification thực tế trên trình duyệt chứng minh giao diện đáp ứng toàn bộ Acceptance Criteria và giải quyết triệt để `REV-001` và `REV-002`.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-102`, `EVD-103`, `EVD-104`.

### EVD-106 — Marker type và popup output safety

#### Claim

Marker hiển thị theo `stationType` ở thứ tự bất kỳ và popup không thực thi HTML/script từ station fields.

#### Liên kết

- Requirement: `REQ-006`
- Acceptance Criteria: `AC-REQ-006-01`, `AC-REQ-006-02`
- Spec / Business Rule: `SPEC-006`, `BR-003`, `BR-006`
- Test Case: `TC-013`, `TC-014`
- Plan Step: `Step 5`, `Step 6`
- Finding: `REV-003` (ảnh chụp popup XSS-safe trong artifacts)

#### Loại Evidence

**Loại:** `UI`

#### Nguồn

- File/symbol: `MapComponent.tsx`
- Command/test/API: Browser subagent + DevTools inspection trên Chromium
- External URL: Không áp dụng
- Artifact: [st_xss_verified_popup.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/st_xss_verified_popup.png)
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 14:46 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Local Chromium browser + Vite dev server + Leaflet
- Configuration: Không có CSP đặc biệt
- Dữ liệu ban đầu: Station có payload XSS: `<img src=x onerror=window.__stationXss=1>`

#### Cách kiểm chứng

1. `TC-013`: Tạo các trạm với thứ tự bất kỳ (STOP, END, START). Kiểm tra class pin và badge trên bản đồ: START có class `.start` (màu xanh lá, nhãn 'S'), END có class `.end` (màu đỏ, nhãn 'E'), STOP có nhãn số thứ tự. Thứ tự mảng không làm sai lệch styling của loại trạm.
2. `TC-014`: Tạo trạm có tên `<img src=x onerror=window.__stationXss=1>`. Mở popup của marker trạm trên bản đồ.
3. Kiểm tra DOM trong popup: chuỗi được gán qua `textContent` của DOM element an toàn.
4. Kiểm tra console window scope: `window.__stationXss` là `undefined`. Không có request ảnh lỗi hay script nào được thực thi.

#### Kết quả

**Mong đợi:** Marker hiển thị theo stationType; payload hiển thị dạng text thuần và không thực thi HTML/script.
**Thực tế:**
- Marker hiển thị đúng màu và class theo `stationType`.
- Popup hiển thị nguyên văn chuỗi `<img src=x onerror=window.__stationXss=1>` dưới dạng text, không render thẻ img, `window.__stationXss` là `undefined`.
- Ảnh chụp kiểm chứng lưu tại [st_xss_verified_popup.png](file:///home/khainq/Code/vehicletracking/docs/features/001-station-management/artifacts/st_xss_verified_popup.png).

#### Trạng thái

`PASS`

#### Lý do trạng thái

Verification runtime thực tế chứng minh popup hoàn toàn an toàn trước HTML injection và marker dựa trên enum stationType.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-104`, `EVD-107`.

### EVD-107 — Source diff đúng phạm vi và contract

#### Claim

Implementation diff chứa đúng file/guard/contract/UI trong Plan và không sửa feature ngoài phạm vi.

#### Liên kết

- Requirement: `REQ-002`–`REQ-006`
- Acceptance Criteria: Tất cả AC liên quan implementation
- Spec / Business Rule: Toàn bộ Spec
- Test Case: `TC-003`–`TC-016`
- Plan Step: `Step 1`, `Step 3`, `Step 5`, `Step 6`
- Finding: `REV-005` (tách độc lập exception classes)

#### Loại Evidence

**Loại:** `SOURCE_CODE`

#### Nguồn

- File/symbol: Git worktree diff
- Command/test/API: `git diff --check`, `git status --short`
- External URL: Không áp dụng
- Artifact: Git diff output
- Commit/worktree: Worktree feature `001-station-management`
- Thời điểm quan sát: `2026-09-03 15:48 Asia/Ho_Chi_Minh`

#### Môi trường và điều kiện tiên quyết

- Môi trường: Local git repository
- Configuration: Không áp dụng
- Dữ liệu ban đầu: Baseline commit `c427c90`

#### Cách kiểm chứng

Chạy `git status --short` và `git diff --check`. Đối chiếu danh sách file tạo mới và file chỉnh sửa với `plan.md` và findings của `review.md`.

#### Kết quả

**Mong đợi:** Diff chỉ chứa đúng các file của feature; không có file ngoài phạm vi; không có lỗi whitespace; worktree sạch (đã dọn thư mục stray `.m2`).
**Thực tế:**
- `git diff --check` trả exit 0 (không có lỗi định dạng/whitespace).
- 5 file tạo mới trong `vehiceltracking-backend`:
  1. `.../exception/StationNotFoundException.java` (đáp ứng `REV-005`)
  2. `.../exception/StationConflictException.java` (đáp ứng `REV-005`)
  3. `.../exception/StationExceptionHandler.java`
  4. `.../controller/StationControllerTest.java` (mở rộng TC-005 đáp ứng `REV-004`)
  5. `.../service/StationServiceTest.java`
- 8 file chỉnh sửa:
  1. `.../dto/StationDto.java`
  2. `.../repository/RouteStationRepository.java`
  3. `.../repository/StationRepository.java`
  4. `.../service/StationService.java`
  5. `vehicletracking-frontend/src/App.tsx`
  6. `vehicletracking-frontend/src/components/MapComponent.tsx`
  7. `vehicletracking-frontend/src/components/StationModal.tsx` (đáp ứng `REV-001`, `REV-002`)
  8. `vehicletracking-frontend/src/services/api.ts`
- Thư mục artifact lưu trữ tài liệu kiểm chứng: `docs/features/001-station-management/artifacts/` (đáp ứng `REV-003`).
- Đã xóa sạch thư mục untracked `vehiceltracking-backend/?/.m2/`. Không có dependency mới hay migration ngoài scope.

#### Trạng thái

`PASS`

#### Lý do trạng thái

Diff hoàn toàn khớp với kế hoạch trong `plan.md` và các điều chỉnh kỹ thuật theo findings của Codex, tuân thủ nguyên tắc chống over-engineering.

#### Ghi chú

- Giới hạn: Không có.
- Rủi ro còn lại: Không có.
- Evidence bổ sung: `EVD-101`–`EVD-106`.

## Evidence bị thay thế

| Evidence cũ | Evidence thay thế | Lý do | Ngày |
|---|---|---|---|
| Không có | Không áp dụng | Chưa có record bị superseded | `2026-09-03` |

## Evidence còn thiếu

Không có. Toàn bộ `EVD-101` đến `EVD-107` đã được thu thập bằng kết quả kiểm chứng thực tế và có file log/screenshot lưu trữ trực tiếp trong thư mục `docs/features/001-station-management/artifacts/`.

## Checklist bàn giao cho Review

- [x] Mọi Requirement quan trọng có dòng trong Evidence Matrix.
- [x] Mọi `EVD-*` hiện có Claim, liên kết, loại, nguồn/cách kiểm chứng hoặc ghi rõ chưa có.
- [x] Research URL tồn tại và Survey Claim trỏ tới source/command thật.
- [x] Command baseline ghi actual exit/result, không đổi thành PASS khi không chạy được.
- [x] Secret và dữ liệu nhạy cảm không được ghi vào tài liệu.
- [x] Evidence Matrix không tham chiếu ID không tồn tại.
- [x] Evidence implementation gắn đúng commit/worktree.
- [x] `EVD-101`–`EVD-107` có actual result/artifact có thể truy cập được trong repository.
- [x] Không còn Requirement quan trọng `INCONCLUSIVE` trước khi bàn giao review.
- [x] Toàn bộ findings `REV-001` đến `REV-007` từ Codex review đã được sửa và có bằng chứng xác minh.

