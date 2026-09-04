# Review — 002-route-management

| Thuộc tính | Giá trị |
| --- | --- |
| Feature ID | 002-route-management |
| Trạng thái | APPROVED |
| Reviewer | Codex |
| Ngày re-review | 2026-09-04 |
| Phạm vi | Toàn bộ Requirement → Research → Survey → Spec → Test-Plan → Plan → Evidence; git diff hiện tại; source/test frontend-backend; artifacts verification |

## Kết luận

**APPROVED.** Hai finding bắt buộc của vòng review trước đã được xử lý và mọi Requirement quan trọng đều có đường dẫn kiểm chứng `Requirement → Spec → Test Case → Evidence → Review` ở trạng thái `PASS`.

Implementation vẫn bám phạm vi Plan: thêm REST `PUT /api/routes/{id}`, kiểm tra thứ tự `START → STOP* → END`, guard route đã có `Trip`, lỗi `404/409` theo Problem Details, `RouteModal` và API client. Không có dependency mới, secret, hoặc thay đổi source ngoài phạm vi feature trong git diff đã kiểm tra.

## Xác minh độc lập của Codex

| Hạng mục | Cách xác minh | Kết quả thực tế | Trạng thái |
| --- | --- | --- | --- |
| Scope diff và whitespace | `git status --short`, `git diff --check` | Các thay đổi ứng dụng nằm trong backend/frontend của route management; test, tài liệu và artifacts là bổ sung của feature. `git diff --check` không có output. | PASS |
| EVD-013 — full backend suite | Đọc `artifacts/mvn-test.log` | Log thực thi `clean`, recompile 55 source + 6 test files và kết thúc `BUILD SUCCESS`: 63 tests, 0 failure, 0 error, 0 skipped. | PASS |
| EVD-013 — controller contract | Chạy lại `mvnw -Dtest=RouteControllerTest test` với Java 26; đọc `target/surefire-reports/TEST-com.quangkhai.vehiceltracking_backend.controller.RouteControllerTest.xml` | Report tạo lúc 08:33 ghi 9 tests, 0 failure, 0 error. Các test bao phủ POST/PUT validation, 404, 409, update, Trip guard, delete và GET list/detail. | PASS |
| EVD-014 — frontend lint | `npm run lint` với Node 24.16.0 | Exit code 0. Có 4 warnings đã tồn tại ở `IncidentModal`, `MapComponent`, `App`, không phải lỗi lint của thay đổi route management. | PASS |
| EVD-015 — frontend type-check/build | `npx tsc --noEmit`; `npm run build` với Node 24.16.0 | Cả hai exit code 0; Vite build hoàn tất, 1,831 modules transformed. | PASS |
| EVD-016 — UI manual verification | Mở 7 ảnh timestamp trong `artifacts/` và đối chiếu `RouteModal.tsx`, `App.tsx`, `api.ts` | Ảnh lần lượt cho thấy mở modal, realtime validation sai, route xuất hiện sau create, tên mới sau update, lỗi UI khi DELETE/PUT route có Trip và route biến mất sau delete hợp lệ. | PASS |

## Traceability review

| Requirement | Trace đã kiểm tra | Evidence | Kết luận |
| --- | --- | --- | --- |
| REQ-001 | SPEC-001, SPEC-004, SPEC-006 → TC-001, TC-009, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-002 | SPEC-002..SPEC-004 → TC-001..TC-005 | EVD-013, EVD-016 | PASS |
| REQ-003 | SPEC-003, SPEC-004, SPEC-006 → TC-003, TC-006, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-004 | SPEC-003, SPEC-005, SPEC-006 → TC-007, TC-008, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-005 | SPEC-004, SPEC-005 → TC-002..TC-009, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-006 | SPEC-007 → TC-011, TC-012 | EVD-013, EVD-014, EVD-015, EVD-016 | PASS |

## Trạng thái findings từ review trước

### REV-001 — EVD-013 đánh dấu PASS vượt quá test thực tế và command artifact không chính xác

- **Severity ban đầu:** Medium — bắt buộc xử lý trước approval.
- **Liên kết:** REQ-003, REQ-005, REQ-006; SPEC-003..SPEC-005; TC-002..TC-005, TC-009; EVD-013; P-004, P-007.
- **Trạng thái:** **RESOLVED**.
- **Xác minh xử lý:**
  - `RouteControllerTest` nay có test PUT với DTO rỗng/sai cấu trúc, từng cấu hình START/STOP/END sai, station không tồn tại và xác nhận route không đổi khi request bị từ chối.
  - Test thêm PUT mã trùng khác route (khác hoa/thường/khoảng trắng), kiểm tra `409`, bảo toàn dữ liệu và cho phép giữ mã của chính route.
  - `testGetRoutes_orderedStations` gọi cả `GET /api/routes/{id}` và `GET /api/routes`, sau đó kiểm tra `stopOrder` tăng dần.
  - `artifacts/mvn-test.log` chứa Maven `clean` goal, recompile và kết quả full suite 63/63 pass. Codex chạy lại độc lập controller integration và report ghi 9/9 pass.
- **Kết luận:** EVD-013 hiện hỗ trợ đầy đủ TC-001..TC-009; không còn khoảng trống PUT/GET-list nêu trong finding trước.

### REV-002 — EVD-016 không đủ artifact cho các thao tác UI đã claim và nêu recording không tồn tại

- **Severity ban đầu:** Medium — bắt buộc xử lý trước approval.
- **Liên kết:** REQ-001, REQ-003, REQ-004, REQ-006; SPEC-005, SPEC-006; TC-010; EVD-016; P-006, P-007.
- **Trạng thái:** **RESOLVED**.
- **Xác minh xử lý:** `tc010_01` đến `tc010_07` lần lượt chứng minh mở modal, validation realtime, tạo route, sửa tên, DELETE 409 với route đã có Trip, PUT 409 với route đã có Trip và xóa route chưa gán Trip. Nội dung ảnh khớp với luồng `RouteModal`/`App` và message lỗi từ `parseErrorMessage`.
- **Kết luận:** Ảnh timestamp là evidence trực tiếp, đủ bao phủ TC-010; không cần suy diễn từ source code hoặc từ một recording duy nhất.

## Ghi chú không chặn

- Hai WebP `route_crud_cycle_verification_1788485075322.webp` và `route_ui_verification_1788484531095.webp` không chứa recording UI hoàn chỉnh (lần lượt hiển thị “Generating recording…” và “Error generating recording”). Chúng **không được dùng** làm cơ sở cho kết luận này; các ảnh `tc010_01`..`tc010_07` là evidence manual được sử dụng. Đây không làm thiếu evidence cho Requirement vì từng thao tác TC-010 đã có ảnh tương ứng, nhưng artifact lỗi không nên được tham chiếu làm video verification trong các review sau.
- Lint vẫn có 4 warnings tồn tại ngoài phạm vi feature. Không có warning mới trong file route management; việc dọn chúng thuộc feature/refactor riêng nếu cần.

## Điểm đã kiểm tra, không phải finding

- `RouteService` dùng cùng `validateAndFetchStations` cho create/update, áp dụng `START → STOP* → END` và kiểm tra `tripRepository.existsByRouteId` trước PUT/DELETE.
- `RouteExceptionHandler` chỉ áp dụng cho `RouteController`; các test Station vẫn pass trong suite đầy đủ, nên handler mới không ảnh hưởng error handling của station.
- UI truyền `stationIds` theo thứ tự hiển thị, có thao tác thêm/xóa/lên/xuống trạm và dùng `parseErrorMessage` cho API route.
- Không phát hiện regression, dependency mới hoặc secret trong source/diff được review.
