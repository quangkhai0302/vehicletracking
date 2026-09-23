# 033 — Kế hoạch Triển khai Tái cấu trúc Thư mục Frontend

Trạng thái: **Verified**. Tất cả các giai đoạn đã hoàn thành và kiểm chứng tự động 100%.

## 1. Nguyên tắc triển khai

1. **Phân kỳ an toàn (Incremental Migration):** Không di chuyển toàn bộ tệp tin trong một lần duy nhất. Triển khai theo từng giai đoạn độc lập kèm checkpoint kiểm thử sau mỗi giai đoạn.
2. **Bảo toàn chức năng & giao diện:** Không sửa đổi logic xử lý nghiệp vụ, không đổi markup HTML/CSS ngoài việc sửa đường dẫn import và di dời tệp tin.
3. **Giữ sạch trạng thái kiểm thử:** Sau mỗi giai đoạn, `npm run typecheck` và `npm run test:unit` phải chạy và đạt kết quả kiểm thử.

---

## 2. Chi tiết các giai đoạn triển khai (Phases)

### Giai đoạn 1: Chuẩn bị Toolchain & Cấu hình Path Alias `@/*`
* **Mục tiêu:** Cho phép import dạng `@/shared/...` và `@/features/...` trên toàn bộ hệ thống.
* **Các bước thực hiện:**
  1. Cập nhật [vehicletracking-frontend/vite.config.js](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vite.config.js): Thêm `resolve.alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }`.
  2. Cập nhật [vehicletracking-frontend/tsconfig.json](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tsconfig.json): Thêm `"baseUrl": "."` và `"paths": { "@/*": ["src/*"] }`.
  3. Cập nhật [vehicletracking-frontend/vitest.config.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vitest.config.ts) (nếu cần alias độc lập).
* **Checkpoint 1:** Chạy `npm run typecheck && npm run test:unit && npm run build` để chứng minh việc thêm alias không ảnh hưởng tới mã nguồn hiện tại.

---

### Giai đoạn 2: Thành lập lớp Shared & Layouts dùng chung
* **Mục tiêu:** Tách các thành phần độc lập nghiệp vụ và dọn dẹp thư mục hỗn tạp `components/business`.
* **Các bước thực hiện:**
  1. Khởi tạo cây thư mục `src/shared/`:
     * `src/shared/api/http.ts` $\leftarrow$ chuyển từ `src/services/http.ts`.
     * `src/shared/components/PageHeading.vue` $\leftarrow$ chuyển từ `src/components/business/PageHeading.vue`.
     * `src/shared/components/SidePanel.vue` $\leftarrow$ chuyển từ `src/components/business/SidePanel.vue`.
     * `src/shared/composables/useCompactLayout.ts` $\leftarrow$ chuyển từ `src/composables/useCompactLayout.ts`.
     * `src/shared/types/workspace.ts` $\leftarrow$ chuyển từ `src/types/workspace.ts`.
     * `src/shared/utils/format.ts` $\leftarrow$ chuyển từ `src/utils/format.ts`.
  2. Cập nhật `src/app/layouts/`:
     * `src/app/layouts/AuthLayout.vue` $\leftarrow$ chuyển từ `src/components/business/AuthLayout.vue`.
     * `src/app/layouts/ApplicationShell.vue` $\leftarrow$ chuyển từ `src/app/ApplicationShell.vue`.
     * `src/app/layouts/application-shell.css` $\leftarrow$ chuyển từ `src/app/application-shell.css`.
  3. Cập nhật router: `src/app/router/index.ts` và `src/app/router/guards.ts`.
  4. Cập nhật toàn bộ các file đang import các module trên.
* **Checkpoint 2:** Chạy `npm run typecheck && npm run test:unit`.

---

### Giai đoạn 3: Di dời các Feature Modules độc lập (Không gắn với Bản đồ)
* **Mục tiêu:** Gom cụm các domain nghiệp vụ bảng biểu / quản trị vào `src/features/`.
* **Các bước thực hiện:**
  1. **Feature `auth`:**
     * Chuyển `src/auth/authState.ts`, `src/auth/useAuth.ts` $\rightarrow$ `src/features/auth/composables/`.
     * Chuyển `src/services/auth.ts`, `src/services/users.ts` $\rightarrow$ `src/features/auth/api/`.
     * Chuyển `src/types/auth.ts` $\rightarrow$ `src/features/auth/types/`.
     * Chuyển `src/pages/auth-pages.css` $\rightarrow$ `src/features/auth/styles/`.
     * Xóa bỏ thư mục `src/auth/` cũ.
  2. **Feature `schedules`:**
     * Chuyển `src/services/schedules.ts` $\rightarrow$ `src/features/schedules/api/`.
     * Chuyển `src/types/schedule.ts` $\rightarrow$ `src/features/schedules/types/`.
     * Chuyển `src/components/business/ScheduleConfirm.vue` $\rightarrow$ `src/features/schedules/components/`.
     * Chuyển `src/pages/schedule-management.css` $\rightarrow$ `src/features/schedules/styles/`.
     * Xóa bỏ hoàn toàn thư mục `src/components/business/`.
  3. **Feature `reports`:**
     * Chuyển `src/services/dashboard.ts`, `reports.ts`, `notifications.ts` $\rightarrow$ `src/features/reports/api/`.
     * Chuyển `src/types/dashboard.ts`, `reports.ts`, `notifications.ts` $\rightarrow$ `src/features/reports/types/`.
     * Chuyển `src/pages/reports.css`, `alerts-management.css`, `business-pages.css` $\rightarrow$ `src/features/reports/styles/`.
  4. Cập nhật import trong các Page tương ứng (`LoginPage`, `AdminRegistrationPage`, `ScheduleManagementPage`, `DashboardPage`, `ReportsPage`, `AlertsManagementPage`, `UserManagementPage`).
* **Checkpoint 3:** Chạy `npm run typecheck && npm run test:unit`.

---

### Giai đoạn 4: Di dời các Feature Modules trọng tâm Bản đồ
* **Mục tiêu:** Gom cụm toàn bộ các module gắn với bản đồ Leaflet.
* **Các bước thực hiện:**
  1. **Feature `stations`:**
     * Chuyển `services/stations.ts`, `types/station.ts`, `composables/useStationWorkspace.ts`.
     * Chuyển `components/StationPanel.vue`, `components/StationDrawer.vue`.
     * Chuyển `components/operations/ConfirmStationDelete.vue` $\rightarrow$ `src/features/stations/components/ConfirmStationDelete.vue`.
  2. **Feature `routes`:**
     * Chuyển `services/routes.ts`, `types/route.ts`, `composables/useRouteTraffic.ts`, `useSelectedVehicleRoute.ts`.
     * Chuyển toàn bộ `components/route/*` $\rightarrow$ `src/features/routes/components/` & styles.
     * Chuyển `utils/routeInspection.ts`, `inspectionFormat.ts` $\rightarrow$ `src/features/routes/utils/`.
  3. **Feature `fleet`:**
     * Chuyển `services/fleet.ts`, `telemetry.ts`, `checkins.ts`, `eta.ts`, `driverPortal.ts`.
     * Chuyển `types/fleet.ts`, `vehicle.ts`, `telemetry.ts`, `checkin.ts`, `eta.ts`.
     * Chuyển `composables/useFleetWorkspace.ts`, `useTripEta.ts`, `useTripCheckIns.ts`.
     * Chuyển toàn bộ `components/fleet/*`, `components/VehicleDrawer.vue`.
     * Chuyển `utils/vehicleMotion.ts`, `vehiclePresentation.ts`, `tripTime.ts`.
     * Chuyển `pages/driver-portal.css` $\rightarrow$ `src/features/fleet/styles/`.
  4. **Feature `tracking`:**
     * Chuyển `services/operations.ts`, `types/operations.ts`.
     * Chuyển `composables/useLiveOperations.ts`, `useVehicleMarkers.ts`, `usePlannedVehicleAnchors.ts`.
     * Chuyển `components/TrackingPanel.vue`, `components/operations/ModeBar.vue`, `components/operations/AlertStream.vue`, `components/operations/TripTrafficSummary.vue`.
  5. **Feature `simulation`:**
     * Chuyển `components/SimulatorControls.vue`.
     * Chuyển `components/operations/SimulatorPanel.vue`, `SimulationFleetLayer.vue`, `SimulationFleetList.vue`, `SimulationRoutesLayer.vue`, `simulator.css`.
     * Chuyển `composables/useSimulator.ts`, `useSimulationFleet.ts`.
     * Chuyển `utils/simulationFleet.ts`.
     * Xóa bỏ hoàn toàn thư mục `src/components/operations/`.
  6. **Feature `traffic`:**
     * Chuyển `services/hereTraffic.ts`, `types/traffic.ts`, `composables/useTraffic.ts`.
     * Chuyển `components/traffic/*` và `utils/tripTraffic.ts`.
  7. **Feature `map`:**
     * Chuyển `components/MapComponent.vue`, `components/MapControls.vue`.
     * Chuyển `composables/useMapCamera.ts`, `composables/useMapLayers.ts`.
     * Chuyển `types/map.ts`.
     * Chuyển `services/polyline.ts` $\rightarrow$ `src/features/map/utils/polyline.ts`.
* **Checkpoint 4:** Chạy `npm run typecheck && npm run test:unit && npm run test:motion`.

---

### Giai đoạn 5: Dọn dẹp thư mục cũ & Cập nhật các bài Test
* **Mục tiêu:** Xóa triệt để các thư mục phẳng rỗng và chuẩn hóa toàn bộ đường dẫn import trong `tests/unit/`.
* **Các bước thực hiện:**
  1. Xóa các thư mục cũ nếu đã trống: `src/services/`, `src/types/`, `src/composables/`, `src/components/`, `src/utils/`.
  2. Cập nhật import paths trong 14 files của [tests/unit/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit) và [tests/vehicleMotion.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/vehicleMotion.test.ts).
  3. Cập nhật import trong [src/main.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/main.ts).
* **Checkpoint 5:** Chạy đầy đủ các lệnh kiểm thử chất lượng code.

---

### Giai đoạn 6: Nghiệm thu toàn diện & Đồng bộ tài liệu
* **Mục tiêu:** Đảm bảo hệ thống xanh 100% và cập nhật tài liệu hướng dẫn.
* **Các bước thực hiện:**
  1. Chạy toàn bộ test suites:
     ```bash
     cd vehicletracking-frontend
     npm run lint
     npm run typecheck
     npm run test:unit
     npm run test:motion
     npm run build
     ```
  2. Cập nhật tài liệu [docs/design.md](file:///home/khainq/Code/vehicletracking/docs/design.md) và [AGENTS.md](file:///home/khainq/Code/vehicletracking/AGENTS.md) phản ánh cấu trúc thư mục mới của frontend.
  3. Tạo `evidence.md` và `walkthrough.md` theo quy trình.

---

## 3. Kế hoạch dự phòng và Rollback (Rollback Strategy)

* Do việc tái cấu trúc chỉ tác động lên cấu trúc tệp tin và import path trong `vehicletracking-frontend`, mọi thay đổi đều được kiểm soát bởi Git:
  * Nếu phát sinh lỗi nghiêm trọng ở bất kỳ checkpoint nào, có thể sử dụng `git checkout` hoặc `git restore` trên các tệp tin của phân hệ đó để khôi phục trạng thái ổn định gần nhất mà không làm ảnh hưởng đến backend hay cơ sở dữ liệu.
