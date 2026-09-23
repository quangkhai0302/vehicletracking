# 033 — Khảo sát Hiện trạng Mã nguồn Frontend

## 1. Tổng quan cấu trúc thư mục hiện tại

Mã nguồn tại [vehicletracking-frontend/src/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src) bao gồm 8 thư mục con cấp 1 và 6 file ở root:

```text
src/
├── app/                  # Application Shell, router, navigation
├── auth/                 # Auth state, router guards, useAuth composable
├── components/           # 7 component ở root + 5 thư mục domain con
├── composables/          # 15 composables phẳng
├── pages/                # 12 page SFCs + 7 file CSS riêng của page
├── services/             # 17 services API phẳng
├── types/                # 16 file types phẳng
├── utils/                # 8 utils (trộn lẫn generic và domain logic)
├── App.vue               # Root component
├── main.ts               # Entry point ứng dụng
├── index.css             # 50.7 KB (Design tokens & base styles)
├── workspace.css         # 56.7 KB (Workspace styles)
├── ui-refresh.css        # 53.7 KB (UI Refresh overrides)
└── vite-env.d.ts         # Vite client type definitions
```

---

## 2. Bảng phân loại chi tiết các thành phần hiện có

### 2.1 API Services ([src/services/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services)) — 17 files

| File | Domain nghiệp vụ | Đề xuất vị trí mới |
|---|---|---|
| [http.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/http.ts) | Core HTTP client (Generic) | `src/shared/api/http.ts` |
| [auth.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/auth.ts) | Xác thực (Auth) | `src/features/auth/api/auth.ts` |
| [users.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/users.ts) | Quản lý người dùng (Auth/User) | `src/features/auth/api/users.ts` |
| [stations.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/stations.ts) | Quản lý trạm dừng (Station) | `src/features/stations/api/stations.ts` |
| [routes.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/routes.ts) | Quản lý tuyến đường (Route) | `src/features/routes/api/routes.ts` |
| [polyline.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/polyline.ts) | Giải mã flexible polyline (Map util) | `src/features/map/utils/polyline.ts` |
| [fleet.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/fleet.ts) | Xe, tài xế, chuyến đi (Fleet) | `src/features/fleet/api/fleet.ts` |
| [telemetry.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/telemetry.ts) | Telemetry xe (Fleet/Telemetry) | `src/features/fleet/api/telemetry.ts` |
| [checkins.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/checkins.ts) | Check-in trạm dừng của chuyến (Fleet/Checkin) | `src/features/fleet/api/checkins.ts` |
| [eta.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/eta.ts) | Tính toán ETA chuyến đi (Fleet/ETA) | `src/features/fleet/api/eta.ts` |
| [driverPortal.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/driverPortal.ts) | Cổng tài xế (Fleet/Driver) | `src/features/fleet/api/driverPortal.ts` |
| [operations.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/operations.ts) | Giám sát vận hành realtime (Tracking) | `src/features/tracking/api/operations.ts` |
| [hereTraffic.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/hereTraffic.ts) | Dữ liệu giao thông HERE (Traffic) | `src/features/traffic/api/hereTraffic.ts` |
| [schedules.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/schedules.ts) | Lịch trình hoạt động (Schedule) | `src/features/schedules/api/schedules.ts` |
| [dashboard.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/dashboard.ts) | Dữ liệu tổng quan (Reports/Dashboard) | `src/features/reports/api/dashboard.ts` |
| [reports.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/reports.ts) | Báo cáo vận hành (Reports) | `src/features/reports/api/reports.ts` |
| [notifications.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/notifications.ts) | Thông báo & Cảnh báo (Reports/Alerts) | `src/features/reports/api/notifications.ts` |

### 2.2 Kiểu dữ liệu Types ([src/types/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types)) — 16 files

| File | Domain nghiệp vụ | Đề xuất vị trí mới |
|---|---|---|
| [workspace.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/workspace.ts) | Chế độ làm việc (Workspace Mode) | `src/shared/types/workspace.ts` |
| [auth.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/auth.ts) | Kiểu dữ liệu xác thực & tài khoản | `src/features/auth/types/auth.ts` |
| [station.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/station.ts) | Trạm dừng | `src/features/stations/types/station.ts` |
| [route.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/route.ts) | Tuyến đường | `src/features/routes/types/route.ts` |
| [map.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/map.ts) | Bản đồ, tọa độ, theme bản đồ | `src/features/map/types/map.ts` |
| [fleet.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/fleet.ts) | Đội xe, tài xế, chuyến đi | `src/features/fleet/types/fleet.ts` |
| [vehicle.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/vehicle.ts) | Loại xe, thông tin xe | `src/features/fleet/types/vehicle.ts` |
| [telemetry.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/telemetry.ts) | Vết GPS, cảm biến | `src/features/fleet/types/telemetry.ts` |
| [checkin.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/checkin.ts) | Trạng thái check-in | `src/features/fleet/types/checkin.ts` |
| [eta.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/eta.ts) | Dự kiến đến | `src/features/fleet/types/eta.ts` |
| [operations.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/operations.ts) | Sự kiện vận hành realtime | `src/features/tracking/types/operations.ts` |
| [traffic.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/traffic.ts) | Luồng và sự cố giao thông | `src/features/traffic/types/traffic.ts` |
| [schedule.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/schedule.ts) | Lịch trình tự động | `src/features/schedules/types/schedule.ts` |
| [dashboard.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/dashboard.ts) | Chỉ số dashboard | `src/features/reports/types/dashboard.ts` |
| [reports.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/reports.ts) | Báo cáo hiệu suất | `src/features/reports/types/reports.ts` |
| [notifications.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/notifications.ts) | Danh sách cảnh báo/thông báo | `src/features/reports/types/notifications.ts` |

### 2.3 Composables ([src/composables/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables)) — 15 files

| File | Chức năng | Đề xuất vị trí mới |
|---|---|---|
| [useCompactLayout.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useCompactLayout.ts) | Trạng thái bố cục thu gọn (UI generic) | `src/shared/composables/useCompactLayout.ts` |
| [useMapCamera.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useMapCamera.ts) | Điều khiển camera bản đồ (Pan, Zoom, FitBounds) | `src/features/map/composables/useMapCamera.ts` |
| [useMapLayers.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useMapLayers.ts) | Quản lý các Leaflet LayerGroup | `src/features/map/composables/useMapLayers.ts` |
| [useStationWorkspace.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useStationWorkspace.ts) | Quản lý không gian làm việc trạm | `src/features/stations/composables/useStationWorkspace.ts` |
| [useRouteTraffic.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useRouteTraffic.ts) | Giao thông trên tuyến | `src/features/routes/composables/useRouteTraffic.ts` |
| [useSelectedVehicleRoute.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useSelectedVehicleRoute.ts) | Tuyến của xe được chọn | `src/features/routes/composables/useSelectedVehicleRoute.ts` |
| [useFleetWorkspace.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useFleetWorkspace.ts) | Quản lý tab xe/tài xế/chuyến | `src/features/fleet/composables/useFleetWorkspace.ts` |
| [useTripEta.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useTripEta.ts) | Polling/Snapshot ETA chuyến đi | `src/features/fleet/composables/useTripEta.ts` |
| [useTripCheckIns.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useTripCheckIns.ts) | Danh sách check-in chuyến đi | `src/features/fleet/composables/useTripCheckIns.ts` |
| [useLiveOperations.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useLiveOperations.ts) | Kết nối SSE telemetry trực tiếp | `src/features/tracking/composables/useLiveOperations.ts` |
| [useVehicleMarkers.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useVehicleMarkers.ts) | Quản lý Marker xe trên bản đồ | `src/features/tracking/composables/useVehicleMarkers.ts` |
| [usePlannedVehicleAnchors.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/usePlannedVehicleAnchors.ts) | Neo vị trí xe theo kế hoạch | `src/features/tracking/composables/usePlannedVehicleAnchors.ts` |
| [useSimulator.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useSimulator.ts) | Điều khiển giả lập mô phỏng | `src/features/simulation/composables/useSimulator.ts` |
| [useSimulationFleet.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useSimulationFleet.ts) | Danh sách xe giả lập đa luồng | `src/features/simulation/composables/useSimulationFleet.ts` |
| [useTraffic.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useTraffic.ts) | Quản lý lớp giao thông và sự cố | `src/features/traffic/composables/useTraffic.ts` |

### 2.4 Components ([src/components/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components))

#### Root Components:
- [MapComponent.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.vue) (1.084 dòng) $\rightarrow$ `src/features/map/components/MapComponent.vue`
- [MapControls.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapControls.vue) $\rightarrow$ `src/features/map/components/MapControls.vue`
- [SimulatorControls.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/SimulatorControls.vue) $\rightarrow$ `src/features/simulation/components/SimulatorControls.vue`
- [StationDrawer.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.vue) $\rightarrow$ `src/features/stations/components/StationDrawer.vue`
- [StationPanel.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.vue) $\rightarrow$ `src/features/stations/components/StationPanel.vue`
- [TrackingPanel.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/TrackingPanel.vue) $\rightarrow$ `src/features/tracking/components/TrackingPanel.vue`
- [VehicleDrawer.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/VehicleDrawer.vue) $\rightarrow$ `src/features/fleet/components/VehicleDrawer.vue`

#### Thư mục `components/business/`:
- [AuthLayout.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/AuthLayout.vue) $\rightarrow$ `src/app/layouts/AuthLayout.vue`
- [PageHeading.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/PageHeading.vue) $\rightarrow$ `src/shared/components/PageHeading.vue`
- [SidePanel.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/SidePanel.vue) $\rightarrow$ `src/shared/components/SidePanel.vue`
- [FleetManagementTable.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/FleetManagementTable.vue) $\rightarrow$ `src/features/fleet/components/FleetManagementTable.vue`
- [ScheduleConfirm.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/ScheduleConfirm.vue) $\rightarrow$ `src/features/schedules/components/ScheduleConfirm.vue`

#### Thư mục `components/operations/`:
- [ConfirmStationDelete.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/ConfirmStationDelete.vue) (liên quan trạm dừng) $\rightarrow$ `src/features/stations/components/ConfirmStationDelete.vue`
- [ModeBar.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/ModeBar.vue) $\rightarrow$ `src/features/tracking/components/ModeBar.vue`
- [AlertStream.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/AlertStream.vue) $\rightarrow$ `src/features/tracking/components/AlertStream.vue`
- [TripTrafficSummary.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/TripTrafficSummary.vue) $\rightarrow$ `src/features/tracking/components/TripTrafficSummary.vue`
- [SimulatorPanel.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/SimulatorPanel.vue), [SimulationFleetLayer.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/SimulationFleetLayer.vue), [SimulationFleetList.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/SimulationFleetList.vue), [SimulationRoutesLayer.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/SimulationRoutesLayer.vue), [simulator.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/simulator.css) $\rightarrow$ `src/features/simulation/`

#### Thư mục `components/fleet/`, `components/route/`, `components/traffic/`:
- Toàn bộ chuyển về `src/features/fleet/`, `src/features/routes/`, `src/features/traffic/`.

### 2.5 Utilities ([src/utils/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils)) — 8 files

| File | Phân loại | Đề xuất vị trí mới |
|---|---|---|
| [format.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/format.ts) | Generic formatting | `src/shared/utils/format.ts` |
| [routeInspection.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/routeInspection.ts) | Nghiệp vụ soi xét tuyến | `src/features/routes/utils/routeInspection.ts` |
| [inspectionFormat.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/inspectionFormat.ts) | Định dạng thông số soi xét tuyến | `src/features/routes/utils/inspectionFormat.ts` |
| [simulationFleet.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/simulationFleet.ts) | Tạo tọa độ và xe mô phỏng | `src/features/simulation/utils/simulationFleet.ts` |
| [vehicleMotion.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/vehicleMotion.ts) | Toán học nội suy chuyển động xe | `src/features/fleet/utils/vehicleMotion.ts` |
| [vehiclePresentation.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/vehiclePresentation.ts) | Trạng thái hiển thị marker xe | `src/features/fleet/utils/vehiclePresentation.ts` |
| [tripTime.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/tripTime.ts) | Tính toán thời gian chuyến | `src/features/fleet/utils/tripTime.ts` |
| [tripTraffic.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/tripTraffic.ts) | Giao thông và trễ chuyến đi | `src/features/traffic/utils/tripTraffic.ts` |

---

## 3. Khảo sát các bài test hiện có ([tests/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests))

- **Vitest Unit Tests ([tests/unit/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit)):** 14 test files, 85 test cases.
  - Các file test sử dụng các import relative như:
    - `import { http } from '../../src/services/http'`
    - `import { fetchStations } from '../../src/services/stations'`
    - `import FleetWorkspace from '../../src/components/fleet/FleetWorkspace.vue'`
  - Khi tái cấu trúc, các đường dẫn import trong test sẽ được chuyển đổi sang path alias `@/` hoặc relative mới, giữ nguyên 100% logic test.
- **Motion Test ([tests/vehicleMotion.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/vehicleMotion.test.ts)):** 5 test cases chạy qua Node.js native test runner:
  - Cần cập nhật import relative từ `../src/utils/vehicleMotion` sang `../src/features/fleet/utils/vehicleMotion`.
