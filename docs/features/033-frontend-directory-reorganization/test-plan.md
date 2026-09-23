# 033 — Kế hoạch Kiểm thử Tái cấu trúc Thư mục Frontend

## 1. Mục tiêu kiểm thử

Xác minh rằng sau khi di dời toàn bộ tệp tin sang cấu trúc Feature-Driven và cấu hình path alias `@/`:
1. Không có bất kỳ lỗi biên dịch TypeScript, lỗi phân giải mô-đun (unresolved module), hoặc cảnh báo linter nào.
2. Toàn bộ 14 tệp kiểm thử đơn vị (85 test case) và 5 kiểm thử chuyển động xe (vehicle motion) tiếp tục vượt qua 100%.
3. Quá trình đóng gói sản xuất (`vite build`) tạo ra các gói tài nguyên hợp lệ, kích thước tương đương và không lỗi.
4. Hành vi nghiệp vụ và giao diện người dùng của toàn bộ các màn hình không có bất kỳ sự thoái lui (regression) nào.

---

## 2. Các tầng kiểm thử tự động

### 2.1 Kiểm tra tĩnh (Static Code Quality)

Chạy trên môi trường Node.js v24 (`nvm use 24`):

```bash
cd vehicletracking-frontend
npm run lint
npm run typecheck
```

* **Tiêu chí đạt:**
  * `oxlint`: 0 warnings, 0 errors.
  * `eslint "**/*.vue"`: 0 warnings, 0 errors.
  * `vue-tsc --noEmit`: hoàn thành thành công không có lỗi type mismatch hoặc module not found.

### 2.2 Kiểm thử đơn vị (Vitest Unit Tests)

```bash
cd vehicletracking-frontend
npm run test:unit
```

* **Bộ test bao phủ:**
  * [tests/unit/auth.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/auth.test.ts) (9 tests)
  * [tests/unit/fleet.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/fleet.test.ts) (10 tests)
  * [tests/unit/routes.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/routes.test.ts) (5 tests)
  * [tests/unit/map-state.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/map-state.test.ts) (12 tests)
  * [tests/unit/map-components.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/map-components.test.ts) (12 tests)
  * [tests/unit/map-integration.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/map-integration.test.ts) (3 tests)
  * [tests/unit/tracking-panels.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/tracking-panels.test.ts) (2 tests)
  * [tests/unit/router.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/router.test.ts) (4 tests)
  * [tests/unit/business-pages.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/business-pages.test.ts) (7 tests)
  * [tests/unit/page-workflows.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/page-workflows.test.ts) (6 tests)
  * [tests/unit/http-contract.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/http-contract.test.ts) (5 tests)
  * [tests/unit/cutover.test.mjs](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/cutover.test.mjs) (4 tests)
  * [tests/unit/preview-server.test.mjs](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/preview-server.test.mjs) (5 tests)
  * [tests/unit/toolchain.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit/toolchain.test.ts) (1 test)
* **Tiêu chí đạt:** 14/14 files passed, 85/85 tests passed.

### 2.3 Kiểm thử chuyển động xe (Native Node Motion Test)

```bash
cd vehicletracking-frontend
npm run test:motion
```

* **Tiêu chí đạt:** 5/5 assertions passed về nội suy chuyển động xe, góc quay hướng lái và xử lý trễ vị trí.

### 2.4 Kiểm thử đóng gói sản xuất (Production Build)

```bash
cd vehicletracking-frontend
npm run build
```

* **Tiêu chí đạt:**
  * Build hoàn thành không lỗi.
  * Các chunk phân giải chính xác (MapComponent, SimulationLayers, RouteInspectionLayer).

---

## 3. Ma trận kiểm tra xác minh từng phân hệ (Feature Verification Matrix)

| Phân hệ | Màn hình / Thành phần kiểm tra | Tiêu chí xác minh |
|---|---|---|
| **Shared & Layout** | `ApplicationShell`, `PageHeading`, `SidePanel` | Shell điều hướng, sidebar thu phóng, header tiêu đề hiển thị đúng trạng thái. |
| **Auth** | `/login`, `/register`, `guards.ts` | Form nhập liệu, gửi yêu cầu đăng nhập, chặn truy cập khi chưa có quyền, chuyển hướng đúng role. |
| **Map Core** | `MapComponent`, `MapControls`, `useMapCamera` | Bản đồ tải đúng Leaflet container, zoom controls, fitBounds khi chọn tuyến hoặc xe. |
| **Stations** | `/stations`, `StationPanel`, `StationDrawer` | Mở danh sách trạm, tạo trạm mới, mở drawer sửa thông tin, mở modal xóa trạm ([ConfirmStationDelete.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/ConfirmStationDelete.vue)). |
| **Routes** | `/routes`, `RouteWorkspace`, `RouteShapeEditor` | Chọn tuyến, hiển thị các điểm dừng có thể sắp xếp, layer thanh tra tuyến ([RouteInspectionLayer.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/route/RouteInspectionLayer.vue)). |
| **Fleet** | `/vehicles`, `/drivers`, `/trips`, `FleetWorkspace` | Bảng quản lý đội xe, drawer thông tin xe, lịch sử telemetry, chi tiết chuyến đi, ETA từng trạm. |
| **Tracking** | `/operations`, `TrackingPanel`, `useVehicleMarkers` | Chế độ theo dõi realtime, danh sách xe đang chạy, stream cảnh báo ([AlertStream.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/AlertStream.vue)). |
| **Simulation** | `/operations?mode=simulation`, `SimulatorPanel` | Bật tắt simulator, danh sách xe giả lập đa luồng, chọn tốc độ, chạy/dừng mô phỏng. |
| **Traffic** | `TrafficLayer`, `NearbyTrafficIncidents` | Bật/tắt lớp tình trạng giao thông, xem sự cố ùn tắc lân cận. |
| **Schedules** | `/schedules`, `ScheduleManagementPage` | Bảng lịch trình tự động, dialog xác nhận điều phối ([ScheduleConfirm.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/ScheduleConfirm.vue)). |
| **Reports** | `/dashboard`, `/reports`, `/alerts` | Dashboard số liệu thẻ KPI, biểu đồ báo cáo hiệu suất, danh sách lịch sử cảnh báo. |
