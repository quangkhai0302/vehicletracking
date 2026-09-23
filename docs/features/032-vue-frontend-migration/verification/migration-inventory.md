# Inventory migration — cutover 2026-09-23

Đối chiếu với manifest React bất biến trong `artifacts/react-baseline-manifest.json`: 51 TSX và 14 hooks có source Vue đối ứng. Bảng này ghi **source đã có**, không thay cho bằng chứng functional/visual của từng luồng. Sau xác nhận giao diện và duyệt cutover của người dùng, production `index.html` dùng Vue; 65file trong bảng đã gỡ khỏi source. File thứ66 là metadata React `src/app/routeConfig.ts` được thay bởi `src/app/navigation.ts`. Bản gốc vẫn còn trong archive baseline, hash đã kiểm tra trước xóa.

Các đường dẫn dưới đây tương đối với `vehicletracking-frontend/`.

| Baseline đã gỡ | Source Vue | Trạng thái |
|---|---|---|
| `src/App.tsx` | `src/App.vue` | Đã thay thế; React lưu trong archive |
| `src/app/ApplicationShell.tsx` | `src/app/ApplicationShell.vue` | Đã thay thế; React lưu trong archive |
| `src/auth/AuthContext.tsx` | `src/auth/authState.ts + src/auth/useAuth.ts` | Đã thay thế; React lưu trong archive |
| `src/auth/RouteGuards.tsx` | `src/auth/routerGuards.ts + src/app/router.ts` | Đã thay thế; React lưu trong archive |
| `src/components/MapComponent.tsx` | `src/components/MapComponent.vue` | Đã thay thế; React lưu trong archive |
| `src/components/MapControls.tsx` | `src/components/MapControls.vue` | Đã thay thế; React lưu trong archive |
| `src/components/SimulatorControls.tsx` | `src/components/SimulatorControls.vue` | Đã thay thế; React lưu trong archive |
| `src/components/StationDrawer.tsx` | `src/components/StationDrawer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/StationPanel.tsx` | `src/components/StationPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/TrackingPanel.tsx` | `src/components/TrackingPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/VehicleDrawer.tsx` | `src/components/VehicleDrawer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/business/AuthLayout.tsx` | `src/components/business/AuthLayout.vue` | Đã thay thế; React lưu trong archive |
| `src/components/business/FleetManagementTable.tsx` | `src/components/business/FleetManagementTable.vue` | Đã thay thế; React lưu trong archive |
| `src/components/business/PageHeading.tsx` | `src/components/business/PageHeading.vue` | Đã thay thế; React lưu trong archive |
| `src/components/business/SidePanel.tsx` | `src/components/business/SidePanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/DriverEditor.tsx` | `src/components/fleet/DriverEditor.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/FleetConfirmDialog.tsx` | `src/components/fleet/FleetConfirmDialog.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/FleetWorkspace.tsx` | `src/components/fleet/FleetWorkspace.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/RouteRevisionPanel.tsx` | `src/components/fleet/RouteRevisionPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/TelemetryHistoryPanel.tsx` | `src/components/fleet/TelemetryHistoryPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/TripDetailPanel.tsx` | `src/components/fleet/TripDetailPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/TripEditor.tsx` | `src/components/fleet/TripEditor.vue` | Đã thay thế; React lưu trong archive |
| `src/components/fleet/VehicleEditor.tsx` | `src/components/fleet/VehicleEditor.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/AlertStream.tsx` | `src/components/operations/AlertStream.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/ConfirmStationDelete.tsx` | `src/components/operations/ConfirmStationDelete.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/ModeBar.tsx` | `src/components/operations/ModeBar.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/SimulationFleetLayer.tsx` | `src/components/operations/SimulationFleetLayer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/SimulationFleetList.tsx` | `src/components/operations/SimulationFleetList.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/SimulationRoutesLayer.tsx` | `src/components/operations/SimulationRoutesLayer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/SimulatorPanel.tsx` | `src/components/operations/SimulatorPanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/operations/TripTrafficSummary.tsx` | `src/components/operations/TripTrafficSummary.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/RouteDrawer.tsx` | `src/components/route/RouteDrawer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/RouteInspectionLayer.tsx` | `src/components/route/RouteInspectionLayer.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/RoutePanel.tsx` | `src/components/route/RoutePanel.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/RouteShapeEditor.tsx` | `src/components/route/RouteShapeEditor.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/RouteWorkspace.tsx` | `src/components/route/RouteWorkspace.vue` | Đã thay thế; React lưu trong archive |
| `src/components/route/SortableStopList.tsx` | `src/components/route/SortableStopList.vue` | Đã thay thế; React lưu trong archive |
| `src/components/traffic/TrafficInspectionCard.tsx` | `src/components/traffic/{MapInspectionCard,TrafficSourceLine,TrafficFlowDetails,NearbyTrafficIncidents}.vue` | Đã thay thế; React lưu trong archive |
| `src/components/traffic/TrafficLayer.tsx` | `src/components/traffic/TrafficLayer.vue` | Đã thay thế; React lưu trong archive |
| `src/hooks/useCompactLayout.ts` | `src/composables/useCompactLayout.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useFleetWorkspace.ts` | `src/composables/useFleetWorkspace.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useLiveOperations.ts` | `src/composables/useLiveOperations.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useMapCamera.ts` | `src/composables/useMapCamera.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/usePlannedVehicleAnchors.ts` | `src/composables/usePlannedVehicleAnchors.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useRouteTraffic.ts` | `src/composables/useRouteTraffic.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useSelectedVehicleRoute.ts` | `src/composables/useSelectedVehicleRoute.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useSimulationFleet.ts` | `src/composables/useSimulationFleet.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useSimulator.ts` | `src/composables/useSimulator.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useStationWorkspace.ts` | `src/composables/useStationWorkspace.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useTraffic.ts` | `src/composables/useTraffic.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useTripCheckIns.ts` | `src/composables/useTripCheckIns.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useTripEta.ts` | `src/composables/useTripEta.ts` | Đã thay thế; React lưu trong archive |
| `src/hooks/useVehicleMarkers.ts` | `src/composables/useVehicleMarkers.ts` | Đã thay thế; React lưu trong archive |
| `src/main.tsx` | `src/main.ts` | Đã thay thế; React lưu trong archive |
| `src/pages/AdminRegistrationPage.tsx` | `src/pages/AdminRegistrationPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/AlertsManagementPage.tsx` | `src/pages/AlertsManagementPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/DashboardPage.tsx` | `src/pages/DashboardPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/DriverPortalPage.tsx` | `src/pages/DriverPortalPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/FleetManagementPage.tsx` | `src/pages/FleetManagementPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/LoginPage.tsx` | `src/pages/LoginPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/NotFoundPage.tsx` | `src/pages/NotFoundPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/ReportsPage.tsx` | `src/pages/ReportsPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/RoadmapPage.tsx` | `src/pages/RoadmapPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/ScheduleManagementPage.tsx` | `src/pages/ScheduleManagementPage.vue` | Đã thay thế; React lưu trong archive |
| `src/pages/UserManagementPage.tsx` | `src/pages/UserManagementPage.vue` | Đã thay thế; React lưu trong archive |

## Thay đổi cấu trúc có chủ đích

- `src/app/routeConfig.ts` chứa icon React đã gỡ, đối ứng Vue là `src/app/navigation.ts`. `src/app/router.ts` định nghĩa 15 URL, root redirect và catch-all; `tests/unit/router.test.ts` kiểm tra role boundary, query và key/remount.
- `src/components/MapComponent.vue` giữ UI/state owner, còn việc sở hữu Leaflet map và các layer nền/trạm/tuyến nháp nằm trong `src/composables/useMapLayers.ts`. `tests/unit/map-integration.test.ts` kiểm tra full map mount/unmount20lần trong jsdom và SSE cleanup.
- `src/components/route/RouteDrawer.vue` dùng `RouteCreateContent.vue` và `RouteViewContent.vue`, giữ key tạo/sửa/xem riêng như baseline. `tests/unit/routes.test.ts` kiểm tra request stale, refresh khi đang sửa, form và busy/dispose.
- `TrackingPanel.vue` và `VehicleDrawer.vue` được giữ dù chưa có route dùng trực tiếp; không nối thêm UI ngoài phạm vi. `tests/unit/tracking-panels.test.ts` kiểm tra filter/keyboard/drawer callbacks.
- CSS/assets/services/types/motion utilities tiếp tục dùng lại. Trước xóa: checksum124file `src/` baseline không đổi. Sau xóa66file React:58file dùng chung còn nguyên, gồm16CSS.
- `tests/visual/vue-preview.ts` nạp `src/main.ts`, không còn bản router sao chép chỉ dành cho test. `src/main.ts` đồng thời là entry production; `src/main.tsx` đã gỡ.

Đã gỡ React và cập nhật CI theo duyệt cutover. Còn phải kiểm tra đầy đủ matrix browser/API/visual/container; không coi bảng inventory là trạng thái Verified hoặc kết quả pass cho các test chưa chạy.
