# Survey — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | COMPLETE |
| Phạm vi khảo sát | `vehiceltracking-backend`, `vehicletracking-frontend`, Git baseline |
| Ngày khảo sát | 2026-09-03 |

## Baseline

**EVD-002 — Baseline Git trước khi lập kế hoạch**

- **Claim:** Feature 002 được khảo sát trên commit `ace0f83` với worktree sạch tại thời điểm khảo sát.
- **Liên kết:** Toàn bộ feature 002.
- **Loại Evidence:** LOG.
- **Nguồn:** Lệnh chỉ đọc `git rev-parse --short HEAD` và `git status --short` chạy tại workspace.
- **Cách kiểm chứng:** Chạy lại hai lệnh tại repository; hash có thể thay đổi sau khi implementation được commit.
- **Kết quả:** `ace0f83`; `git status --short` không có dòng nào.
- **Trạng thái:** PASS.
- **Ghi chú:** Đây là baseline lịch sử, không phải bằng chứng cho implementation tương lai.

## Backend hiện có

**EVD-003 — REST route chỉ có đọc, tạo và xóa**

- **Claim:** `RouteController` đã công bố `/api/routes` với `GET /`, `GET /{id}`, `POST /` và `DELETE /{id}`, nhưng chưa có endpoint cập nhật `PUT`/`PATCH`.
- **Liên kết:** REQ-001, REQ-002, REQ-003, REQ-004, SPEC-004.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/controller/RouteController.java`, lớp `RouteController`.
- **Cách kiểm chứng:** `rg -n "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)|RequestMapping" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/controller/RouteController.java`.
- **Kết quả:** Có `GetMapping`, `PostMapping`, `DeleteMapping`; không có `PutMapping` hoặc `PatchMapping`.
- **Trạng thái:** PASS.

**EVD-004 — Service tạo route đã tính metric theo thứ tự stationIds**

- **Claim:** `RouteService.createRoute` duyệt `stationIds` theo thứ tự gửi, gán `stopOrder = i + 1`, dùng `GeoUtil.calculateDistanceKm`, tốc độ hằng `35 km/h` và dừng `1.5` phút để tính các metric route.
- **Liên kết:** REQ-002, REQ-003, SPEC-002, SPEC-003.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`, hằng `AVERAGE_URBAN_SPEED_KMH`, `STATION_DWELL_TIME_MINUTES`, phương thức `createRoute`.
- **Cách kiểm chứng:** `rg -n "AVERAGE_URBAN_SPEED_KMH|STATION_DWELL_TIME_MINUTES|createRoute|stopOrder|calculateDistanceKm" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`.
- **Kết quả:** Service hiện tạo RouteStation tuần tự và cộng quãng đường/thời gian từng chặng. Chưa kiểm tra loại `START`/`END` hoặc có method update.
- **Trạng thái:** PASS.

**EVD-005 — Model đã lưu được thứ tự trạm của route**

- **Claim:** `Route` sở hữu `routeStations` với `cascade = ALL`, `orphanRemoval = true`, `@OrderBy("stopOrder ASC")`; `RouteStation` có unique constraint `(route_id, stop_order)`.
- **Liên kết:** REQ-001, REQ-003, SPEC-001, SPEC-003.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Route.java`; `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/RouteStation.java`.
- **Cách kiểm chứng:** `rg -n "routeStations|CascadeType.ALL|orphanRemoval|OrderBy|UniqueConstraint|stopOrder" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Route.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/RouteStation.java`.
- **Kết quả:** Model có đủ quan hệ để thay thế danh sách con theo thứ tự; schema hiện không cấm một station xuất hiện nhiều lần ở các stopOrder khác nhau.
- **Trạng thái:** PASS.

**EVD-006 — StationType đã có START, STOP, END**

- **Claim:** Entity `Station` lưu `stationType` kiểu enum `StationType`; enum hiện chỉ định ba giá trị `START`, `STOP`, `END`.
- **Liên kết:** REQ-002, REQ-003, SPEC-002.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Station.java`; `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/enums/StationType.java`.
- **Cách kiểm chứng:** `rg -n "stationType|enum StationType|START|STOP|END" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Station.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/enums/StationType.java`.
- **Kết quả:** Có các giá trị enum cần thiết cho rule `START → STOP* → END`.
- **Trạng thái:** PASS.

**EVD-007 — Seeder dùng convention START → STOP* → END**

- **Claim:** Dữ liệu mẫu tạo năm station theo thứ tự START, ba STOP, END rồi gọi `routeService.createRoute` với danh sách đó.
- **Liên kết:** REQ-002, SPEC-002.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/DataSeeder.java`, phần tạo stations và route.
- **Cách kiểm chứng:** `rg -n "StationType\\.(START|STOP|END)|createRoute" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/config/DataSeeder.java`.
- **Kết quả:** Source dùng đúng thứ tự đề xuất, nhưng đây chỉ là convention dữ liệu mẫu; feature 002 sẽ biến nó thành validation backend.
- **Trạng thái:** PASS.

**EVD-008 — Trip phụ thuộc route và tạo check-in từ RouteStation**

- **Claim:** `Trip` có khóa ngoại không-null `route_id`; khi tạo trip, `TripService.createTrip` lấy RouteStation theo stopOrder và tạo TripCheckIn chứa station/stopOrder/lịch đến. Vì vậy sửa/xóa route đã có Trip có nguy cơ làm không nhất quán dữ liệu nghiệp vụ hoặc lỗi ràng buộc.
- **Liên kết:** REQ-004, SPEC-003, SPEC-005.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Trip.java`, field `route`; `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/TripService.java`, `createTrip`, dòng 42–101.
- **Cách kiểm chứng:** `rg -n "route_id|private Route route|createTrip|findByRouteIdOrderByStopOrderAsc|TripCheckIn" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/entity/Trip.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/TripService.java`.
- **Kết quả:** Route được bắt buộc cho Trip và check-in được tạo từ các route station lúc khởi tạo trip. Quy tắc chặn sửa/xóa route có Trip là biện pháp bảo toàn dữ liệu của feature 002.
- **Trạng thái:** PASS.

**EVD-009 — Cơ chế lỗi hiện tại chỉ scope cho StationController**

- **Claim:** `StationExceptionHandler` dùng `@RestControllerAdvice(assignableTypes = StationController.class)`, nên lỗi route hiện chỉ đi theo `IllegalArgumentException` của service mà không có contract Route-specific tương đương.
- **Liên kết:** REQ-005, SPEC-005.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/StationExceptionHandler.java`; `RouteService.java`.
- **Cách kiểm chứng:** `rg -n "RestControllerAdvice|assignableTypes|IllegalArgumentException" vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/StationExceptionHandler.java vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`.
- **Kết quả:** Cần một handler lỗi có scope RouteController hoặc một cơ chế chung được thiết kế cẩn thận để đảm bảo 400/404/409 cho route, không làm đổi behavior Station.
- **Trạng thái:** PASS.

## Frontend hiện có

**EVD-010 — Client route chưa có update và parse lỗi chưa thống nhất**

- **Claim:** `api.ts` có `getRoutes`, `getRouteById`, `createRoute`, `deleteRoute`; chưa có `updateRoute`. Luồng create/delete route tự đọc `message` trong JSON, trong khi API station dùng helper `parseErrorMessage`.
- **Liên kết:** REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehicletracking-frontend/src/services/api.ts`, object `api` và helper `parseErrorMessage`.
- **Cách kiểm chứng:** `rg -n "getRoutes|getRouteById|createRoute|updateRoute|deleteRoute|parseErrorMessage" vehicletracking-frontend/src/services/api.ts`.
- **Kết quả:** Cần thêm method update và tái sử dụng parser lỗi hiện có cho toàn bộ thao tác route.
- **Trạng thái:** PASS.

**EVD-011 — App chưa có UI/handler quản lý route**

- **Claim:** `App.tsx` tải routes lúc khởi tạo và giữ `selectedRoute`, nhưng chỉ có state/handler/modal cho Station và Incident; `SimulatorPanel` chỉ nhận callback mở modal Station. Không có `RouteModal` trong `src/components`.
- **Liên kết:** REQ-001, REQ-002, REQ-003, REQ-004.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehicletracking-frontend/src/App.tsx`; `vehicletracking-frontend/src/components/SimulatorPanel.tsx`; danh sách `vehicletracking-frontend/src/components/`.
- **Cách kiểm chứng:** `rg -n "selectedRoute|StationModal|IncidentModal|is.*ModalOpen|onOpenStationsModal" vehicletracking-frontend/src/App.tsx vehicletracking-frontend/src/components/SimulatorPanel.tsx` và `rg --files vehicletracking-frontend/src/components | sort`.
- **Kết quả:** Routes chỉ được tải/chọn mặc định; cần thêm RouteModal, handlers ở App và điểm mở modal.
- **Trạng thái:** PASS.

**EVD-012 — Khả năng xác minh hiện có**

- **Claim:** Frontend chỉ có scripts `lint` và `build`, không có script test; backend có test cho Station/GeoUtil/application nhưng chưa có RouteServiceTest hoặc RouteControllerTest.
- **Liên kết:** REQ-006, TEST-PLAN.
- **Loại Evidence:** SOURCE_CODE.
- **Nguồn:** `vehicletracking-frontend/package.json`; danh sách file tại `vehiceltracking-backend/src/test`.
- **Cách kiểm chứng:** `rg -n '"(lint|build|test)"' vehicletracking-frontend/package.json`; `rg --files vehiceltracking-backend/src/test | sort`.
- **Kết quả:** Plan phải bổ sung route tests backend; frontend được xác minh bằng lint/build và manual verification cho feature này.
- **Trạng thái:** PASS.

## Hệ quả cho thiết kế

- Không cần migration: `Route`, `RouteStation`, `StationType` và DTO response đã có dữ liệu cần thiết; chỉ cần bổ sung query/repository, contract/logic/handler và UI.
- Chỉ service backend được quyền tính và xác thực thứ tự; UI hỗ trợ người dùng nhưng không phải nguồn quyết định.
- Không dùng traffic realtime trong feature này. Thời lượng là công thức hiện có, không được mô tả như ETA giao thông thật.
