# Survey — Gộp màn hình tuyến và chuyến

## Cây file và luồng hiện tại

- `vehicletracking-frontend/src/app/router/index.ts`: `/routes` mở `RouteManagementPage`; `/trips` mở `FleetManagementPage` thông qua wrapper tab.
- `vehicletracking-frontend/src/app/navigation.ts`: hai mục sidebar riêng; `ApplicationShell.vue` chọn tiêu đề/active theo path.
- `vehicletracking-frontend/src/pages/RouteManagementPage.vue`: tải route/station, danh sách, tạo, xem chi tiết và ngừng sử dụng; chi tiết nằm trong `SidePanel`.
- `vehicletracking-frontend/src/pages/FleetManagementPage.vue` → `features/fleet/components/FleetWorkspace.vue` → `TripEditor.vue`/`TripDetailPanel.vue`: danh sách, form, chi tiết, hành động chuyến.
- `vehicletracking-frontend/src/features/routes/api/routes.ts` và `features/fleet/api/fleet.ts`: API hiện hữu đủ cho liên kết chéo.
- `vehicletracking-backend/.../route/controller/RouteController.java` và `.../trip/controller/TripController.java`: hai contract HTTP độc lập.
- `vehicletracking-backend/.../trip/entity/TripEntity.java`: chuyến tham chiếu tuyến qua `route_id`; `V4__create_vehicles_and_trips.sql` tạo FK và snapshot điểm dừng.
- `vehicletracking-frontend/tests/unit/router.test.ts`: kiểm tra URL/deep link/remount; `tests/unit/page-workflows.test.ts`: luồng trang thật với fixture HTTP.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Route/trip hiện có route riêng | `vehicletracking-frontend/src/app/router/index.ts:createApplicationRouter` | Có thể giữ path cũ, dùng chung wrapper. |
| Sidebar có hai mục riêng | `vehicletracking-frontend/src/app/navigation.ts:navigationGroups` | Đổi thành một mục có hai path tương đương. |
| Trang tuyến giữ form và detail cục bộ | `vehicletracking-frontend/src/pages/RouteManagementPage.vue:loadData,openDetail,saveRoute,confirmDeactivate` | Tái sử dụng nguyên trang bên trong tab. |
| Trang chuyến dùng state riêng | `vehicletracking-frontend/src/features/fleet/composables/useFleetWorkspace.ts:selectTrip,saveTrip,transition` | Tab switch nên remount để tránh state cũ rò sang tab khác. |
| Form chuyến chọn tuyến bằng route ID | `vehicletracking-frontend/src/features/fleet/components/TripEditor.vue:routeId,submit` | Có thể truyền route ID ban đầu mà không đổi API. |
| Chuyến tham chiếu tuyến, không phải cùng thực thể | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/entity/TripEntity.java:route`; `vehicletracking-backend/src/main/resources/db/migration/V4__create_vehicles_and_trips.sql:14` | Không đổi backend/schema. |

## Rủi ro và trạng thái working tree

Trước triển khai worktree đã có thay đổi ở bản đồ, CSS và test, cùng `FleetManagementPage.vue`, `MapPage.vue`. Bảo toàn các thay đổi này; chỉ thêm phần cần thiết cho workspace gộp. Rủi ro chính: query/deep link, active sidebar, drawer và test router khi hai path dùng chung component.
