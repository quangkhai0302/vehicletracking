# 033 — Đặc tả Kỹ thuật Tái cấu trúc Thư mục Frontend

## 1. Cấu trúc cây thư mục mục tiêu

Toàn bộ thư mục `vehicletracking-frontend/src/` được chuẩn hóa theo cấu trúc sau:

```text
src/
├── app/                        # Khởi tạo ứng dụng & cấu hình toàn cục
│   ├── layouts/                # Các layout nền tảng
│   │   ├── ApplicationShell.vue
│   │   ├── AuthLayout.vue
│   │   └── application-shell.css
│   ├── router/                 # Cấu hình Vue Router & Navigation Guards
│   │   ├── index.ts            # Khởi tạo router instance
│   │   └── guards.ts           # Route guards kiểm tra auth & role
│   └── navigation.ts           # Định nghĩa danh mục menu điều hướng
│
├── shared/                     # Lớp dùng chung toàn hệ thống (Domain-agnostic)
│   ├── api/                    # HTTP client cấu hình chung
│   │   └── http.ts
│   ├── components/             # UI Components tái sử dụng (Atoms / Molecules)
│   │   ├── PageHeading.vue
│   │   └── SidePanel.vue
│   ├── composables/            # Composable tiện ích không gắn với nghiệp vụ
│   │   └── useCompactLayout.ts
│   ├── styles/                 # Design tokens, variables, typography, reset CSS
│   │   ├── index.css           # Core tokens & global styles
│   │   ├── workspace.css       # Workspace common layout styles
│   │   └── ui-refresh.css      # UI overrides
│   ├── types/                  # Kiểu dữ liệu chung
│   │   └── workspace.ts
│   └── utils/                  # Hàm tiện ích thuần túy
│       └── format.ts
│
├── features/                   # CÁC MODULE NGHIỆP VỤ (Feature-Driven Co-location)
│   │
│   ├── auth/                   # Xác thực, tài khoản & phiên làm việc
│   │   ├── api/                # auth.ts, users.ts
│   │   ├── composables/        # useAuth.ts, authState.ts
│   │   ├── types/              # auth.ts
│   │   └── styles/             # auth-pages.css
│   │
│   ├── map/                    # Hạ tầng bản đồ Leaflet & Controls dùng chung
│   │   ├── components/         # MapComponent.vue, MapControls.vue
│   │   ├── composables/        # useMapCamera.ts, useMapLayers.ts
│   │   ├── types/              # map.ts
│   │   └── utils/              # polyline.ts
│   │
│   ├── stations/               # Quản lý trạm dừng
│   │   ├── api/                # stations.ts
│   │   ├── components/         # StationPanel.vue, StationDrawer.vue, ConfirmStationDelete.vue
│   │   ├── composables/        # useStationWorkspace.ts
│   │   └── types/              # station.ts
│   │
│   ├── routes/                 # Tuyến đường & hình học tuyến
│   │   ├── api/                # routes.ts
│   │   ├── components/         # RouteWorkspace.vue, RouteDrawer.vue, RoutePanel.vue,
│   │   │                       # RouteShapeEditor.vue, SortableStopList.vue, RouteInspectionLayer.vue,
│   │   │                       # RouteCreateContent.vue, RouteViewContent.vue
│   │   ├── composables/        # useRouteTraffic.ts, useSelectedVehicleRoute.ts
│   │   ├── types/              # route.ts
│   │   ├── styles/             # route.css, route-inspection.css
│   │   └── utils/              # routeInspection.ts, inspectionFormat.ts
│   │
│   ├── fleet/                  # Đội xe, tài xế, chuyến đi
│   │   ├── api/                # fleet.ts, telemetry.ts, checkins.ts, eta.ts, driverPortal.ts
│   │   ├── components/         # FleetWorkspace.vue, VehicleEditor.vue, DriverEditor.vue,
│   │   │                       # TripEditor.vue, TripDetailPanel.vue, TelemetryHistoryPanel.vue,
│   │   │                       # RouteRevisionPanel.vue, FleetConfirmDialog.vue, FleetManagementTable.vue,
│   │   │                       # VehicleDrawer.vue
│   │   ├── composables/        # useFleetWorkspace.ts, useTripEta.ts, useTripCheckIns.ts
│   │   ├── types/              # fleet.ts, vehicle.ts, checkin.ts, eta.ts, telemetry.ts
│   │   ├── styles/             # fleet.css, driver-portal.css
│   │   └── utils/              # vehicleMotion.ts, vehiclePresentation.ts, tripTime.ts
│   │
│   ├── tracking/               # Giám sát hành trình realtime (Live Operations)
│   │   ├── api/                # operations.ts
│   │   ├── components/         # TrackingPanel.vue, AlertStream.vue, ModeBar.vue,
│   │   │                       # TripTrafficSummary.vue
│   │   ├── composables/        # useLiveOperations.ts, useVehicleMarkers.ts, usePlannedVehicleAnchors.ts
│   │   └── types/              # operations.ts
│   │
│   ├── simulation/             # Giả lập hành trình & xe mô phỏng
│   │   ├── components/         # SimulatorPanel.vue, SimulatorControls.vue,
│   │   │                       # SimulationFleetLayer.vue, SimulationFleetList.vue,
│   │   │                       # SimulationRoutesLayer.vue
│   │   ├── composables/        # useSimulator.ts, useSimulationFleet.ts
│   │   ├── styles/             # simulator.css
│   │   └── utils/              # simulationFleet.ts
│   │
│   ├── traffic/                # Lớp giao thông & sự cố HERE
│   │   ├── api/                # hereTraffic.ts
│   │   ├── components/         # TrafficLayer.vue, TrafficFlowDetails.vue, NearbyTrafficIncidents.vue,
│   │   │                       # MapInspectionCard.vue, TrafficSourceLine.vue
│   │   ├── composables/        # useTraffic.ts
│   │   ├── types/              # traffic.ts
│   │   ├── styles/             # traffic.css
│   │   └── utils/              # tripTraffic.ts
│   │
│   ├── schedules/              # Lịch trình tự động
│   │   ├── api/                # schedules.ts
│   │   ├── components/         # ScheduleConfirm.vue
│   │   ├── types/              # schedule.ts
│   │   └── styles/             # schedule-management.css
│   │
│   └── reports/                # Báo cáo, Dashboard & Cảnh báo
│       ├── api/                # dashboard.ts, reports.ts, notifications.ts
│       ├── types/              # dashboard.ts, reports.ts, notifications.ts
│       └── styles/             # reports.css, alerts-management.css, business-pages.css
│
├── pages/                      # Thin Page components kết nối Router
│   ├── DashboardPage.vue
│   ├── MapPage.vue
│   ├── FleetManagementPage.vue
│   ├── ScheduleManagementPage.vue
│   ├── AlertsManagementPage.vue
│   ├── ReportsPage.vue
│   ├── UserManagementPage.vue
│   ├── DriverPortalPage.vue
│   ├── LoginPage.vue
│   ├── AdminRegistrationPage.vue
│   ├── RoadmapPage.vue
│   └── NotFoundPage.vue
│
├── App.vue                     # Root Application Component
├── main.ts                     # Application Entry Point
└── vite-env.d.ts               # Environment Types
```

---

## 2. Quy tắc phân tầng & ranh giới (Layering Rules)

1. **Quy tắc phụ thuộc một chiều (Unidirectional Dependency):**
   * `pages` $\rightarrow$ phụ thuộc vào `features`, `shared`, `app`.
   * `features` $\rightarrow$ phụ thuộc vào `shared`. Feature có thể import types từ feature khác (ví dụ: `tracking` dùng `TripDetail` từ `fleet`), nhưng không được import component nội bộ riêng tư của feature khác nếu không qua file định nghĩa công khai.
   * `shared` $\rightarrow$ **tuyệt đối không** import từ `features` hay `pages`.
   * `app` $\rightarrow$ quản lý router, layouts và cấu hình khởi tạo.

2. **Quy tắc Import Path:**
   * Import nội bộ trong cùng một feature: Ưu tiên dùng đường dẫn tương đối (relative: `./`, `../`) để đảm bảo tính đóng gói cục bộ của module.
   * Import giữa các feature khác nhau hoặc từ feature sang shared/app: Dùng path alias `@/` (ví dụ: `import { http } from '@/shared/api/http'`).
   * Không dùng các chuỗi relative dài hơn 2 cấp (ví dụ: cấm `../../../services/...`).

---

## 3. Bảng ánh xạ di dời file (Migration Mapping Matrix)

### 3.1 Nhóm Shared & App
| File cũ | File mới | Ghi chú |
|---|---|---|
| `src/services/http.ts` | `src/shared/api/http.ts` | HTTP client lõi |
| `src/components/business/PageHeading.vue` | `src/shared/components/PageHeading.vue` | Reusable header |
| `src/components/business/SidePanel.vue` | `src/shared/components/SidePanel.vue` | Reusable sidebar panel |
| `src/composables/useCompactLayout.ts` | `src/shared/composables/useCompactLayout.ts` | UI state composable |
| `src/utils/format.ts` | `src/shared/utils/format.ts` | Formatting tiện ích chung |
| `src/types/workspace.ts` | `src/shared/types/workspace.ts` | Workspace mode type |
| `src/components/business/AuthLayout.vue` | `src/app/layouts/AuthLayout.vue` | Layout xác thực |
| `src/app/ApplicationShell.vue` | `src/app/layouts/ApplicationShell.vue` | Layout chính |
| `src/app/application-shell.css` | `src/app/layouts/application-shell.css` | CSS layout chính |
| `src/app/router.ts` | `src/app/router/index.ts` | Router entry |
| `src/auth/routerGuards.ts` | `src/app/router/guards.ts` | Guards điều hướng |

### 3.2 Nhóm Features
| Feature | File cũ | File mới |
|---|---|---|
| **auth** | `src/auth/authState.ts` | `src/features/auth/composables/authState.ts` |
| | `src/auth/useAuth.ts` | `src/features/auth/composables/useAuth.ts` |
| | `src/services/auth.ts` | `src/features/auth/api/auth.ts` |
| | `src/services/users.ts` | `src/features/auth/api/users.ts` |
| | `src/types/auth.ts` | `src/features/auth/types/auth.ts` |
| | `src/pages/auth-pages.css` | `src/features/auth/styles/auth-pages.css` |
| **map** | `src/components/MapComponent.vue` | `src/features/map/components/MapComponent.vue` |
| | `src/components/MapControls.vue` | `src/features/map/components/MapControls.vue` |
| | `src/composables/useMapCamera.ts` | `src/features/map/composables/useMapCamera.ts` |
| | `src/composables/useMapLayers.ts` | `src/features/map/composables/useMapLayers.ts` |
| | `src/types/map.ts` | `src/features/map/types/map.ts` |
| | `src/services/polyline.ts` | `src/features/map/utils/polyline.ts` |
| **stations** | `src/services/stations.ts` | `src/features/stations/api/stations.ts` |
| | `src/types/station.ts` | `src/features/stations/types/station.ts` |
| | `src/composables/useStationWorkspace.ts` | `src/features/stations/composables/useStationWorkspace.ts` |
| | `src/components/StationPanel.vue` | `src/features/stations/components/StationPanel.vue` |
| | `src/components/StationDrawer.vue` | `src/features/stations/components/StationDrawer.vue` |
| | `src/components/operations/ConfirmStationDelete.vue` | `src/features/stations/components/ConfirmStationDelete.vue` |
| **routes** | `src/services/routes.ts` | `src/features/routes/api/routes.ts` |
| | `src/types/route.ts` | `src/features/routes/types/route.ts` |
| | `src/composables/useRouteTraffic.ts` | `src/features/routes/composables/useRouteTraffic.ts` |
| | `src/composables/useSelectedVehicleRoute.ts` | `src/features/routes/composables/useSelectedVehicleRoute.ts` |
| | `src/components/route/*` | `src/features/routes/components/*` |
| | `src/utils/routeInspection.ts` | `src/features/routes/utils/routeInspection.ts` |
| | `src/utils/inspectionFormat.ts` | `src/features/routes/utils/inspectionFormat.ts` |
| **fleet** | `src/services/fleet.ts` | `src/features/fleet/api/fleet.ts` |
| | `src/services/telemetry.ts` | `src/features/fleet/api/telemetry.ts` |
| | `src/services/checkins.ts` | `src/features/fleet/api/checkins.ts` |
| | `src/services/eta.ts` | `src/features/fleet/api/eta.ts` |
| | `src/services/driverPortal.ts` | `src/features/fleet/api/driverPortal.ts` |
| | `src/types/fleet.ts` | `src/features/fleet/types/fleet.ts` |
| | `src/types/vehicle.ts` | `src/features/fleet/types/vehicle.ts` |
| | `src/types/telemetry.ts` | `src/features/fleet/types/telemetry.ts` |
| | `src/types/checkin.ts` | `src/features/fleet/types/checkin.ts` |
| | `src/types/eta.ts` | `src/features/fleet/types/eta.ts` |
| | `src/composables/useFleetWorkspace.ts` | `src/features/fleet/composables/useFleetWorkspace.ts` |
| | `src/composables/useTripEta.ts` | `src/features/fleet/composables/useTripEta.ts` |
| | `src/composables/useTripCheckIns.ts` | `src/features/fleet/composables/useTripCheckIns.ts` |
| | `src/components/fleet/*` | `src/features/fleet/components/*` |
| | `src/components/VehicleDrawer.vue` | `src/features/fleet/components/VehicleDrawer.vue` |
| | `src/components/business/FleetManagementTable.vue` | `src/features/fleet/components/FleetManagementTable.vue` |
| | `src/utils/vehicleMotion.ts` | `src/features/fleet/utils/vehicleMotion.ts` |
| | `src/utils/vehiclePresentation.ts` | `src/features/fleet/utils/vehiclePresentation.ts` |
| | `src/utils/tripTime.ts` | `src/features/fleet/utils/tripTime.ts` |
| | `src/pages/driver-portal.css` | `src/features/fleet/styles/driver-portal.css` |
| **tracking** | `src/services/operations.ts` | `src/features/tracking/api/operations.ts` |
| | `src/types/operations.ts` | `src/features/tracking/types/operations.ts` |
| | `src/composables/useLiveOperations.ts` | `src/features/tracking/composables/useLiveOperations.ts` |
| | `src/composables/useVehicleMarkers.ts` | `src/features/tracking/composables/useVehicleMarkers.ts` |
| | `src/composables/usePlannedVehicleAnchors.ts` | `src/features/tracking/composables/usePlannedVehicleAnchors.ts` |
| | `src/components/TrackingPanel.vue` | `src/features/tracking/components/TrackingPanel.vue` |
| | `src/components/operations/ModeBar.vue` | `src/features/tracking/components/ModeBar.vue` |
| | `src/components/operations/AlertStream.vue` | `src/features/tracking/components/AlertStream.vue` |
| | `src/components/operations/TripTrafficSummary.vue` | `src/features/tracking/components/TripTrafficSummary.vue` |
| **simulation** | `src/components/SimulatorControls.vue` | `src/features/simulation/components/SimulatorControls.vue` |
| | `src/components/operations/SimulatorPanel.vue` | `src/features/simulation/components/SimulatorPanel.vue` |
| | `src/components/operations/SimulationFleetLayer.vue` | `src/features/simulation/components/SimulationFleetLayer.vue` |
| | `src/components/operations/SimulationFleetList.vue` | `src/features/simulation/components/SimulationFleetList.vue` |
| | `src/components/operations/SimulationRoutesLayer.vue` | `src/features/simulation/components/SimulationRoutesLayer.vue` |
| | `src/components/operations/simulator.css` | `src/features/simulation/styles/simulator.css` |
| | `src/composables/useSimulator.ts` | `src/features/simulation/composables/useSimulator.ts` |
| | `src/composables/useSimulationFleet.ts` | `src/features/simulation/composables/useSimulationFleet.ts` |
| | `src/utils/simulationFleet.ts` | `src/features/simulation/utils/simulationFleet.ts` |
| **traffic** | `src/services/hereTraffic.ts` | `src/features/traffic/api/hereTraffic.ts` |
| | `src/types/traffic.ts` | `src/features/traffic/types/traffic.ts` |
| | `src/composables/useTraffic.ts` | `src/features/traffic/composables/useTraffic.ts` |
| | `src/components/traffic/*` | `src/features/traffic/components/*` |
| | `src/utils/tripTraffic.ts` | `src/features/traffic/utils/tripTraffic.ts` |
| **schedules** | `src/services/schedules.ts` | `src/features/schedules/api/schedules.ts` |
| | `src/types/schedule.ts` | `src/features/schedules/types/schedule.ts` |
| | `src/components/business/ScheduleConfirm.vue` | `src/features/schedules/components/ScheduleConfirm.vue` |
| | `src/pages/schedule-management.css` | `src/features/schedules/styles/schedule-management.css` |
| **reports** | `src/services/dashboard.ts` | `src/features/reports/api/dashboard.ts` |
| | `src/services/reports.ts` | `src/features/reports/api/reports.ts` |
| | `src/services/notifications.ts` | `src/features/reports/api/notifications.ts` |
| | `src/types/dashboard.ts` | `src/features/reports/types/dashboard.ts` |
| | `src/types/reports.ts` | `src/features/reports/types/reports.ts` |
| | `src/types/notifications.ts` | `src/features/reports/types/notifications.ts` |
| | `src/pages/reports.css` | `src/features/reports/styles/reports.css` |
| | `src/pages/alerts-management.css` | `src/features/reports/styles/alerts-management.css` |
| | `src/pages/business-pages.css` | `src/features/reports/styles/business-pages.css` |
