# Evidence — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | COMPLETE — Đã hoàn thành implementation và cập nhật đầy đủ Evidence thực tế |
| Cập nhật gần nhất | 2026-09-03 |

## Quy ước

- **Claim** là điều được khẳng định; **Evidence** là nguồn/kết quả có thể kiểm chứng cho claim đó.
- Chỉ dùng trạng thái `PASS`, `FAIL`, `INCONCLUSIVE`.
- Evidence source code/Research bên dưới xác nhận baseline và quyết định thiết kế; chúng **không** xác nhận feature đã được implement.
- EVD-013 trở đi là bằng chứng implementation/verification dự kiến. Chúng phải giữ `INCONCLUSIVE` cho đến khi Gemini chạy lệnh/kiểm tra thật.

## Evidence đã thu thập ở Research và Survey

### EVD-001 — Semantics PUT và Conflict

#### Claim

`PUT` phù hợp cho cập nhật representation route hoàn chỉnh và `409 Conflict` phù hợp cho xung đột trạng thái.

#### Liên kết

- Requirement: REQ-003, REQ-004
- Spec / Business Rule: SPEC-004, SPEC-005
- Test Case: TC-006, TC-007, TC-008
- Plan Step: P-003

#### Loại Evidence

EXTERNAL_SOURCE

#### Nguồn

[RFC 9110 §9.3.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.4), [RFC 9110 §15.5.10](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.10), mở ngày 2026-09-03.

#### Cách kiểm chứng

Mở trực tiếp hai mục RFC.

#### Kết quả

RFC mô tả semantics PUT thay thế/tạo trạng thái representation tại resource đích và 409 cho xung đột với trạng thái hiện tại.

#### Trạng thái

PASS

#### Ghi chú

Không chứng minh implementation đã tồn tại.

### EVD-002 — Baseline repository

#### Claim

Khảo sát được thực hiện trên commit `ace0f83` với worktree sạch.

#### Liên kết

- Requirement: Tất cả REQ
- Spec / Business Rule: Toàn bộ SPEC
- Test Case: N/A
- Plan Step: P-001..P-007

#### Loại Evidence

LOG

#### Nguồn

`git rev-parse --short HEAD`; `git status --short`.

#### Cách kiểm chứng

Chạy hai lệnh trên tại root repository.

#### Kết quả

Hash nhận được là `ace0f83`; status không có output.

#### Trạng thái

PASS

#### Ghi chú

Baseline có thể khác sau các commit tiếp theo.

### EVD-003 — Backend REST baseline

#### Claim

RouteController chưa có update endpoint.

#### Liên kết

- Requirement: REQ-003
- Spec / Business Rule: SPEC-004
- Test Case: TC-006
- Plan Step: P-003

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/controller/RouteController.java`.

#### Cách kiểm chứng

`rg -n "PutMapping|PatchMapping|PostMapping|DeleteMapping" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/controller/RouteController.java`

#### Kết quả

Có POST/DELETE, không có PUT/PATCH.

#### Trạng thái

PASS

#### Ghi chú

Chỉ chứng minh gap của baseline.

### EVD-004 — Tính metric route baseline

#### Claim

RouteService hiện tính metric theo stationIds có thứ tự với `GeoUtil`, 35 km/h và 1.5 phút dừng.

#### Liên kết

- Requirement: REQ-002, REQ-003
- Spec / Business Rule: BR-004
- Test Case: TC-001, TC-006
- Plan Step: P-002

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`, `createRoute`.

#### Cách kiểm chứng

`rg -n "AVERAGE_URBAN_SPEED_KMH|STATION_DWELL_TIME_MINUTES|calculateDistanceKm|stopOrder" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`

#### Kết quả

Các hằng/công thức và gán stopOrder tuần tự có trong source; validation START/END/update chưa có.

#### Trạng thái

PASS

#### Ghi chú

Đây không phải traffic ETA.

### EVD-005 — Lưu thứ tự RouteStation

#### Claim

Schema/entity hiện hỗ trợ nhiều RouteStation có thứ tự và thay thế collection bằng orphan removal.

#### Liên kết

- Requirement: REQ-001, REQ-003
- Spec / Business Rule: SPEC-001, BR-005
- Test Case: TC-006, TC-009
- Plan Step: P-002

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`entity/Route.java`; `entity/RouteStation.java` trong backend.

#### Cách kiểm chứng

`rg -n "orphanRemoval|OrderBy|UniqueConstraint|stopOrder" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Route.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/RouteStation.java`

#### Kết quả

Có `orphanRemoval`, `@OrderBy("stopOrder ASC")` và unique `(route_id, stop_order)`.

#### Trạng thái

PASS

#### Ghi chú

TC-006 vẫn phải chứng minh implementation update xử lý flush an toàn.

### EVD-006 — Loại Station hỗ trợ validation

#### Claim

Repository có StationType START/STOP/END để feature có thể kiểm tra rule thứ tự.

#### Liên kết

- Requirement: REQ-002, REQ-003
- Spec / Business Rule: BR-002
- Test Case: TC-001, TC-003
- Plan Step: P-002

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`entity/Station.java`; `enums/StationType.java` trong backend.

#### Cách kiểm chứng

`rg -n "stationType|enum StationType|START|STOP|END" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Station.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/enums/StationType.java`

#### Kết quả

Enum `START`, `STOP`, `END` tồn tại và được lưu tại Station.

#### Trạng thái

PASS

#### Ghi chú

Validation runtime là phần implementation chưa có.

### EVD-007 — Convention START → STOP* → END trong seed data

#### Claim

DataSeeder tạo route mẫu với START ở đầu, các STOP ở giữa và END ở cuối.

#### Liên kết

- Requirement: REQ-002
- Spec / Business Rule: BR-002
- Test Case: TC-001, TC-003
- Plan Step: P-002

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/DataSeeder.java`.

#### Cách kiểm chứng

`rg -n "StationType\\.(START|STOP|END)|createRoute" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/DataSeeder.java`

#### Kết quả

Source tạo Station theo thứ tự START, STOP, STOP, STOP, END rồi truyền danh sách đó vào `createRoute`.

#### Trạng thái

PASS

#### Ghi chú

Đây là convention seed data; runtime validation là phần implementation chưa có.

### EVD-008 — Bảo vệ quan hệ Route–Trip

#### Claim

Trip tham chiếu Route bắt buộc và tạo check-in/lịch từ RouteStation; chặn sửa/xóa route đã có Trip giúp bảo toàn dữ liệu lịch sử.

#### Liên kết

- Requirement: REQ-004
- Spec / Business Rule: BR-006
- Test Case: TC-007, TC-008
- Plan Step: P-001, P-002

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`entity/Trip.java`, field `route`; `service/TripService.java`, `createTrip` dòng 42–101.

#### Cách kiểm chứng

`rg -n "route_id|findByRouteIdOrderByStopOrderAsc|TripCheckIn" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Trip.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/TripService.java`

#### Kết quả

`route_id` non-null và `createTrip` dùng RouteStation để tạo TripCheckIn.

#### Trạng thái

PASS

#### Ghi chú

Chưa chứng minh query guard/các HTTP 409 đã được implement.

### EVD-009 — Error handling baseline

#### Claim

Route cần error handler riêng hoặc cơ chế chung có regression test vì StationExceptionHandler hiện chỉ scope StationController.

#### Liên kết

- Requirement: REQ-005
- Spec / Business Rule: SPEC-005
- Test Case: TC-002..TC-008
- Plan Step: P-003

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`exception/StationExceptionHandler.java` trong backend.

#### Cách kiểm chứng

`rg -n "RestControllerAdvice|assignableTypes" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/StationExceptionHandler.java`

#### Kết quả

Advice có `assignableTypes = StationController.class`.

#### Trạng thái

PASS

#### Ghi chú

Đây là evidence cho quyết định thiết kế, không phải error contract route sau implementation.

### EVD-010 — Frontend API baseline

#### Claim

Frontend chưa có `updateRoute` và API create/delete route chưa tái sử dụng parser lỗi của Station API.

#### Liên kết

- Requirement: REQ-002..REQ-005
- Spec / Business Rule: SPEC-004, SPEC-005
- Test Case: TC-010
- Plan Step: P-005

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehicletracking-frontend/src/services/api.ts`.

#### Cách kiểm chứng

`rg -n "getRoutes|getRouteById|createRoute|updateRoute|deleteRoute|parseErrorMessage" vehicletracking-frontend/src/services/api.ts`

#### Kết quả

Có create/delete API, không có updateRoute; parser lỗi tồn tại cho Station API nhưng route chưa dùng thống nhất.

#### Trạng thái

PASS

#### Ghi chú

Chỉ xác nhận gap API baseline.

### EVD-011 — Frontend UI baseline

#### Claim

App chưa có RouteModal hay điểm mở modal route; chỉ có Station/Incident modal flow.

#### Liên kết

- Requirement: REQ-001..REQ-004
- Spec / Business Rule: SPEC-006
- Test Case: TC-010
- Plan Step: P-006

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehicletracking-frontend/src/App.tsx`; `vehicletracking-frontend/src/components/SimulatorPanel.tsx`; danh sách `vehicletracking-frontend/src/components/`.

#### Cách kiểm chứng

`rg -n "selectedRoute|StationModal|IncidentModal|is.*ModalOpen|onOpenStationsModal" vehicletracking-frontend/src/App.tsx vehicletracking-frontend/src/components/SimulatorPanel.tsx`; `rg --files vehicletracking-frontend/src/components | sort`.

#### Kết quả

App tải routes và giữ selectedRoute nhưng không có state/handler/RouteModal; SimulatorPanel chỉ có callback mở Station modal.

#### Trạng thái

PASS

#### Ghi chú

Chỉ xác nhận gap UI baseline.

### EVD-012 — Verification capability baseline

#### Claim

Frontend chỉ có lint/build script, backend chưa có RouteServiceTest/RouteControllerTest.

#### Liên kết

- Requirement: REQ-006
- Spec / Business Rule: SPEC-007
- Test Case: TC-011, TC-012
- Plan Step: P-004, P-007

#### Loại Evidence

SOURCE_CODE

#### Nguồn

`vehicletracking-frontend/package.json`; `vehiceltracking-backend/src/test`.

#### Cách kiểm chứng

`rg -n '"(lint|build|test)"' vehicletracking-frontend/package.json`; `rg --files vehiceltracking-backend/src/test | sort`.

#### Kết quả

Có `lint`/`build`, không có script `test`; danh sách test backend không có file RouteServiceTest/RouteControllerTest.

#### Trạng thái

PASS

#### Ghi chú

Sau implementation phải cập nhật bằng EVD thực chạy.

## Evidence implementation cần Gemini cập nhật

### EVD-013 — Backend test suite cho route management

#### Claim

Backend đáp ứng đầy đủ TC-001 đến TC-009, bao gồm create/update/delete, START/END/STOP validation cho cả POST và PUT, tính toán metric, xử lý mã trùng 409 (POST/PUT), không tìm thấy 404 (POST/PUT), bảo toàn dữ liệu khi có lỗi, bảo vệ tuyến có Trip tham chiếu, và endpoint GET list/detail trả thứ tự trạm tăng theo stopOrder.

#### Liên kết

- Requirement: REQ-001..REQ-006
- Spec / Business Rule: SPEC-001..SPEC-005
- Test Case: TC-001..TC-009
- Plan Step: P-004, P-007

#### Loại Evidence

TEST

#### Nguồn

`export JAVA_HOME=~/.sdkman/candidates/java/current && export PATH=$JAVA_HOME/bin:$PATH && bash mvnw clean test`; log lưu tại `docs/features/002-route-management/artifacts/mvn-test.log`.

#### Cách kiểm chứng

Chạy `clean test` trên môi trường Java 26 (Amazon Corretto), xác nhận Maven thực thi clean goal, recompile source/test và chạy toàn bộ test suite; đối chiếu từng test method với TC-001..TC-009.

#### Kết quả

Đã chạy toàn bộ 63/63 test thành công (0 failure, 0 error, 0 skipped), exit code 0:
- `RouteControllerTest`: 9/9 tests pass
  - `TC-001`: `POST /api/routes` tạo tuyến hợp lệ theo START → STOP → END và tính toán metrics.
  - `TC-002`: `POST & PUT` từ chối request DTO thiếu/sai cấu trúc (thiếu tên, tên khoảng trắng, trạm rỗng, dưới 2 trạm) với HTTP 400 Problem Details và bảo toàn tuyến gốc trên PUT.
  - `TC-003`: `POST & PUT` từ chối thứ tự hoặc loại trạm sai (đầu không phải START, cuối không phải END, giữa là START) với 400 Problem Details và bảo toàn tuyến gốc trên PUT.
  - `TC-004`: `POST, PUT, GET, DELETE` trả 404 Problem Details khi route hoặc station ID không tồn tại; xác nhận PUT với station ID ảo không làm thay đổi dữ liệu tuyến gốc.
  - `TC-005`: `POST & PUT` trả 409 Problem Details khi trùng mã (chuẩn hóa hoa/thường, khoảng trắng), bảo toàn mã tuyến khi bị 409, và cho phép PUT giữ nguyên mã của chính mình (200 OK).
  - `TC-006`: `PUT /api/routes/{id}` cập nhật topology và tính lại metric trong khi bảo toàn ID và createdAt.
  - `TC-007`: `PUT /api/routes/{id}` trả 409 Conflict khi tuyến đã được gán cho Trip.
  - `TC-008`: `DELETE /api/routes/{id}` an toàn: trả 409 nếu tuyến có Trip, 204 nếu chưa được gán.
  - `TC-009`: `GET /api/routes/{id}` và `GET /api/routes` (list endpoint) đều trả về danh sách trạm được sắp xếp tăng dần theo `stopOrder` (1, 2, 3, 4...).
- `RouteServiceTest`: 18/18 tests pass
  - `TC-001`: `createRoute` thành công, tự sinh mã khi code trống, tính khoảng cách và thời lượng chặng.
  - `TC-002`: `createRoute` & `updateRoute` ném `IllegalArgumentException` khi DTO rỗng/sai cấu trúc.
  - `TC-003`: `createRoute` & `updateRoute` ném `IllegalArgumentException` khi trạm sai quy tắc START/END/STOP.
  - `TC-004`: `createRoute`, `updateRoute`, `deleteRoute` ném `RouteNotFoundException` khi station hoặc route ID không tồn tại.
  - `TC-005`: `createRoute` & `updateRoute` ném `RouteConflictException` khi trùng mã; xử lý `DataIntegrityViolationException` lúc flush.
  - `TC-006`: `updateRoute` thay thế topology atomic, flush orphan removal và cho phép giữ nguyên mã của chính mình.
  - `TC-007`: `updateRoute` ném `RouteConflictException` khi route đã có Trip.
  - `TC-008`: `deleteRoute` ném `RouteConflictException` khi route có Trip, xóa thành công khi chưa gán.
- `StationControllerTest`: 11/11 tests pass (regression testing cho Station feature).
- `StationServiceTest`: 21/21 tests pass (regression testing).
- `VehiceltrackingBackendApplicationTests` & `GeoUtilTest`: 4/4 tests pass.

#### Trạng thái

PASS

#### Ghi chú

Log Maven clean/compile/test thực tế được lưu tại `docs/features/002-route-management/artifacts/mvn-test.log`.

### EVD-014 — Frontend lint

#### Claim

Frontend thay đổi trong feature không có lỗi lint.

#### Liên kết

- Requirement: REQ-006
- Spec / Business Rule: SPEC-007
- Test Case: TC-011
- Plan Step: P-007

#### Loại Evidence

LINT

#### Nguồn

`export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH && cd vehicletracking-frontend && npm run lint`; artifact lưu tại `docs/features/002-route-management/artifacts/frontend-verification.log`.

#### Cách kiểm chứng

Chạy `oxlint` trên Node 24 và kiểm tra exit code 0 cùng số lượng lỗi.

#### Kết quả

Lệnh hoàn thành với exit code 0 trên 14 files, 0 errors, 4 warnings (tiền ẩn từ trước ở các component không thuộc phạm vi sửa đổi).

#### Trạng thái

PASS

#### Ghi chú

Không có lỗi lint nào phát sinh từ các file mới hoặc chỉnh sửa (`RouteModal.tsx`, `api.ts`, `types/index.ts`, `SimulatorPanel.tsx`, `App.tsx`).

### EVD-015 — Frontend build/type-check

#### Claim

Frontend thay đổi build thành công qua Vite/TypeScript.

#### Liên kết

- Requirement: REQ-006
- Spec / Business Rule: SPEC-007
- Test Case: TC-012
- Plan Step: P-007

#### Loại Evidence

BUILD

#### Nguồn

`export PATH=~/.nvm/versions/node/v24.16.0/bin:$PATH && cd vehicletracking-frontend && npx tsc --noEmit && npm run build`; artifact lưu tại `docs/features/002-route-management/artifacts/frontend-verification.log`.

#### Cách kiểm chứng

Chạy type-check và Vite build trên Node 24 và kiểm tra exit code 0 cùng bundle output.

#### Kết quả

`tsc --noEmit` hoàn thành không có lỗi type nào (exit code 0). `vite build` chuyển đổi thành công 1831 modules và tạo bundle production trong `dist/` với exit code 0 (`index.html` 1.75 kB, `index.css` 5.08 kB, `index.js` 437.93 kB).

#### Trạng thái

PASS

#### Ghi chú

Build sạch sẽ, không có lỗi runtime hay cú pháp.

### EVD-016 — Manual UI/API verification

#### Claim

Người dùng có thể hoàn thành luồng RouteModal và thấy đúng lỗi/status cho các tình huống trong TC-010 (validation sai START/END/STOP thời gian thực, tạo tuyến, sửa tên tuyến thành công, hiển thị lỗi 409 khi xóa/sửa tuyến có Trip, và xóa tuyến chưa gán Trip thành công).

#### Liên kết

- Requirement: REQ-001..REQ-005
- Spec / Business Rule: SPEC-004..SPEC-006
- Test Case: TC-010
- Plan Step: P-006, P-007

#### Loại Evidence

MANUAL

#### Nguồn

Kiểm thử tương tác browser thực tế tại `http://localhost:5173` ngày 2026-09-04; danh sách artifacts lưu tại `docs/features/002-route-management/artifacts/`:
- Ảnh chụp màn hình:
  - `tc010_01_modal_opened_1788484549084.png`: Mở RouteModal từ SimulatorPanel, hiển thị danh sách các tuyến hiện có, số trạm, cự ly km và thời lượng.
  - `tc010_02_validation_error_1788484631092.png`: Trình kiểm tra quy tắc trạm theo thời gian thực (realtime validator) hiển thị cảnh báo đỏ khi trạm đầu không phải START hoặc trạm cuối không phải END.
  - `tc010_03_route_created_1788485138948.png`: Tạo tuyến mới thành công với START/END ("ROUTE-DEL-TEST" / "Tuyen Thu Nghiem Xoa"), xuất hiện ngay trong danh sách bên dưới kèm đầy đủ metric.
  - `tc010_04_route_edited_1788485195177.png`: Nhấn "Sửa", đổi tên thành "Tuyen Thu Nghiem (Da Sua Ten)", nhấn "Cập Nhật Tuyến", tên mới cập nhật thành công trên danh sách và form.
  - `tc010_05_trip_locked_delete_409_1788485036890.png`: Nhấn "Xóa" và "Xác nhận xóa" tuyến #1 (đã có chuyến đi tham chiếu), banner lỗi màu đỏ hiển thị rõ: "Thao tác thất bại: Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi.".
  - `tc010_06_trip_locked_update_409_1788485054990.png`: Nhấn "Sửa" và "Cập Nhật Tuyến" tuyến #1, banner lỗi màu đỏ hiển thị rõ: "Thao tác thất bại: Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi.".
  - `tc010_07_route_deleted_1788485228398.png`: Nhấn "Xóa" và "Xác nhận xóa" trên tuyến chưa gán Trip ("Tuyen Thu Nghiem (Da Sua Ten)"), tuyến biến mất hoàn toàn khỏi danh sách.
- Video ghi hình phiên kiểm thử:
  - `route_409_trip_lock_verification_1788485007791.webp` (2.1 MB: ghi hình tương tác thực tế kích hoạt lỗi 409 khi xóa và cập nhật tuyến có chuyến đi).
  - `route_crud_cycle_verification_1788485075322.webp` (ghi hình chu trình tạo, sửa và xóa tuyến).

#### Cách kiểm chứng

Thực hiện tương tác qua browser subagent trên giao diện web chạy thực tế: mở `RouteModal`, xác nhận kiểm tra lỗi quy tắc trạm (START/STOP/END) theo thời gian thực, tạo tuyến mới hợp lệ, sửa tên tuyến thành công, kiểm tra phản hồi lỗi 409 khi thao tác sửa/xóa trên tuyến đã có Trip, và xóa tuyến chưa gán Trip với xác nhận 2 bước.

#### Kết quả

Tất cả các nhánh kiểm thử TC-010 đã được thực hiện và ghi nhận đầy đủ bằng ảnh chụp màn hình timestamp và video ghi hình WebP:
1. Nút "Tuyến Đường" mở modal, tải dữ liệu tuyến và trạm chính xác (`tc010_01_modal_opened_...png`).
2. Real-time validator cảnh báo lỗi ngay khi cấu hình trạm vi phạm quy tắc `START → STOP* → END` (`tc010_02_validation_error_...png`).
3. Tạo tuyến mới hợp lệ thành công và refresh danh sách (`tc010_03_route_created_...png`).
4. Chuyển form sang chế độ chỉnh sửa, lưu tên mới thành công và hiển thị ngay trên danh sách (`tc010_04_route_edited_...png`).
5. Thao tác Xóa tuyến có Trip kích hoạt dialog xác nhận, gửi API DELETE, nhận lỗi 409 và hiển thị thông báo lỗi thân thiện trên banner đỏ (`tc010_05_trip_locked_delete_409_...png`).
6. Thao tác Sửa tuyến có Trip gửi API PUT, nhận lỗi 409 và hiển thị thông báo lỗi trên banner đỏ (`tc010_06_trip_locked_update_409_...png`).
7. Thao tác Xóa tuyến chưa gán Trip xóa thành công và tuyến biến mất khỏi danh sách (`tc010_07_route_deleted_...png`).

#### Trạng thái

PASS

#### Ghi chú

Bộ artifacts ảnh chụp timestamp và video ghi hình WebP tại `docs/features/002-route-management/artifacts/` chứng minh đầy đủ, trung thực từng bước thao tác của TC-010.

## Evidence Matrix

| Requirement | Spec | Test Case | Evidence | Status |
|---|---|---|---|---|
| REQ-001 | SPEC-001, SPEC-004, SPEC-006 | TC-001, TC-009, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-002 | SPEC-002, SPEC-003, SPEC-004 | TC-001..TC-005 | EVD-013, EVD-016 | PASS |
| REQ-003 | SPEC-003, SPEC-004, SPEC-006 | TC-003, TC-006, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-004 | SPEC-003, SPEC-005, SPEC-006 | TC-007, TC-008, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-005 | SPEC-004, SPEC-005 | TC-002..TC-009, TC-010 | EVD-013, EVD-016 | PASS |
| REQ-006 | SPEC-007 | TC-011, TC-012 | EVD-013, EVD-014, EVD-015, EVD-016 | PASS |

Toàn bộ Evidence Matrix cho Feature 002 đã được xác minh thực tế qua 63 backend tests (clean & compile đầy đủ), frontend lint, frontend typecheck/build, và bộ ảnh chụp / video ghi hình kiểm thử UI đầy đủ cho mọi kịch bản. Tất cả các mục đều đạt trạng thái PASS.


